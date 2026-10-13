package app.selfonium.playback;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import app.selfonium.net.Http;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Range-aware streaming with the same credential redirect policy as library requests. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class SafeHttpDataSource extends BaseDataSource {
    private Http.Response response;private InputStream input;private Uri uri;private long remaining;private boolean opened;
    public SafeHttpDataSource(){super(true);}
    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);uri=spec.uri;
        Map<String,String> headers=new LinkedHashMap<>(spec.httpRequestHeaders);headers.put("Accept-Encoding","identity");
        if(spec.position>0||spec.length!=C.LENGTH_UNSET)headers.put("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":spec.position+spec.length-1));
        try {
            response=Http.open("GET",uri.toString(),headers,null);input=response.input();
            if(response.code()==206){String range=response.header("Content-Range");if(range==null||!range.startsWith("bytes "+spec.position+"-"))throw new IOException("Сервер повернув некоректний діапазон аудіо");}
            long skipped=response.code()==200?spec.position:0;
            byte[] buffer=new byte[8192];long skip=skipped;
            while(skip>0){int n=input.read(buffer,0,(int)Math.min(skip,buffer.length));if(n<0)throw new EOFException();skip-=n;}
            remaining=spec.length!=C.LENGTH_UNSET?spec.length:response.length()<0?C.LENGTH_UNSET:Math.max(0,response.length()-skipped);
            opened=true;transferStarted(spec);return remaining;
        }catch(IOException|RuntimeException e){close();throw e;}
    }
    @Override public int read(byte[] buffer,int offset,int length)throws IOException{
        if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;
        int n=input.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(length,remaining));
        if(n<0){if(remaining>0)throw new EOFException("Аудіопотік обірвався");return C.RESULT_END_OF_INPUT;}
        if(remaining!=C.LENGTH_UNSET)remaining-=n;bytesTransferred(n);return n;
    }
    @Override public Uri getUri(){return uri;}
    @Override public Map<String,List<String>> getResponseHeaders(){return response==null?Map.of():response.headers();}
    @Override public void close(){if(response!=null)response.close();response=null;input=null;uri=null;if(opened){opened=false;transferEnded();}}
}
