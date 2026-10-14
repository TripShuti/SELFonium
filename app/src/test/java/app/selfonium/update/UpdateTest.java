package app.selfonium.update;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.json.*;
import static app.selfonium.update.UpdatePolicy.*;

/** Production parsers, HTTPS policy and streaming code against local HTTP fixtures only. */
public final class UpdateTest {
    private static int checks;
    private static volatile String fault="";
    private static final byte[] apk=new byte[256*1024];
    private static String hash;
    private static final String SIGNER="a".repeat(64),OTHER="b".repeat(64);
    private static final AtomicInteger hits=new AtomicInteger(),apkHits=new AtomicInteger();
    interface Work {void run()throws Exception;}
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static void failure(Reason reason,Work work)throws Exception{try{work.run();throw new AssertionError("Accepted "+reason);}catch(Failure e){check(e.reason==reason,"Expected "+reason+", received "+e.reason);}}
    static JSONObject asset(String suffix,long size){return new JSONObject().put("name","SELFonium-v0.6.4"+suffix).put("size",size).put("state","uploaded").put("browser_download_url",ReleaseInfo.REPO+"v0.6.4/SELFonium-v0.6.4"+suffix);}
    static JSONObject release(){return new JSONObject().put("draft",false).put("prerelease",false).put("tag_name","v0.6.4").put("published_at","2026-10-14T20:00:00Z").put("body","## Changes\n<script>alert('literal text only')</script>").put("assets",new JSONArray().put(asset(".apk",apk.length)).put(asset(".json",500)));}
    static JSONObject manifest(){return new JSONObject().put("package",PACKAGE).put("version","0.6.4").put("versionCode",11).put("sha256",hash).put("signerSha256",SIGNER).put("debuggable",false).put("workingTreeDirty",false);}
    static ReleaseInfo info()throws Exception{return ReleaseInfo.restore(release().toString(),manifest().toString());}
    static void archive(ReleaseInfo info,String name,long code,String version,boolean debug,int minSdk,Set<String> installed,Set<String> past,Set<String> offered,Set<String> history,boolean rotation)throws Exception{
        UpdatePolicy.archive(name,code,version,debug,minSdk,35,info,10,installed,past,offered,history,rotation);
    }
    public static void main(String[] args)throws Exception{
        new Random(31).nextBytes(apk);hash=UpdateDownload.hex(MessageDigest.getInstance("SHA-256").digest(apk));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);ExecutorService handlers=Executors.newCachedThreadPool();server.setExecutor(handlers);
        server.createContext("/",exchange->{
            try{
                hits.incrementAndGet();String path=exchange.getRequestURI().getPath(),mode=fault;
                if(exchange.getRequestHeaders().getFirst("Authorization")!=null)throw new AssertionError("Unexpected credentials");
                if(mode.equals("network")){exchange.close();return;}
                if(mode.equals("404")||mode.equals("403")||mode.equals("429")||mode.equals("limit")||mode.equals("rate-date")){
                    int code=mode.equals("limit")?403:mode.equals("rate-date")?429:Integer.parseInt(mode);
                    if(code==429)exchange.getResponseHeaders().set("Retry-After",mode.equals("rate-date")?java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(120).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME):"120");
                    if(mode.equals("limit")){exchange.getResponseHeaders().set("X-RateLimit-Remaining","0");exchange.getResponseHeaders().set("X-RateLimit-Reset",Long.toString(System.currentTimeMillis()/1000+3600));}
                    exchange.sendResponseHeaders(code,-1);return;
                }
                if(mode.equals("redirect-http")||mode.equals("loop")){
                    exchange.getResponseHeaders().set("Location",mode.equals("loop")?UpdateHttp.LATEST:"http://github.com/TripShuti/SELFonium");exchange.sendResponseHeaders(302,-1);return;
                }
                byte[] body;
                if(path.endsWith("/latest")){
                    JSONObject json=release();if(mode.equals("draft"))json.put("draft",true);if(mode.equals("prerelease"))json.put("prerelease",true);if(mode.equals("no-apk"))json.put("assets",new JSONArray());
                    body=(mode.equals("json")?"broken {":json.toString()).getBytes(StandardCharsets.UTF_8);
                }else if(path.endsWith(".json"))body=manifest().toString().getBytes(StandardCharsets.UTF_8);
                else if(path.endsWith(".apk")){
                    apkHits.incrementAndGet();
                    if(mode.equals("redirect-cdn")&&!path.equals("/content.apk")){exchange.getResponseHeaders().set("Location","https://release-assets.githubusercontent.com/content.apk");exchange.sendResponseHeaders(302,-1);return;}
                    body=apk;
                }else{exchange.sendResponseHeaders(404,-1);return;}
                exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body,0,mode.equals("interrupted")?body.length/2:body.length);
            }catch(IOException ignored){}finally{exchange.close();}
        });server.start();
        String local="http://127.0.0.1:"+server.getAddress().getPort();
        // Mapping is test-only; production validates the original GitHub HTTPS URI before every call.
        OkHttpClient routed=new OkHttpClient.Builder().addInterceptor(chain->chain.proceed(chain.request().newBuilder().url(local+chain.request().url().encodedPath()).build())).build();
        UpdateHttp http=new UpdateHttp(routed);Path temp=Files.createTempDirectory("selfonium-updates-");
        try{
            ReleaseInfo info=http.latest(new UpdateHttp.Cancel());check(info.code==11,"Manifest versionCode");check(apkHits.get()==0,"Checks do not download APK");
            ReleaseInfo wider=ReleaseInfo.restore(release().toString().replace("0.6.4","0.10.0"),manifest().put("version","0.10.0").put("versionCode",12).toString());check(newer(wider.code,11),"Use versionCode instead of lexical versionName order");
            check(newer(info.code,10),"Update available");check(!newer(info.code,11),"Same version");check(!newer(info.code,12),"Installed is newer");check(!newer(0,10),"Invalid versionCode");
            for(String type:List.of("draft","prerelease")){fault=type;check(http.latest(new UpdateHttp.Cancel())==null,"Ignore "+type);}
            for(String mode:List.of("404","403","429","limit","network","json","no-apk","redirect-http")){
                fault=mode;Reason reason=switch(mode){case "404"->Reason.NOT_FOUND;case "403"->Reason.HTTP;case "429","limit"->Reason.RATE_LIMIT;case "network"->Reason.NETWORK;case "json","redirect-http"->Reason.INVALID_RELEASE;default->Reason.NO_ASSET;};
                int before=hits.get();failure(reason,()->http.latest(new UpdateHttp.Cancel()));check(hits.get()==before+1,"No automatic retry: "+mode);
            }
            fault="429";try{http.latest(new UpdateHttp.Cancel());}catch(Failure e){check(e.retryAt>=System.currentTimeMillis()+119_000,"Retry-After respected");}
            fault="rate-date";try{http.latest(new UpdateHttp.Cancel());}catch(Failure e){check(e.retryAt>=System.currentTimeMillis()+118_000,"HTTP-date Retry-After respected");}
            fault="limit";try{http.latest(new UpdateHttp.Cancel());}catch(Failure e){check(e.retryAt>=System.currentTimeMillis()+3_590_000,"Rate reset respected");}
            fault="loop";int before=hits.get();failure(Reason.HTTP,()->http.latest(new UpdateHttp.Cancel()));check(hits.get()==before+6,"Redirect limit");fault="";
            for(Object code:List.of(0,-1,11.5,"11",Long.MAX_VALUE))failure(Reason.INVALID_RELEASE,()->ReleaseInfo.restore(release().toString(),manifest().put("versionCode",code).toString()));
            failure(Reason.INVALID_RELEASE,()->ReleaseInfo.restore(release().toString(),manifest().put("version","0.6.5").toString()));
            failure(Reason.INVALID_RELEASE,()->ReleaseInfo.assets(release().put("tag_name","v../bad").toString()));
            failure(Reason.INVALID_RELEASE,()->ReleaseInfo.restore(release().toString(),manifest().put("sha256","invalid").toString()));
            failure(Reason.INVALID_RELEASE,()->ReleaseInfo.restore(release().toString(),manifest().put("debuggable",true).toString()));
            failure(Reason.INVALID_RELEASE,()->ReleaseInfo.restore(release().toString(),manifest().put("workingTreeDirty",true).toString()));
            JSONObject duplicate=release();duplicate.getJSONArray("assets").put(asset(".apk",apk.length));failure(Reason.INVALID_RELEASE,()->ReleaseInfo.assets(duplicate.toString()));
            JSONObject missing=release();missing.put("assets",new JSONArray().put(asset(".apk",apk.length)));failure(Reason.NO_ASSET,()->ReleaseInfo.assets(missing.toString()));
            JSONObject badUrl=release();badUrl.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://evil.example/update.apk");failure(Reason.INVALID_RELEASE,()->ReleaseInfo.assets(badUrl.toString()));
            check(!UpdateHttp.allowed("http://github.com/a"),"No HTTP");check(!UpdateHttp.allowed("https://github.com.evil.example/a"),"No suffix spoof");check(!UpdateHttp.allowed("https://user@github.com/a"),"No userinfo");check(!UpdateHttp.allowed("https://github.com:444/a"),"No alternate port");check(UpdateHttp.allowed("https://release-assets.githubusercontent.com/a?signature=fixture"),"GitHub asset CDN");
            long day=DAY+100;check(due(day,0,0,true),"First automatic check");check(!due(day+DAY-1,day,0,true),"Automatic checks limited");check(due(day+DAY,day,0,true),"Daily boundary");check(!due(day+59_999,day,0,false),"Manual repeated click limited");check(due(day+60_000,day,0,false),"Manual retry boundary");check(!due(day+DAY,day,day+2*DAY,false),"API cooldown wins");check(due(day-1,day,0,true),"Clock rollback recovers");
            Set<String> old=Set.of(SIGNER),next=Set.of(OTHER),lineage=Set.of(SIGNER,OTHER);
            archive(info,PACKAGE,11,"0.6.4",false,26,old,old,old,old,true);check(true,"Compatible current signer");
            failure(Reason.PACKAGE,()->archive(info,"other.app",11,"0.6.4",false,26,old,old,old,old,true));
            failure(Reason.VERSION,()->archive(info,PACKAGE,10,"0.6.4",false,26,old,old,old,old,true));
            failure(Reason.VERSION,()->archive(info,PACKAGE,11,"wrong",false,26,old,old,old,old,true));
            failure(Reason.VERSION,()->archive(info,PACKAGE,11,"0.6.4",true,26,old,old,old,old,true));
            failure(Reason.VERSION,()->archive(info,PACKAGE,11,"0.6.4",false,36,old,old,old,old,true));
            failure(Reason.SIGNATURE,()->archive(info,PACKAGE,11,"0.6.4",false,26,old,old,next,next,true));
            check(compatible(old,old,next,lineage,true),"Verified forward rotation");check(!compatible(old,old,next,next,true),"Unrelated certificate rejected");check(!compatible(next,lineage,old,old,true),"Signing rollback rejected");check(!compatible(old,old,next,lineage,false),"Legacy requires exact signer");check(compatible(lineage,lineage,lineage,lineage,true),"Exact multiple signers");check(!compatible(lineage,lineage,old,lineage,true),"Partial multiple signers rejected");check(!compatible(Set.of(),Set.of(),old,old,true),"Missing installed signer rejected");
            File dir=temp.toFile();AtomicInteger progress=new AtomicInteger();
            File result=UpdateDownload.fetch(http,info,dir,new UpdateHttp.Cancel(),(done,total)->{check(done<=total,"Bounded progress");progress.incrementAndGet();},()->MAX_APK);
            check(progress.get()>0&&result.length()==apk.length&&UpdateDownload.digest(result,new UpdateHttp.Cancel()).equals(hash),"Completed download and hash");result.delete();
            ReleaseInfo wrong=ReleaseInfo.restore(release().toString(),manifest().put("sha256","0".repeat(64)).toString());
            failure(Reason.INTEGRITY,()->UpdateDownload.fetch(http,wrong,dir,new UpdateHttp.Cancel(),(d,t)->{},()->MAX_APK));check(dir.list().length==0,"Hash failure cleanup");
            failure(Reason.SPACE,()->UpdateDownload.fetch(http,info,dir,new UpdateHttp.Cancel(),(d,t)->{},()->0));
            AtomicInteger space=new AtomicInteger();failure(Reason.SPACE,()->UpdateDownload.fetch(http,info,dir,new UpdateHttp.Cancel(),(d,t)->{},()->space.getAndIncrement()==0?MAX_APK:0));check(dir.list().length==0,"Space failure cleanup");
            UpdateHttp.Cancel cancel=new UpdateHttp.Cancel();failure(Reason.CANCELED,()->UpdateDownload.fetch(http,info,dir,cancel,(d,t)->cancel.cancel(),()->MAX_APK));check(dir.list().length==0,"Canceled partial cleanup");
            UpdateHttp.Cancel early=new UpdateHttp.Cancel();early.cancel();failure(Reason.CANCELED,()->http.latest(early));
            fault="interrupted";failure(Reason.NETWORK,()->UpdateDownload.fetch(http,info,dir,new UpdateHttp.Cancel(),(d,t)->{},()->MAX_APK));check(dir.list().length==0,"Interrupted partial cleanup");
            fault="redirect-cdn";File cdn=UpdateDownload.fetch(http,info,dir,new UpdateHttp.Cancel(),(d,t)->{},()->MAX_APK);check(cdn.length()==apk.length,"HTTPS CDN redirect download");cdn.delete();
            System.out.println("PASS: "+checks+" updater checks (metadata, HTTP failures, throttling, integrity, identity, rotation policy, progress, cancellation and cleanup)");
        }finally{server.stop(0);handlers.shutdownNow();routed.dispatcher().executorService().shutdownNow();routed.connectionPool().evictAll();try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
