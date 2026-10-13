package app.selfonium.core;

import java.util.Map;

/** Runs without Android or downloaded test frameworks: scripts/test-core.sh. */
public final class CoreTest {
    private static int checks;
    private static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    private static void rejects(String value){try{Protocol.base(value);throw new AssertionError("Accepted invalid base: "+value);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        check(Protocol.base(" https://example.org/music/// ").equals("https://example.org/music"),"reverse proxy base path");
        check(Protocol.base("http://[::1]:8096").equals("http://[::1]:8096"),"IPv6 address");
        rejects("file:///private");rejects("https://u:password@example.org");rejects("https://example.org?token=secret");rejects("example.org");rejects("https://example.org/#fragment");
        check(Protocol.encode("Björk / 東京 +&").equals("Bj%C3%B6rk%20%2F%20%E6%9D%B1%E4%BA%AC%20%2B%26"),"UTF-8 encoding");
        check(Protocol.query("https://x/a?existing=1",Map.of("id","a/b")).equals("https://x/a?existing=1&id=a%2Fb"),"append query");
        Source source=new Source("id","server",Source.Type.SUBSONIC,"https://x/music","alice","password");
        String request=Protocol.subsonic(source,"stream",Protocol.params("id","a&b","format","raw"),"salt");
        check(request.startsWith("https://x/music/rest/stream.view?"),"keep subpath");
        check(request.contains("t=b305cadbb3bce54f3aa59c64fec00dea"),"Subsonic MD5 password+salt known vector");
        check(!request.contains("password"),"no raw password in query");
        check(request.contains("id=a%26b"),"ID cannot inject query");
        check(!Protocol.subsonic(source,"ping",Map.of()).equals(Protocol.subsonic(source,"ping",Map.of())),"unique salts");
        check(Protocol.sameOrigin("https://x/music","https://x:443/stream"),"explicit default port");
        check(!Protocol.sameOrigin("https://x/music","http://x/music"),"reject TLS downgrade");
        check(!Protocol.sameOrigin("https://x","https://other"),"reject host redirect");
        check(!Protocol.sameOrigin("https://x:443","https://x:8443"),"reject port redirect");
        check(Protocol.audio("/Music/SONG.FLAC"),"case insensitive extension");
        check(!Protocol.audio("/Music/song.flac.txt"),"extension boundary");
        check(Protocol.time(-20).equals("0:00"),"unknown duration");
        check(Protocol.time(3661000).equals("1:01:01"),"audiobook hours");
        Track track=new Track("id","source","remote","",null,"",null,-1,-1);
        check(track.durationMs==0&&track.number==0&&!track.title.isEmpty()&&!track.artist.isEmpty(),"incomplete metadata");
        Track covered=track.withArtwork("al-opaque/abc&size=1");
        check(track.artwork.isEmpty()&&covered.artwork.equals("al-opaque/abc&size=1")&&covered.id.equals(track.id)&&covered.sourceId.equals(track.sourceId),"artwork copy preserves track identity");
        String coverRequest=Protocol.subsonic(source,"getCoverArt",Protocol.params("id",covered.artwork,"size","768"),"salt");
        check(coverRequest.contains("/rest/getCoverArt.view?")&&coverRequest.contains("id=al-opaque%2Fabc%26size%3D1")&&coverRequest.contains("size=768"),"opaque cover ID encoded independently of track ID");
        check(coverRequest.contains("t=b305cadbb3bce54f3aa59c64fec00dea")&&!coverRequest.contains("password"),"artwork uses authenticated token");
        check(EqualizerCurve.clamp(1800,-1200,1200)==1200&&EqualizerCurve.clamp(-1800,-1200,1200)==-1200,"EQ clamps to actual device range");
        check(EqualizerCurve.preset(0,60,-1500,1500)==0&&EqualizerCurve.preset(0,16000,-1500,1500)==0,"flat preset resets bands");
        check(EqualizerCurve.preset(1,60,-1500,1500)>EqualizerCurve.preset(1,1000,-1500,1500),"bass preset weights low frequencies");
        check(EqualizerCurve.preset(3,12000,-1500,1500)>EqualizerCurve.preset(3,1000,-1500,1500),"treble preset weights high frequencies");
        check(EqualizerCurve.preset(2,1000,-1500,1500)>EqualizerCurve.preset(2,60,-1500,1500),"vocal preset weights mids");
        check(EqualizerCurve.preset(1,60,-100,100)==100,"preset respects narrow gain range");
        System.out.println("PASS: "+checks+" core checks");
    }
}
