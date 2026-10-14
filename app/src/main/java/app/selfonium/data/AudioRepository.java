package app.selfonium.data;

import android.content.Context;
import android.net.Uri;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.*;
import app.selfonium.core.Protocol;
import app.selfonium.core.Source;
import app.selfonium.core.Track;
import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded audio cache. Prefetch never owns a lock that playback must wait for. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class AudioRepository {
    private final Context context;private final Library library;private final SourceStore sources;
    private SimpleCache cache;
    private final AtomicLong generation=new AtomicLong();
    private final ThreadPoolExecutor warmers=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile CacheWriter active;
    public AudioRepository(Context context,Library library,SourceStore sources){this.context=context.getApplicationContext();this.library=library;this.sources=sources;}
    private synchronized SimpleCache cache(){
        if(cache==null)cache=new SimpleCache(new File(context.getCacheDir(),"audio-v2"),new LeastRecentlyUsedCacheEvictor(192L*1024*1024),new StandaloneDatabaseProvider(context));
        return cache;
    }
    private String key(DataSpec spec){
        String id=spec.uri.getLastPathSegment();Track track=library.get(id==null?"":id);
        if(track==null)return Protocol.md5(spec.uri.toString());
        Source source=sources.get(track.sourceId);
        return Protocol.md5(track.id+"\n"+track.uri+"\n"+track.durationMs+"\n"+(source==null?"":source.baseUrl+"\n"+source.username));
    }
    private CacheDataSource.Factory factory(DataSource.Factory upstream){return new CacheDataSource.Factory().setCache(cache()).setCacheKeyFactory(this::key).setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);}
    public DataSource.Factory cached(DataSource.Factory upstream){return ()->new DataSource(){
        private DataSource delegate;
        private final java.util.List<androidx.media3.datasource.TransferListener> listeners=new java.util.ArrayList<>();
        @Override public void addTransferListener(androidx.media3.datasource.TransferListener listener){listeners.add(listener);if(delegate!=null)delegate.addTransferListener(listener);}
        @Override public long open(DataSpec spec) throws java.io.IOException {
            String id="selfonium".equals(spec.uri.getScheme())?spec.uri.getLastPathSegment():null;
            Track track=id==null?null:library.get(id);
            boolean music=track!=null&&!java.util.List.of("device","files","radio").contains(track.sourceId)&&sources.get(track.sourceId)!=null&&!track.uri.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")&&library.offline(track.id).isEmpty();
            delegate=music?factory(upstream).createDataSource():upstream.createDataSource();
            for(var listener:listeners)delegate.addTransferListener(listener);return delegate.open(spec);
        }
        @Override public int read(byte[] buffer,int offset,int length)throws java.io.IOException{return delegate.read(buffer,offset,length);}
        @Override public Uri getUri(){return delegate==null?null:delegate.getUri();}
        @Override public java.util.Map<String,java.util.List<String>> getResponseHeaders(){return delegate==null?java.util.Map.of():delegate.getResponseHeaders();}
        @Override public void close()throws java.io.IOException{if(delegate!=null){try{delegate.close();}finally{delegate=null;}}}
    };}
    public void cancelPrefetch(){generation.incrementAndGet();CacheWriter writer=active;if(writer!=null)writer.cancel();warmers.getQueue().clear();}
    public void prefetch(DataSource.Factory upstream,List<Track> upcoming){
        cancelPrefetch();long request=generation.get();
        warmers.execute(()->{
            try{for(Track track:upcoming){
                if(generation.get()!=request)return;
                if(java.util.List.of("device","files","radio").contains(track.sourceId))continue;
                Source source=sources.get(track.sourceId);if(source==null||track.uri.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")||!library.offline(track.id).isEmpty())continue;
                CacheWriter writer=new CacheWriter(factory(upstream).createDataSource(),new DataSpec.Builder().setUri(Uri.parse("selfonium://track/"+Uri.encode(track.id))).setLength(512*1024).build(),null,null);
                active=writer;
                if(generation.get()!=request){writer.cancel();return;}
                try{writer.cache();}catch(Exception ignored){/* Partial cache remains useful; never fail playback. */}
                finally{if(active==writer)active=null;}
            }}catch(Exception ignored){/* Source/cache initialization failure must not kill the audio process. */}
        });
    }
}
