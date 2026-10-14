package app.selfonium.update;

import app.selfonium.net.Http;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import static app.selfonium.update.UpdatePolicy.*;

/** GitHub-only HTTPS transport. No auth, automatic retries or cross-origin credentials. */
public final class UpdateHttp {
    public static final String LATEST="https://api.github.com/repos/TripShuti/SELFonium/releases/latest";
    private final OkHttpClient client;
    public UpdateHttp(){this(Http.clientBuilder().build());}
    UpdateHttp(OkHttpClient client){this.client=client.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(15,TimeUnit.MINUTES).build();}
    public static final class Cancel {
        private volatile boolean canceled;private Call call;
        public synchronized void cancel(){canceled=true;if(call!=null)call.cancel();}
        synchronized void attach(Call value){call=value;if(canceled)value.cancel();}
        synchronized void detach(Call value){if(call==value)call=null;}
        public void check()throws Failure{if(canceled||Thread.currentThread().isInterrupted())throw new Failure(Reason.CANCELED);}
    }
    public final class Body implements AutoCloseable {
        final Response response;final Call call;final Cancel cancel;
        Body(Response response,Call call,Cancel cancel){this.response=response;this.call=call;this.cancel=cancel;}
        public InputStream input(){return response.body().byteStream();}
        public long length(){return response.body().contentLength();}
        public void close(){response.close();cancel.detach(call);}
    }
    public static boolean allowed(String url){
        try{URI uri=URI.create(url);return "https".equals(uri.getScheme())&&uri.getRawUserInfo()==null&&uri.getRawFragment()==null&&(uri.getPort()==-1||uri.getPort()==443)&&Set.of("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(uri.getHost());}catch(Exception e){return false;}
    }
    public Body open(String url,Cancel cancel)throws IOException{
        for(int redirects=0;redirects<6;redirects++){
            cancel.check();if(!allowed(url))throw new Failure(Reason.INVALID_RELEASE);
            Request.Builder builder=new Request.Builder().url(url).header("User-Agent","SELFonium-updater").header("Accept",url.equals(LATEST)?"application/vnd.github+json":"application/octet-stream");
            if(url.equals(LATEST))builder.header("X-GitHub-Api-Version","2022-11-28");
            Call call=client.newCall(builder.build());if(url.equals(LATEST)||url.endsWith(".json"))call.timeout().timeout(30,TimeUnit.SECONDS);cancel.attach(call);Response response;
            try{response=call.execute();}catch(IOException e){cancel.detach(call);cancel.check();throw new Failure(Reason.NETWORK);}
            int code=response.code();
            if(code>=300&&code<400){String location=response.header("Location");response.close();cancel.detach(call);if(location==null)throw new Failure(Reason.HTTP,code,0);try{url=URI.create(url).resolve(location).toString();}catch(Exception e){throw new Failure(Reason.INVALID_RELEASE);}continue;}
            if(code<200||code>=300){
                long retryAt=0;boolean limited=code==429||code==403&&("0".equals(response.header("X-RateLimit-Remaining"))||response.header("Retry-After")!=null);
                if(limited){
                    long now=System.currentTimeMillis(),delay=MANUAL_INTERVAL;String after=response.header("Retry-After");
                    try{delay=Math.max(delay,Math.multiplyExact(Long.parseLong(after),1000));}catch(Exception ignored){try{delay=Math.max(delay,java.time.ZonedDateTime.parse(after,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now);}catch(Exception invalid){}}
                    try{delay=Math.max(delay,Math.multiplyExact(Long.parseLong(response.header("X-RateLimit-Reset")),1000)-now);}catch(Exception ignored){}
                    retryAt=now+Math.min(delay,7*DAY);
                }
                response.close();cancel.detach(call);throw new Failure(limited?Reason.RATE_LIMIT:code==404?Reason.NOT_FOUND:Reason.HTTP,code,retryAt);
            }
            if(response.body()==null){response.close();cancel.detach(call);throw new Failure(Reason.NETWORK);}
            return new Body(response,call,cancel);
        }
        throw new Failure(Reason.HTTP);
    }
    public String text(String url,int limit,Cancel cancel)throws IOException{
        try(Body body=open(url,cancel);InputStream in=body.input();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(body.length()>limit)throw new Failure(Reason.INVALID_RELEASE);
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){cancel.check();if(out.size()+n>limit)throw new Failure(Reason.INVALID_RELEASE);out.write(buffer,0,n);}return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    public ReleaseInfo latest(Cancel cancel)throws IOException{
        ReleaseInfo.Assets assets=ReleaseInfo.assets(text(LATEST,256*1024,cancel));return assets==null?null:assets.manifest(text(assets.manifestUrl,16*1024,cancel));
    }
}
