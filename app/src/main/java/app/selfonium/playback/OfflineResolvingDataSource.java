package app.selfonium.playback;

import android.content.Context;
import android.net.Uri;
import androidx.media3.datasource.*;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.ManagedFiles;
import app.selfonium.core.Track;
import app.selfonium.data.OfflineStore;
import app.selfonium.net.SubsonicClient;
import java.io.IOException;
import java.util.*;

/** Acquire the file lease at resolution time, including downloads completed during buffering. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class OfflineResolvingDataSource implements DataSource {
    private final ResolvingDataSource delegate;private ManagedFiles.Lease lease;private Uri original;
    OfflineResolvingDataSource(Context context,SelfoniumApp app){
        delegate=new ResolvingDataSource(new DefaultDataSource.Factory(context,SafeHttpDataSource::new).createDataSource(),new ResolvingDataSource.Resolver(){
            @Override public Uri resolveReportedUri(Uri uri){return original==null?uri:original;}
            @Override public DataSpec resolveDataSpec(DataSpec spec)throws IOException{
                original=spec.uri;if(!"selfonium".equals(spec.uri.getScheme()))return spec;
                String id=spec.uri.getLastPathSegment();Track track=app.library.get(id);if(track==null)throw new IOException("Композицію видалено з бібліотеки");
                OfflineStore.Read offline=app.offline.open(id);
                if(offline!=null){lease=offline.lease();return spec.withUri(Uri.fromFile(offline.file()));}
                try{SubsonicClient.Stream stream=app.subsonic.stream(track);return spec.withUri(Uri.parse(stream.url())).withRequestHeaders(stream.headers());}
                catch(Exception error){throw new IOException("Не вдалося відкрити аудіо. Перевірте джерело або синхронізуйте його повторно.",error);}
            }
        });
    }
    @Override public void addTransferListener(TransferListener listener){delegate.addTransferListener(listener);}
    @Override public long open(DataSpec spec)throws IOException{try{return delegate.open(spec);}catch(IOException|RuntimeException error){close();throw error;}}
    @Override public int read(byte[] buffer,int offset,int length)throws IOException{return delegate.read(buffer,offset,length);}
    @Override public Uri getUri(){return delegate.getUri();}
    @Override public Map<String,List<String>> getResponseHeaders(){return delegate.getResponseHeaders();}
    @Override public void close()throws IOException{try{delegate.close();}finally{if(lease!=null){lease.close();lease=null;}original=null;}}
}
