package app.selfonium.core;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.UUID;

/** Listening time follows a monotonic clock, never the seek position or time spent paused. */
public final class ListeningSession {
    public static final int MAX_PENDING=10_000;
    public static final long RETENTION=90L*24*60*60*1000;
    public record State(String session,String track,long started,long listened,boolean submitted){}
    private final String session,track;private long started,listened,last;private boolean playing,submitted;
    public ListeningSession(String track,long elapsed){this(new State(UUID.randomUUID().toString(),track,0,0,false),elapsed);}
    public ListeningSession(State saved,long elapsed){session=saved.session;track=saved.track;started=Math.max(0,saved.started);listened=Math.max(0,saved.listened);submitted=saved.submitted;last=elapsed;}
    public void advance(long elapsed,long wall,boolean nowPlaying){
        if(playing&&elapsed>=last)listened=ManagedFiles.saturatedAdd(listened,elapsed-last);
        if(nowPlaying&&started==0)started=Math.max(1,wall);last=Math.max(last,elapsed);playing=nowPlaying;
    }
    public static long threshold(long duration){return duration<=0?Long.MAX_VALUE:Math.min(duration/2+duration%2,240_000);}
    public boolean eligible(long duration){return !submitted&&started>0&&duration>0&&listened>=threshold(duration);}
    public State state(){return new State(session,track,started,listened,submitted);}
    public void submitted(){submitted=true;}
    public static String account(Source source){try{return Protocol.hex(MessageDigest.getInstance("SHA-256").digest((source.baseUrl+"\n"+source.username).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    public static long retryDelay(int attempts){return Math.min(15*60_000L,30_000L*(1L<<Math.min(5,Math.max(0,attempts))));}
}
