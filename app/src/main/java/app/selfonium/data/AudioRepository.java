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
    private SimpleCache cache;private int users;private boolean clearPending,reconfigure;
    private final Evictor evictor=new Evictor();
    private static final class Evictor implements CacheEvictor {
        private LeastRecentlyUsedCacheEvictor delegate=new LeastRecentlyUsedCacheEvictor(192L*1024*1024);
        void limit(Cache cache,long bytes){java.util.List<CacheSpan> spans=new java.util.ArrayList<>();for(String key:cache.getKeys())spans.addAll(cache.getCachedSpans(key));spans.sort(java.util.Comparator.comparingLong(s->s.lastTouchTimestamp));delegate=new LeastRecentlyUsedCacheEvictor(bytes);for(CacheSpan span:spans)delegate.onSpanAdded(cache,span);}
        public boolean requiresCacheSpanTouches(){return true;}
        public void onCacheInitialized(){delegate.onCacheInitialized();}
        public void onStartFile(Cache cache,String key,long position,long length){delegate.onStartFile(cache,key,position,length);}
        public void onSpanAdded(Cache cache,CacheSpan span){delegate.onSpanAdded(cache,span);}
        public void onSpanRemoved(Cache cache,CacheSpan span){delegate.onSpanRemoved(cache,span);}
        public void onSpanTouched(Cache cache,CacheSpan oldSpan,CacheSpan newSpan){delegate.onSpanTouched(cache,oldSpan,newSpan);}
    }
    public long limitBytes(){return context.getSharedPreferences("storage",0).getLong("audioLimit",192L*1024*1024);}
    public boolean enabled(){return limitBytes()>0;}
    public boolean prefetchEnabled(){return enabled()&&context.getSharedPreferences("storage",0).getBoolean("prefetch",true);}
    public void configure(long bytes,boolean prefetch){if(!java.util.List.of(0L,64L,192L,512L,1024L,2048L).contains(bytes/(1024*1024))||bytes<0||bytes%(1024*1024)!=0)throw new IllegalArgumentException();context.getSharedPreferences("storage",0).edit().putLong("audioLimit",bytes).putBoolean("prefetch",prefetch).apply();cancelPrefetch();synchronized(this){reconfigure=true;maintenance();}}
    public synchronized boolean clear(){cancelPrefetch();clearPending=true;maintenance();return !clearPending;}
    public synchronized boolean clearPending(){return clearPending;}
    private synchronized void begin(){users++;}
    private final ExecutorService upkeep=Executors.newSingleThreadExecutor();
    private synchronized void end(){users--;if(users==0&&(clearPending||reconfigure))upkeep.execute(()->{synchronized(this){maintenance();}});}
    private void maintenance(){
        if(users!=0)return;
        try{if(clearPending){SimpleCache store=cache();for(String key:new java.util.HashSet<>(store.getKeys()))store.removeResource(key);clearPending=false;}
            if(reconfigure){SimpleCache store=cache();synchronized(store){evictor.limit(store,limitBytes());}reconfigure=false;}}
        catch(Exception ignored){/* Retry at the next idle point; never break playback. */}
    }
    private final AtomicLong generation=new AtomicLong();
    private final ThreadPoolExecutor warmers=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile CacheWriter active;
    public AudioRepository(Context context,Library library,SourceStore sources){this.context=context.getApplicationContext();this.library=library;this.sources=sources;}
    private synchronized SimpleCache cache(){
        if(cache==null){cache=new SimpleCache(new File(context.getCacheDir(),"audio-v2"),evictor,new StandaloneDatabaseProvider(context));synchronized(cache){evictor.limit(cache,limitBytes());}}
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
        private DataSource delegate;private boolean counted;
        private final java.util.List<androidx.media3.datasource.TransferListener> listeners=new java.util.ArrayList<>();
        @Override public void addTransferListener(androidx.media3.datasource.TransferListener listener){listeners.add(listener);if(delegate!=null)delegate.addTransferListener(listener);}
        @Override public long open(DataSpec spec) throws java.io.IOException {
            try{String id="selfonium".equals(spec.uri.getScheme())?spec.uri.getLastPathSegment():null;
            Track track=id==null?null:library.get(id);
            boolean music=track!=null&&!java.util.List.of("device","files","radio").contains(track.sourceId)&&sources.get(track.sourceId)!=null&&!track.uri.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")&&library.offline(track.id).isEmpty();
            if(music&&enabled()){begin();counted=true;}delegate=counted?factory(upstream).createDataSource():upstream.createDataSource();
            for(var listener:listeners)delegate.addTransferListener(listener);return delegate.open(spec);}catch(java.io.IOException|RuntimeException error){close();throw error;}
        }
        @Override public int read(byte[] buffer,int offset,int length)throws java.io.IOException{return delegate.read(buffer,offset,length);}
        @Override public Uri getUri(){return delegate==null?null:delegate.getUri();}
        @Override public java.util.Map<String,java.util.List<String>> getResponseHeaders(){return delegate==null?java.util.Map.of():delegate.getResponseHeaders();}
        @Override public void close()throws java.io.IOException{try{if(delegate!=null)delegate.close();}finally{delegate=null;if(counted){counted=false;end();}}}
    };}
    public void cancelPrefetch(){generation.incrementAndGet();CacheWriter writer=active;if(writer!=null)writer.cancel();warmers.getQueue().clear();}
    public void prefetch(DataSource.Factory upstream,List<Track> upcoming){
        cancelPrefetch();if(!prefetchEnabled())return;long request=generation.get();
        warmers.execute(()->{
            synchronized(this){if(generation.get()!=request||!prefetchEnabled())return;begin();}
            try{for(Track track:upcoming){
                if(generation.get()!=request)return;
                if(java.util.List.of("device","files","radio").contains(track.sourceId))continue;
                Source source=sources.get(track.sourceId);if(source==null||track.uri.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")||!library.offline(track.id).isEmpty())continue;
                CacheWriter writer=new CacheWriter(factory(upstream).createDataSource(),new DataSpec.Builder().setUri(Uri.parse("selfonium://track/"+Uri.encode(track.id))).setLength(512*1024).build(),null,null);
                active=writer;
                if(generation.get()!=request){writer.cancel();return;}
                try{writer.cache();}catch(Exception ignored){/* Partial cache remains useful; never fail playback. */}
                finally{if(active==writer)active=null;}
            }}catch(Exception ignored){/* Source/cache initialization failure must not kill the audio process. */}finally{end();}
        });
    }
}
