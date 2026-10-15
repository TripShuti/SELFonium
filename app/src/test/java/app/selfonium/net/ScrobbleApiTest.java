package app.selfonium.net;

import app.selfonium.core.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Actual HTTP requests, opaque IDs and advertised/fallback protocol fixtures. */
public final class ScrobbleApiTest {
    private static int checks,pings,discoveries,calls,status=200,apiError;private static Map<String,String> last;private static String endpoint;private static boolean advertised=true,malformed;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Scrobble API check "+checks);}
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/rest/",exchange->{try{
            calls++;last=new HashMap<>();for(String pair:exchange.getRequestURI().getRawQuery().split("&")){String[] parts=pair.split("=",2);last.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}
            endpoint=exchange.getRequestURI().getPath().replace("/rest/","").replace(".view","");
            JSONObject response=new JSONObject().put("status","ok");if(status!=200){exchange.sendResponseHeaders(status,-1);return;}
            if(endpoint.equals("ping")){pings++;response.put("openSubsonic",advertised);}else if(endpoint.equals("getOpenSubsonicExtensions")){discoveries++;response.put("openSubsonicExtensions",new JSONArray().put(new JSONObject().put("name","playbackReport").put("versions",new JSONArray(List.of(1)))));}
            if(apiError!=0)response=new JSONObject().put("status","failed").put("error",new JSONObject().put("code",apiError));
            byte[] bytes=(malformed?"{broken":new JSONObject().put("subsonic-response",response).toString()).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
        }finally{exchange.close();}});server.start();
        try{
            Source source=new Source("fixture","Fixture",Source.Type.SUBSONIC,"http://127.0.0.1:"+server.getAddress().getPort(),"tester","fixture-password");SubsonicClient client=new SubsonicClient(null);Http.Cancel cancel=new Http.Cancel();
            client.discoverPlayback(source,cancel);check(client.supports(source,"playbackReport",1));client.discoverPlayback(source,cancel);check(pings==1&&discoveries==1);
            String id="opaque/id & α";long started=1_750_000_000_123L;
            client.scrobble(source,id,started,false,cancel);check(endpoint.equals("scrobble"));check(last.get("id").equals(id));check(last.get("time").equals(Long.toString(started)));check(last.get("submission").equals("false"));check(last.get("u").equals("tester"));check(last.get("t").equals(Protocol.md5("fixture-password"+last.get("s"))));check(!last.containsKey("p"));
            client.scrobble(source,id,started,true,cancel);check(last.get("submission").equals("true"));
            for(String state:List.of("starting","playing","paused","stopped")){
                client.reportPlayback(source,id,state,12345,1.5f,cancel);check(endpoint.equals("reportPlayback"));check(last.get("mediaId").equals(id));check(last.get("state").equals(state));check(last.get("mediaType").equals("song"));check(last.get("ignoreScrobble").equals("true"));check(last.get("positionMs").equals("12345"));check(last.get("playbackRate").equals("1.5"));
            }
            client.reportPlayback(source,id,"playing",-1,1,cancel);check(last.get("positionMs").equals("0"));
            client.disablePlaybackReport(source);check(!client.supports(source,"playbackReport",1));
            advertised=false;Source baseline=new Source("legacy","Legacy",Source.Type.SUBSONIC,source.baseUrl,"tester","fixture-password");client.discoverPlayback(baseline,cancel);check(!client.supports(baseline,"playbackReport",1));check(discoveries==1);client.scrobble(baseline,id,started,false,cancel);check(last.get("submission").equals("false"));
            for(int code:List.of(403,404,429,500)){status=code;try{client.scrobble(source,id,started,true,cancel);throw new AssertionError("HTTP error accepted");}catch(Http.StatusException error){check(error.status==code);}}status=200;
            apiError=70;try{client.scrobble(source,id,started,true,cancel);throw new AssertionError("API error accepted");}catch(SubsonicClient.ApiException error){check(error.code==70);}apiError=0;
            malformed=true;try{client.scrobble(source,id,started,true,cancel);throw new AssertionError("Invalid JSON accepted");}catch(JSONException expected){check(true);}malformed=false;
            int before=calls;Http.Cancel cancelled=new Http.Cancel();cancelled.cancel();try{client.scrobble(source,id,started,true,cancelled);throw new AssertionError("Cancelled request executed");}catch(java.io.IOException expected){check(calls==before);}
            try{client.reportPlayback(source,id,"invalid",0,1,cancel);throw new AssertionError();}catch(IllegalArgumentException expected){check(calls==before);}
            server.stop(0);try{client.scrobble(source,id,started,true,cancel);throw new AssertionError("Unavailable server accepted");}catch(java.io.IOException expected){check(true);}
            System.out.println("PASS: "+checks+" scrobble/report HTTP checks (identity, timestamps, states, client-owned counting flags, discovery, fallback and failures)");
        }finally{server.stop(0);}
    }
}
