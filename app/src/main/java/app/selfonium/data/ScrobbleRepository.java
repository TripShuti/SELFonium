package app.selfonium.data;

import android.content.SharedPreferences;
import android.os.SystemClock;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.*;
import app.selfonium.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Durable submissions; only the newest live playback state is kept in memory. */
public final class ScrobbleRepository {
    public record Report(String session,String source,String remote,String account,long started,String state,long position,float rate,long elapsed){}
    private final SelfoniumApp app;private final SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final AtomicBoolean queued=new AtomicBoolean();
    private final AtomicReference<Report> latest=new AtomicReference<>();private Report sent;private boolean sentModern;private volatile boolean resetLive;
    private final Set<Runnable> listeners=new CopyOnWriteArraySet<>();
    private final Map<String,Long> liveRetry=new ConcurrentHashMap<>();private final Map<String,Integer> failures=new HashMap<>();
    private volatile Http.Cancel active;private volatile long lastFlush;private long lastManual=-30_000;private volatile int generation,configurationRevision;
    public volatile int pendingCount;
    private final Runnable retry=()->request(false);
    public ScrobbleRepository(SelfoniumApp app){this.app=app;prefs=app.getSharedPreferences("scrobbling",0);app.io.execute(()->{if(!enabled())app.library.clearScrobbles();else app.library.pruneScrobbles(System.currentTimeMillis());refreshCount();request(true);});}
    public int configurationRevision(){return configurationRevision;}
    public boolean enabled(){return prefs.getBoolean("enabled",true);}
    public void enabled(boolean value){
        generation++;configurationRevision++;prefs.edit().putBoolean("enabled",value).apply();latest.set(null);Http.Cancel call=active;if(call!=null)call.cancel();app.main.removeCallbacks(retry);
        if(!value)app.io.execute(()->{app.library.clearScrobbles();refreshCount();});else request(true);emit();
    }
    public void listen(Runnable listener){listeners.add(listener);}
    public void unlisten(Runnable listener){listeners.remove(listener);}
    private void emit(){app.main.post(()->{for(Runnable listener:listeners)listener.run();});}
    private void refreshCount(){pendingCount=app.library.scrobbleCount();emit();}
    public void save(ListeningSession session,Source source,String remote,long duration){
        ListeningSession.State state=session.state();int revision=generation;boolean newSubmission=session.eligible(duration);String account=ListeningSession.account(source);
        app.io.execute(()->{if(!enabled()||revision!=generation)return;
            try{if(source(source.id,account)==null){app.main.post(session::submitted);return;}boolean submitted=app.library.saveListening(state,source.id,remote,account,duration,System.currentTimeMillis());
                if(submitted)app.main.post(session::submitted);if(newSubmission){refreshCount();request(true);}
            }catch(Exception ignored){/* Keep the in-memory session eligible and retry at the next checkpoint. */}
        });
    }
    public void report(Report report){if(!enabled())return;latest.set(report);request(true);}
    public void retryNow(){long now=SystemClock.elapsedRealtime();if(!enabled()||now-lastManual<30_000)return;lastManual=now;liveRetry.clear();app.io.execute(()->{app.library.resetScrobbleRetries();request(true);});}
    public void networkAvailable(){resetLive=true;liveRetry.clear();lastFlush=0;request(true);}
    public void request(boolean force){
        if(!enabled())return;long now=SystemClock.elapsedRealtime();if(!force&&now-lastFlush<30_000)return;
        if(!queued.compareAndSet(false,true))return;lastFlush=now;
        int revision=generation;worker.execute(()->{Report processed=null;
            try{for(int i=0;i<3;i++){Report report=latest.get();if(report==null||report==processed)break;processed=report;try{sendLive(report,revision);}catch(Exception ignored){}}flush(revision);}
            catch(Exception ignored){/* Playback and local history never depend on server reachability. */}
            finally{active=null;queued.set(false);refreshCount();app.main.removeCallbacks(retry);if(enabled()&&pendingCount>0)app.main.postDelayed(retry,30_000);Report current=latest.get();if(current!=null&&current!=processed)app.main.post(()->request(true));}
        });
    }
    private boolean allowed(int revision){return enabled()&&revision==generation;}
    private Source source(String id,String account){Source source=app.sources.get(id);return source!=null&&source.type==Source.Type.SUBSONIC&&ListeningSession.account(source).equals(account)?source:null;}
    private boolean unsupported(Exception error){return error instanceof SubsonicClient.ApiException api&&(api.code==20||api.code==30||api.code==70)||error instanceof Http.StatusException http&&(http.status==404||http.status==405||http.status==501);}
    private void sendLive(Report report,int revision)throws Exception{
        long now=SystemClock.elapsedRealtime();if(!allowed(revision)||now-report.elapsed()>90_000||now<report.elapsed()||now<liveRetry.getOrDefault(report.source(),0L))return;
        if(resetLive){sent=null;sentModern=false;resetLive=false;}
        Source source=source(report.source(),report.account());if(source==null)return;Http.Cancel call=new Http.Cancel();active=call;
        try{
            app.subsonic.discoverPlayback(source,call);if(!allowed(revision))return;
            if(app.subsonic.supports(source,"playbackReport",1)){
                try{
                    boolean fresh=!sentModern||sent==null||!sent.session().equals(report.session())||!sent.account().equals(report.account());
                    if(fresh){if(sent!=null&&!sent.state().equals("stopped")){Source old=source(sent.source(),sent.account());if(old!=null&&app.subsonic.supports(old,"playbackReport",1))try{app.subsonic.reportPlayback(old,sent.remote(),"stopped",sent.position(),sent.rate(),call);}catch(Exception ignored){/* An unreachable previous source must not block the current one. */}}
                        if(!report.state().equals("stopped")){app.subsonic.reportPlayback(source,report.remote(),"starting",report.position(),report.rate(),call);sent=new Report(report.session(),report.source(),report.remote(),report.account(),report.started(),"starting",report.position(),report.rate(),report.elapsed());sentModern=true;}
                    }
                    if(!allowed(revision)||latest.get()!=report)return;app.subsonic.reportPlayback(source,report.remote(),report.state(),report.position(),report.rate(),call);
                }catch(Exception error){if(!unsupported(error))throw error;app.subsonic.disablePlaybackReport(source);if(allowed(revision)&&latest.get()==report&&report.state().equals("playing"))app.subsonic.scrobble(source,report.remote(),System.currentTimeMillis(),false,call);}
            }else if(allowed(revision)&&latest.get()==report&&report.state().equals("playing"))app.subsonic.scrobble(source,report.remote(),System.currentTimeMillis(),false,call);
            sent=report;sentModern=app.subsonic.supports(source,"playbackReport",1);liveRetry.remove(source.id);failures.remove(source.id);
        }catch(Exception error){int count=failures.merge(source.id,1,Integer::sum);liveRetry.put(source.id,now+ListeningSession.retryDelay(count-1));throw error;}
        finally{active=null;}
    }
    private void flush(int revision){
        if(!allowed(revision))return;long now=System.currentTimeMillis();app.library.pruneScrobbles(now);Set<String> failed=new HashSet<>();
        for(Library.Scrobble event:app.library.dueScrobbles(now)){
            if(!allowed(revision))return;if(failed.contains(event.source())){app.library.retryScrobble(event,now+ListeningSession.retryDelay(event.attempts()));continue;}
            long remaining=liveRetry.getOrDefault(event.source(),0L)-SystemClock.elapsedRealtime();if(remaining>0){app.library.retryScrobble(event,now+remaining);continue;}
            Http.Cancel call=new Http.Cancel();active=call;
            try{
                Source source=source(event.source(),event.account());
                if(source==null){app.library.acknowledgeScrobble(event.session());continue;}
                if(!allowed(revision))return;app.subsonic.scrobble(source,event.remote(),event.started(),true,call);app.library.acknowledgeScrobble(event.session());
            }catch(Exception error){
                if(!allowed(revision))return;failed.add(event.source());long delay=ListeningSession.retryDelay(event.attempts());
                if(error instanceof SubsonicClient.ApiException api&&api.code==70){app.library.acknowledgeScrobble(event.session());continue;}
                if(error instanceof SubsonicClient.ApiException api&&(api.code==40||api.code==50)||error instanceof Http.StatusException http&&(http.status==401||http.status==403))delay=6*60*60_000L;
                liveRetry.put(event.source(),SystemClock.elapsedRealtime()+delay);app.library.retryScrobble(event,now+delay);
            }finally{active=null;}
        }
    }
    public void clear(Consumer<Integer> done){int before=pendingCount;generation++;Http.Cancel call=active;if(call!=null)call.cancel();app.io.execute(()->{app.library.clearScrobbleQueue();refreshCount();app.main.post(()->done.accept(before));});}
}
