package app.selfonium.data;

import android.content.*;
import android.net.*;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.*;
import app.selfonium.net.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Durable user-requested queue; only the existing foreground DownloadService transfers files. */
public final class DownloadRepository {
    public enum Status{QUEUED,RUNNING,WAITING,FAILED,CANCELLED,COMPLETE}
    public enum Error{NONE,NETWORK,SPACE,INCOMPLETE,SOURCE,INTERRUPTED}
    public record Entry(String id,Status status,long bytes,long total,Error error){}
    private final SelfoniumApp app;private final SharedPreferences prefs;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();private final Set<Runnable> listeners=new HashSet<>();
    private volatile Http.Cancel activeCancel;private String activeId="";private boolean foreground,serviceRequested;private long emitted;
    public DownloadRepository(SelfoniumApp app){this.app=app;prefs=app.getSharedPreferences("download_queue",0);
        try{JSONArray array=new JSONArray(prefs.getString("entries","[]"));for(int i=0;i<array.length();i++){JSONObject value=array.getJSONObject(i);Status state=Status.valueOf(value.getString("status"));Error error=Error.valueOf(value.optString("error","NONE"));String id=value.getString("id");if(state==Status.RUNNING){state=app.offline.available(id)?Status.COMPLETE:Status.FAILED;error=state==Status.COMPLETE?Error.NONE:Error.INTERRUPTED;}entries.put(id,new Entry(id,state,0,0,error));}}catch(Exception ignored){/* Corrupt queue metadata must not remove downloaded files. */}
    }
    public synchronized List<Entry> entries(){return new ArrayList<>(entries.values());}
    public synchronized Set<String> activeIds(){return activeId.isEmpty()?Set.of():Set.of(activeId);}
    public synchronized boolean busy(){return !activeId.isEmpty();}
    public boolean wifiOnly(){return app.getSharedPreferences("storage",0).getBoolean("wifiOnly",false);}
    public void wifiOnly(boolean value){app.getSharedPreferences("storage",0).edit().putBoolean("wifiOnly",value).apply();networkChanged();}
    public boolean allowed(){
        ConnectivityManager cm=app.getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps!=null&&(!wifiOnly()||caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }
    private boolean remote(String id){Track track=app.library.get(id);return track!=null&&!List.of("device","files").contains(track.sourceId);}
    private boolean permitted(String id){return !remote(id)||allowed();}
    public synchronized void listen(Runnable listener){listeners.add(listener);}
    public synchronized void unlisten(Runnable listener){listeners.remove(listener);}
    private void changed(boolean persist){
        if(persist){JSONArray array=new JSONArray();for(Entry entry:entries.values())try{array.put(new JSONObject().put("id",entry.id).put("status",entry.status.name()).put("error",entry.error.name()));}catch(JSONException ignored){}prefs.edit().putString("entries",array.toString()).apply();}
        app.main.post(()->{List<Runnable> targets;synchronized(this){targets=new ArrayList<>(listeners);}for(Runnable target:targets)target.run();});
    }
    public synchronized void enqueue(Collection<String> ids){
        for(String id:ids){if(app.library.get(id)==null||"radio".equals(app.library.get(id).sourceId)||app.offline.available(id))continue;Entry existing=entries.get(id);if(existing!=null&&existing.status==Status.RUNNING)continue;entries.put(id,new Entry(id,Status.QUEUED,0,0,Error.NONE));}
        trimHistory();changed(true);kick();
    }
    private void trimHistory(){int history=0;List<String> remove=new ArrayList<>();List<Entry> reverse=new ArrayList<>(entries.values());Collections.reverse(reverse);for(Entry entry:reverse)if((entry.status==Status.COMPLETE||entry.status==Status.CANCELLED)&&!entry.id.equals(activeId)){if(++history>30)remove.add(entry.id);}for(String id:remove)entries.remove(id);}
    public synchronized void cancel(String id){
        Entry entry=entries.get(id);if(entry==null)return;entries.put(id,new Entry(id,Status.CANCELLED,entry.bytes,entry.total,Error.NONE));if(id.equals(activeId)&&activeCancel!=null)activeCancel.cancel();changed(true);
    }
    public synchronized void cancelAll(){for(Entry entry:new ArrayList<>(entries.values()))if(entry.status==Status.QUEUED||entry.status==Status.WAITING||entry.status==Status.RUNNING)entries.put(entry.id,new Entry(entry.id,Status.CANCELLED,entry.bytes,entry.total,Error.NONE));if(activeCancel!=null)activeCancel.cancel();trimHistory();changed(true);}
    public synchronized void forgetHistory(){entries.entrySet().removeIf(e->!e.getKey().equals(activeId)&&(e.getValue().status==Status.CANCELLED||e.getValue().status==Status.COMPLETE));changed(true);}
    public synchronized void foreground(boolean value){foreground=value;if(value)kick();}
    public synchronized void networkChanged(){if(!activeId.isEmpty()&&!permitted(activeId)&&activeCancel!=null)activeCancel.cancel();{for(Entry entry:new ArrayList<>(entries.values()))if(entry.status==Status.WAITING&&permitted(entry.id))entries.put(entry.id,new Entry(entry.id,Status.QUEUED,0,0,Error.NONE));}changed(true);kick();}
    private void kick(){
        if(!foreground||serviceRequested||busy())return;
        boolean pending=entries.values().stream().anyMatch(e->e.status==Status.QUEUED||e.status==Status.WAITING);if(!pending)return;
        if(entries.values().stream().noneMatch(e->(e.status==Status.QUEUED||e.status==Status.WAITING)&&permitted(e.id))){for(Entry entry:new ArrayList<>(entries.values()))if(entry.status==Status.QUEUED)entries.put(entry.id,new Entry(entry.id,Status.WAITING,0,0,Error.NONE));changed(true);return;}
        try{serviceRequested=true;app.startForegroundService(new Intent(app,DownloadService.class));}catch(RuntimeException error){serviceRequested=false;}
    }
    public synchronized void serviceStopped(){serviceRequested=false;kick();}
    public synchronized void interrupt(){if(activeCancel!=null)activeCancel.cancel();}
    /** Runs on the service worker, one track at a time. */
    public boolean runNext(){
        String id;Http.Cancel cancel=new Http.Cancel();
        synchronized(this){
            if(busy())return false;
            Entry next=entries.values().stream().filter(e->(e.status==Status.QUEUED||e.status==Status.WAITING)&&permitted(e.id)).findFirst().orElse(null);if(next==null){for(Entry entry:new ArrayList<>(entries.values()))if(entry.status==Status.QUEUED)entries.put(entry.id,new Entry(entry.id,Status.WAITING,0,0,Error.NONE));changed(true);return false;}
            id=next.id;activeId=id;activeCancel=cancel;entries.put(id,new Entry(id,Status.RUNNING,0,0,Error.NONE));changed(true);
        }
        Status status=Status.COMPLETE;Error error=Error.NONE;
        try{transfer(id,cancel);}catch(Exception failure){
            status=Status.FAILED;error=Error.NETWORK;
            synchronized(this){Entry entry=entries.get(id);if(entry!=null&&entry.status==Status.CANCELLED){status=Status.CANCELLED;error=Error.NONE;}else if(!permitted(id)){status=Status.WAITING;error=Error.NONE;}else if(failure instanceof DownloadCopy.Failure f){error=switch(f.problem){case SPACE->Error.SPACE;case CANCELLED->Error.INTERRUPTED;default->Error.INCOMPLETE;};}else if(failure instanceof IllegalArgumentException||failure instanceof SubsonicClient.ApiException||failure instanceof Http.StatusException){error=Error.SOURCE;}}
        }finally{app.offline.files.delete(app.offline.partial(id));}
        synchronized(this){Entry previous=entries.get(id);long done=previous==null?0:previous.bytes,total=previous==null?0:previous.total;entries.put(id,new Entry(id,status,done,total,error));activeId="";activeCancel=null;trimHistory();changed(true);if(!serviceRequested)app.main.post(()->{synchronized(this){kick();}});}
        return true;
    }
    private void transfer(String id,Http.Cancel cancel)throws Exception{
        Track track=app.library.get(id);if(track==null)throw new IllegalArgumentException();if(app.offline.available(id))return;
        File directory=app.offline.directory;if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create download directory");
        File partial=app.offline.partial(id);SubsonicClient.Stream stream=app.subsonic.stream(track);
        DownloadCopy.Progress progress=(done,total)->{long now=System.currentTimeMillis();synchronized(this){Entry entry=entries.get(id);if(entry!=null&&entry.status==Status.RUNNING){entries.put(id,new Entry(id,Status.RUNNING,done,total,Error.NONE));if(now-emitted>300){emitted=now;changed(false);}}}};
        DownloadCopy.Result result;
        if(stream.url().startsWith("content:")||stream.url().startsWith("file:")){
            try(InputStream in=app.getContentResolver().openInputStream(Uri.parse(stream.url()));FileOutputStream out=new FileOutputStream(partial)){if(in==null)throw new IOException("File unavailable");result=DownloadCopy.copy(in,out,-1,cancel::cancelled,directory::getUsableSpace,progress);out.getFD().sync();}
        }else try(Http.Response response=Http.open("GET",stream.url(),stream.headers(),null,cancel);InputStream in=response.input();FileOutputStream out=new FileOutputStream(partial)){result=DownloadCopy.copy(in,out,response.length(),cancel::cancelled,directory::getUsableSpace,progress);out.getFD().sync();}
        if(cancel.cancelled())throw new DownloadCopy.Failure(DownloadCopy.Problem.CANCELLED);app.offline.publish(id,result);
    }
}
