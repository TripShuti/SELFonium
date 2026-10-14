package app.selfonium.net;

import app.selfonium.core.*;
import app.selfonium.data.SourceStore;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Concurrent production HTTP requests, pagination, strict snapshots and safe diagnostics. */
public final class CatalogSyncTest {
    static int checks;
    static volatile int albums=12,delay=35;
    static volatile String fault="";
    static final AtomicInteger active=new AtomicInteger(),maximum=new AtomicInteger(),requests=new AtomicInteger(),pages=new AtomicInteger();
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Catalog check "+checks);}
    static void quiet() throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(active.get()>0&&System.nanoTime()<end)Thread.sleep(5);
        check(active.get()==0);
    }
    public static void main(String[] args) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService handlers=Executors.newFixedThreadPool(8);server.setExecutor(handlers);
        server.createContext("/rest/",exchange->{
            boolean albumRequest=false;
            try{
                String mode=fault;
                Map<String,String> query=new HashMap<>();for(String part:exchange.getRequestURI().getRawQuery().split("&")){String[] pair=part.split("=",2);query.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
                String method=exchange.getRequestURI().getPath().replace("/rest/","").replace(".view","");
                JSONObject response=new JSONObject().put("status","ok");
                switch(method){
                    case "ping"->response.put("openSubsonic",mode.startsWith("extensions"));
                    case "getOpenSubsonicExtensions"->{
                        if(mode.equals("extensions-network")){exchange.close();return;}
                        if(mode.equals("extensions-http")){exchange.sendResponseHeaders(404,-1);return;}
                        if(mode.equals("extensions-api"))response.put("status","failed").put("error",new JSONObject().put("code",70));
                        else response.put("openSubsonicExtensions","bad-sensitive-token");
                    }
                    case "getAlbumList2"->{
                        pages.incrementAndGet();
                        if(!mode.equals("missing-list")){
                            int offset=mode.equals("repeat-page")?0:Integer.parseInt(query.get("offset"));
                            JSONArray list=new JSONArray();for(int at=offset;at<Math.min(offset+500,albums);at++)list.put(new JSONObject().put("id","album-"+at));
                            response.put("albumList2",new JSONObject().put("album",mode.equals("invalid-list")?"bad-sensitive-token":list));
                        }
                    }
                    case "getAlbum"->{
                        albumRequest=true;requests.incrementAndGet();maximum.accumulateAndGet(active.incrementAndGet(),Math::max);
                        int at=Integer.parseInt(query.get("id").substring(6));
                        Thread.sleep(delay==0?0:delay+(2-at%3)*8L);
                        if(mode.equals("album-fails")&&at==1)response.put("status","failed").put("error",new JSONObject().put("code",70));
                        else {
                            JSONArray songs=new JSONArray().put(new JSONObject().put("id","song-"+at).put("title","Song "+at).put("duration",120));
                            response.put("album",new JSONObject().put("name","Album "+at).put("coverArt","cover-"+at).put("song",mode.equals("invalid-songs")?"bad-sensitive-token":songs));
                        }
                    }
                    case "getStarred2"->response.put("starred2",new JSONObject().put("song","bad-sensitive-token"));
                    case "getPlaylists"->response.put("playlists",new JSONObject().put("playlist","bad-sensitive-token"));
                    default->throw new AssertionError(method);
                }
                byte[] bytes=new JSONObject().put("subsonic-response",response).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Connection","close");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(Exception failure){try{exchange.sendResponseHeaders(500,-1);}catch(Exception ignored){}}
            finally{if(albumRequest)active.decrementAndGet();exchange.close();}
        });server.start();
        List<String> logs=Collections.synchronizedList(new ArrayList<>());
        Source source=new Source("fixture","Fixture",Source.Type.SUBSONIC,"http://127.0.0.1:"+server.getAddress().getPort(),"sensitive-user","sensitive-password");
        SubsonicClient client=new SubsonicClient(new SourceStore(),logs::add);
        try{
            long start=System.nanoTime();
            for(int at=0;at<albums;at++)Http.text("GET",Protocol.subsonic(source,"getAlbum",Map.of("id","album-"+at)),Map.of(),null);
            long sequential=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            requests.set(0);maximum.set(0);start=System.nanoTime();
            List<Track> tracks=client.synchronize(source,status->{});
            long parallel=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            check(tracks.size()==albums);check(maximum.get()==3);check(requests.get()==albums);
            for(int at=0;at<tracks.size();at++){check(tracks.get(at).remoteId.equals("song-"+at));check(tracks.get(at).artwork.equals("cover-"+at));}
            System.out.println("MEASURE: 12 albums with simulated latency: sequential="+sequential+"ms, bounded parallel="+parallel+"ms; no timing threshold asserted");
            albums=3000;delay=0;requests.set(0);maximum.set(0);pages.set(0);start=System.nanoTime();
            tracks=client.synchronize(source,status->{});
            check(tracks.size()==3000);check(requests.get()==3000);check(pages.get()==7);check(maximum.get()<=3);
            for(int at=0;at<tracks.size();at++)if(!tracks.get(at).remoteId.equals("song-"+at))throw new AssertionError("Unstable album order");
            System.out.println("MEASURE: 3000-album fixture="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)+"ms, pages="+pages.get()+", max active album requests="+maximum.get());
            for(String mode:List.of("missing-list","invalid-list","invalid-songs","album-fails","repeat-page")){
                quiet();fault=mode;albums=mode.equals("repeat-page")?501:12;requests.set(0);
                try{client.synchronize(source,status->{});throw new AssertionError("Accepted partial/invalid snapshot: "+mode);}
                catch(java.io.IOException|JSONException expected){check(true);}
                quiet();if(mode.equals("album-fails"))check(requests.get()<=3);
            }
            albums=0;
            for(String mode:List.of("extensions-http","extensions-api","extensions-invalid","extensions-network")){
                fault=mode;logs.clear();check(client.synchronize(source,status->{}).isEmpty());
                String expected=mode.equals("extensions-network")?"network-":mode.equals("extensions-http")?"http-status=404":mode.equals("extensions-api")?"api-code=70":"invalid-response";
                check(logs.stream().anyMatch(line->line.contains(expected)));
                check(logs.stream().noneMatch(line->line.contains(source.baseUrl)||line.contains(source.username)||line.contains(source.password)||line.contains("bad-sensitive-token")));
            }
            try{client.starred(source);throw new AssertionError("Malformed stars accepted as empty");}catch(JSONException expected){check(true);}
            try{client.playlists(source);throw new AssertionError("Malformed playlists accepted as empty");}catch(JSONException expected){check(true);}
            System.out.println("PASS: "+checks+" catalog checks (3000 albums, order, concurrency, failed snapshots and private diagnostics)");
        }finally{server.stop(0);handlers.shutdownNow();}
    }
}
