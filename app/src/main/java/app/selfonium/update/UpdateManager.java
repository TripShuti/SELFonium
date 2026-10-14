package app.selfonium.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static app.selfonium.update.UpdatePolicy.*;

/** Application-owned foreground updater; never uses the catalog/playback queues or a service. */
public final class UpdateManager {
    public enum Stage {LOADING,IDLE,CHECKING,AVAILABLE,DOWNLOADING,VERIFYING,READY}
    public record State(Stage stage,ReleaseInfo release,long downloaded,long total,long lastSuccess,boolean automatic,Failure error){}
    private final Context context;private final SharedPreferences prefs;private final File directory,verified;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Consumer<State>> listeners=new HashSet<>();private final UpdateHttp http=new UpdateHttp();
    private volatile State state;private UpdateHttp.Cancel cancel;private boolean foreground;
    public final String installedVersion;public final long installedCode;
    public UpdateManager(Context context){
        this.context=context.getApplicationContext();prefs=context.getSharedPreferences("updates",0);directory=new File(context.getCacheDir(),"updates");verified=new File(directory,"verified.apk");
        try{var installed=ApkVerifier.installed(context);installedVersion=installed.versionName;installedCode=ApkVerifier.code(installed);}catch(Exception e){throw new IllegalStateException("Installed package unavailable",e);}
        state=new State(Stage.LOADING,null,0,0,prefs.getLong("last_success",0),prefs.getBoolean("automatic",true),null);
        worker.execute(()->{
            new File(directory,"download.part").delete();new File(directory,"candidate.apk").delete();ReleaseInfo saved=null;boolean ready=false;
            try{
                saved=ReleaseInfo.restore(prefs.getString("release",""),prefs.getString("manifest",""));if(!newer(saved.code,installedCode))saved=null;
                if(saved!=null&&freshFile()){ApkVerifier.verify(this.context,verified,saved,new UpdateHttp.Cancel());ready=true;}
            }catch(Exception ignored){verified.delete();}
            if(saved==null||!ready)verified.delete();
            ReleaseInfo result=saved;boolean valid=ready;
            main.post(()->{set(valid?Stage.READY:result==null?Stage.IDLE:Stage.AVAILABLE,result,0,null);if(foreground)check(false);});
        });
    }
    public State state(){return state;}
    public void listen(Consumer<State> listener){listeners.add(listener);listener.accept(state);}
    public void unlisten(Consumer<State> listener){listeners.remove(listener);}
    private boolean freshFile(){long age=System.currentTimeMillis()-verified.lastModified();return verified.isFile()&&age>=0&&age<DAY;}
    public void foreground(boolean value){
        foreground=value;if(!value)return;
        if(!busy()&&state.stage==Stage.READY&&!freshFile()){
            ReleaseInfo release=state.release;set(Stage.LOADING,release,0,null);
            worker.execute(()->{verified.delete();main.post(()->{set(Stage.AVAILABLE,release,0,null);if(foreground)check(false);});});
        }else check(false);
    }
    public void automatic(boolean value){prefs.edit().putBoolean("automatic",value).apply();state=new State(state.stage,state.release,state.downloaded,state.total,state.lastSuccess,value,state.error);emit();if(value&&foreground)check(false);}
    private boolean busy(){return state.stage==Stage.LOADING||state.stage==Stage.CHECKING||state.stage==Stage.DOWNLOADING||state.stage==Stage.VERIFYING;}
    private void emit(){for(var listener:new ArrayList<>(listeners))listener.accept(state);}
    private void set(Stage stage,ReleaseInfo release,long bytes,Failure error){state=new State(stage,release,bytes,release==null?0:release.size,prefs.getLong("last_success",0),prefs.getBoolean("automatic",true),error);emit();}
    private void fail(Failure error){set(state.release==null?Stage.IDLE:verified.isFile()?Stage.READY:Stage.AVAILABLE,state.release,state.downloaded,error);}
    public ReleaseInfo announcement(){
        ReleaseInfo release=state.release;if(!foreground||release==null||busy()||release.code==prefs.getLong("notified",0)||release.code==prefs.getLong("skipped",0))return null;
        prefs.edit().putLong("notified",release.code).apply();return release;
    }
    public void skip(){if(state.release!=null)prefs.edit().putLong("skipped",state.release.code).putLong("notified",state.release.code).apply();}
    public void check(boolean manual){
        if(busy()||!manual&&!state.automatic)return;
        long now=System.currentTimeMillis();if(!due(now,prefs.getLong("attempted",0),prefs.getLong("retry_at",0),!manual)){
            if(manual)fail(new Failure(Reason.THROTTLED,0,Math.max(prefs.getLong("retry_at",0),prefs.getLong("attempted",0)+MANUAL_INTERVAL)));return;
        }
        prefs.edit().putLong("attempted",now).apply();cancel=new UpdateHttp.Cancel();UpdateHttp.Cancel operation=cancel;ReleaseInfo previous=state.release;
        set(Stage.CHECKING,previous,0,null);
        worker.execute(()->{
            try{
                ReleaseInfo found=http.latest(operation);if(found!=null&&!newer(found.code,installedCode))found=null;
                boolean keep=found!=null&&previous!=null&&found.code==previous.code&&found.version.equals(previous.version)&&found.signer.equals(previous.signer)&&found.size==previous.size&&found.sha256.equals(previous.sha256)&&freshFile();
                if(!keep)verified.delete();ReleaseInfo result=found;long checked=System.currentTimeMillis();
                main.post(()->{
                    prefs.edit().putLong("last_success",checked).putLong("retry_at",0).putLong("cached_at",checked).putString("release",result==null?"":result.releaseJson).putString("manifest",result==null?"":result.manifestJson).apply();
                    set(keep?Stage.READY:result==null?Stage.IDLE:Stage.AVAILABLE,result,0,null);
                });
            }catch(IOException e){Failure error=error(e,operation);main.post(()->{if(error.retryAt>0)prefs.edit().putLong("retry_at",error.retryAt).apply();fail(error);});}
        });
    }
    private static Failure error(IOException error,UpdateHttp.Cancel cancel){try{cancel.check();}catch(Failure e){return e;}return error instanceof Failure?(Failure)error:new Failure(Reason.NETWORK);}
    public void download(){
        if(busy()||state.release==null)return;ReleaseInfo release=state.release;cancel=new UpdateHttp.Cancel();UpdateHttp.Cancel operation=cancel;
        set(Stage.DOWNLOADING,release,0,null);
        worker.execute(()->{
            verified.delete();File candidate=null;
            try{
                candidate=UpdateDownload.fetch(http,release,directory,operation,(done,total)->main.post(()->{if(cancel==operation&&state.stage==Stage.DOWNLOADING)set(Stage.DOWNLOADING,release,done,null);}),directory::getUsableSpace);
                main.post(()->set(Stage.VERIFYING,release,release.size,null));ApkVerifier.verify(context,candidate,release,operation);
                Files.move(candidate.toPath(),verified.toPath(),StandardCopyOption.REPLACE_EXISTING);operation.check();
                main.post(()->set(Stage.READY,release,release.size,null));
            }catch(IOException e){if(candidate!=null)candidate.delete();verified.delete();Failure error=error(e,operation);main.post(()->fail(error));}
        });
    }
    public void cancel(){if(state.stage==Stage.DOWNLOADING&&cancel!=null)cancel.cancel();}
    public void prepareInstall(Consumer<File> ready){
        if(busy()||state.release==null)return;ReleaseInfo release=state.release;cancel=new UpdateHttp.Cancel();UpdateHttp.Cancel operation=cancel;
        set(Stage.VERIFYING,release,release.size,null);
        worker.execute(()->{try{ApkVerifier.verify(context,verified,release,operation);main.post(()->{set(Stage.READY,release,release.size,null);ready.accept(verified);});}catch(IOException e){verified.delete();Failure error=error(e,operation);main.post(()->fail(error));}});
    }
}
