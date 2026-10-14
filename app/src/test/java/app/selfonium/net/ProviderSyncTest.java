package app.selfonium.net;
import app.selfonium.core.*;
import app.selfonium.data.SourceStore;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Real HTTP/JSON requests against an isolated Subsonic fixture, never a user's server. */
public final class ProviderSyncTest {
    static int checks,updates;static boolean starred;static boolean openSubsonic,failExtensions;static int extensionCalls;static volatile boolean failApi,failHttp;static String name="Mix";static List<String> entries=new ArrayList<>(List.of("a","b","a"));
    static void check(boolean value){if(!value)throw new AssertionError("Check "+checks);checks++;}
    static JSONObject song(String id){return new JSONObject().put("id",id).put("title",id).put("artist","Artist").put("album","Album").put("duration",42).put("coverArt","al/opaque");}
    static JSONObject playlist(){JSONArray songs=new JSONArray();for(String id:entries)songs.put(song(id));return new JSONObject().put("id","p/1").put("name",name).put("owner","tester").put("entry",songs);}
    public static void main(String[] args)throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/rest/",exchange->{
            try{
                Map<String,String> query=new HashMap<>();for(String part:exchange.getRequestURI().getRawQuery().split("&")){String[] pair=part.split("=",2);query.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
                check(query.get("t").equals(Protocol.md5("fixture-password"+query.get("s"))));check(query.get("u").equals("tester"));
                if(failHttp){failHttp=false;exchange.sendResponseHeaders(401,-1);return;}
                String endpoint=exchange.getRequestURI().getPath().replace("/rest/","").replace(".view","");JSONObject body=new JSONObject().put("status","ok");
                switch(endpoint){
                    case "ping"->body.put("openSubsonic",openSubsonic);
                    case "getAlbumList2"->body.put("albumList2",new JSONObject());
                    case "getOpenSubsonicExtensions"->{extensionCalls++;if(failExtensions)body.put("status","failed");else body.put("openSubsonicExtensions",new JSONArray().put(new JSONObject().put("name","songLyrics").put("versions",new JSONArray(List.of(1,2)))));}

                    case "getStarred2"->body.put("starred2",new JSONObject().put("song",starred?new JSONArray().put(song("a")):new JSONArray()));
                    case "star"->{check("a".equals(query.get("id")));starred=true;}
                    case "unstar"->{check("a".equals(query.get("id")));starred=false;}
                    case "getPlaylists"->body.put("playlists",new JSONObject().put("playlist",new JSONArray().put(playlist())));
                    case "getPlaylist"->{check("p/1".equals(query.get("id")));body.put("playlist",playlist());}
                    case "createPlaylist"->{name=query.get("name");entries=new ArrayList<>();if(query.containsKey("songId"))entries.add(query.get("songId"));body.put("playlist",playlist());}
                    case "updatePlaylist"->{check("p/1".equals(query.get("playlistId")));updates++;if(query.containsKey("name"))name=query.get("name");if(query.containsKey("songIdToAdd"))entries.add(query.get("songIdToAdd"));if(query.containsKey("songIndexToRemove"))entries.remove(Integer.parseInt(query.get("songIndexToRemove")));}
                    case "deletePlaylist"->{check("p/1".equals(query.get("id")));entries.clear();}
                    default->throw new AssertionError("Unexpected endpoint");
                }
                if(failApi){failApi=false;body=new JSONObject().put("status","failed").put("error",new JSONObject().put("code",50));}
                byte[] bytes=new JSONObject().put("subsonic-response",body).toString().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(Throwable failure){byte[] bytes="fixture failure".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(500,bytes.length);exchange.getResponseBody().write(bytes);}
            finally{exchange.close();}
        });server.start();
        try{
            Source source=new Source("one","Fixture",Source.Type.SUBSONIC,"http://127.0.0.1:"+server.getAddress().getPort(),"tester","fixture-password");SubsonicClient providers=new SubsonicClient(new SourceStore());
            check(providers.synchronize(source,status->{}).isEmpty());check(extensionCalls==0);check(!providers.supports(source,"songLyrics",1));
            openSubsonic=true;providers.synchronize(source,status->{});check(extensionCalls==1);check(providers.supports(source,"songLyrics",1));check(providers.supports(source,"songLyrics",2));check(!providers.supports(source,"songLyrics",3));
            failExtensions=true;check(providers.synchronize(source,status->{}).isEmpty());check(!providers.supports(source,"songLyrics",1));failExtensions=false;
            openSubsonic=false;providers.synchronize(source,status->{});check(extensionCalls==2);
            JSONObject old=new JSONObject().put("id","retired").put("type","RETIRED_SERVER").put("url","legacy-invalid-url").put("token","fixture-token");
            JSONObject active=new JSONObject().put("id","one").put("type","SUBSONIC").put("name","Fixture").put("url",source.baseUrl).put("user","tester").put("password","fixture-password");
            app.selfonium.data.SourceRecords vault=new app.selfonium.data.SourceRecords(new JSONArray().put(old).put(active));
            check(vault.supported.size()==1);check(vault.supported.get(0).id.equals("one"));
            JSONArray encoded=vault.encode(vault.supported);check(encoded.length()==2);check(encoded.getJSONObject(0).toString().equals(old.toString()));
            check(new app.selfonium.data.SourceRecords(vault.encode(List.of())).supported.isEmpty());check(vault.encode(List.of()).length()==1);
            check(providers.starred(source).isEmpty());providers.star(source,"a",true);check(providers.starred(source).get(0).id.equals("one:a"));check(providers.starred(source).get(0).artwork.equals("al/opaque"));providers.star(source,"a",false);check(providers.starred(source).isEmpty());
            ServerPlaylist imported=providers.playlists(source).get(0);check(imported.tracks().size()==3);check(imported.tracks().get(0).id.equals(imported.tracks().get(2).id));
            Source second=new Source("two","Other",Source.Type.SUBSONIC,source.baseUrl,"tester","fixture-password");check(providers.playlist(second,"p/1").tracks().get(0).id.equals("two:a"));
            providers.removeFromPlaylist(source,"p/1",2,"a");check(entries.equals(List.of("a","b")));
            int before=updates;try{providers.removeFromPlaylist(source,"p/1",0,"b");throw new AssertionError();}catch(java.io.IOException expected){check(updates==before);}
            Track a=new Track("one:a","one","a","A","Artist","Album","",42_000,1);providers.createPlaylist(source,"Мікс & sound",a);check(entries.equals(List.of("a")));check(name.equals("Мікс & sound"));
            providers.addToPlaylist(source,"p/1",a);check(entries.equals(List.of("a")));
            Track b=new Track("one:b","one","b","B","Artist","Album","",42_000,2);providers.addToPlaylist(source,"p/1",b);check(entries.equals(List.of("a","b")));
            providers.renamePlaylist(source,"p/1","New name");check(name.equals("New name"));check(entries.size()==2);
            providers.deletePlaylist(source,"p/1");check(entries.isEmpty());
            failApi=true;try{providers.star(source,"a",true);throw new AssertionError("API failure accepted");}catch(java.io.IOException expected){check(true);}
            failHttp=true;try{providers.starred(source);throw new AssertionError("HTTP failure accepted");}catch(Http.StatusException expected){check(expected.status==401);}
            System.out.println("PASS: "+checks+" Subsonic HTTP checks (stars, scoped IDs, ordered repeats, create/rename/add/remove/delete and stale-index protection)");
        }finally{server.stop(0);}
    }
}
