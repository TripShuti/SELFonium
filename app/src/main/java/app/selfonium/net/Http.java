package app.selfonium.net;

import app.selfonium.core.Protocol;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class Http {
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build();
    private Http() {}
    public static OkHttpClient.Builder clientBuilder(){return CLIENT.newBuilder();}
    public static final class StatusException extends IOException {
        public final int status;
        StatusException(int status){super("HTTP "+status);this.status=status;}
    }
    public static final class Response implements AutoCloseable {
        private final okhttp3.Response response;
        Response(okhttp3.Response response){this.response=response;}
        public InputStream input(){return response.body().byteStream();}
        public int code(){return response.code();}
        public long length(){return response.body().contentLength();}
        public String header(String name){return response.header(name);}
        public Map<String,java.util.List<String>> headers(){return response.headers().toMultimap();}
        @Override public void close(){response.close();}
    }
    public static final class Cancel {
        private volatile boolean cancelled;private volatile okhttp3.Call call;
        public void cancel(){cancelled=true;okhttp3.Call active=call;if(active!=null)active.cancel();}
        public boolean cancelled(){return cancelled;}
        private void attach(okhttp3.Call next)throws IOException{call=next;if(cancelled){next.cancel();throw new IOException("Cancelled");}}
    }
    public static Response open(String method,String url,Map<String,String> headers,byte[] body) throws IOException {return open(method,url,headers,body,null);}
    public static Response open(String method,String url,Map<String,String> headers,byte[] body,Cancel cancel) throws IOException {
        String original=url;
        for(int redirects=0;redirects<6;redirects++) {
            if(Thread.currentThread().isInterrupted())throw new IOException("Операцію скасовано");
            Request.Builder builder=new Request.Builder().url(url).header("User-Agent","SELFonium/0.3");
            for(var entry:headers.entrySet())builder.header(entry.getKey(),entry.getValue());
            RequestBody requestBody=body==null?null:RequestBody.create(body,MediaType.get(headers.getOrDefault("Content-Type","application/octet-stream")));
            okhttp3.Call call=CLIENT.newCall(builder.method(method,requestBody).build());if(cancel!=null)cancel.attach(call);okhttp3.Response response=call.execute();
            int status=response.code();
            if(status>=300&&status<400) {
                String location=response.header("Location");response.close();
                if(location==null)throw new IOException("Сервер повернув перенаправлення без адреси");
                String next=URI.create(url).resolve(location).toString();
                if(!Protocol.sameOrigin(original,next))throw new IOException("Сервер перенаправляє на інший хост. Вкажіть кінцеву адресу джерела.");
                if(!method.equals("GET")&&!method.equals("HEAD")&&status!=307&&status!=308)throw new IOException("Перевірте кінцеву адресу API сервера");
                url=next;continue;
            }
            if(status<200||status>=300){response.close();throw new StatusException(status);}
            return new Response(response);
        }
        throw new IOException("Забагато перенаправлень");
    }
    public static String text(String method,String url,Map<String,String> headers,String body) throws IOException {return text(method,url,headers,body,null);}
    public static String text(String method,String url,Map<String,String> headers,String body,Cancel cancel) throws IOException {
        try(Response response=open(method,url,headers,body==null?null:body.getBytes(StandardCharsets.UTF_8),cancel);InputStream in=response.input();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>32*1024*1024)throw new IOException("Відповідь перевищує 32 МБ");out.write(buffer,0,n);}
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
