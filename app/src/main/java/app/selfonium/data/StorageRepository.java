package app.selfonium.data;

import app.selfonium.SelfoniumApp;
import app.selfonium.core.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Storage work has its own worker and never occupies catalog, playback or the UI thread. */
public final class StorageRepository {
    public record Snapshot(long offline,int tracks,long audio,long artwork,long temporary,long library,long other,long free,long total){
        public long used(){return offline+audio+artwork+temporary+library+other;}
    }
    public record Removal(long bytes,int removed,int retained){}
    public record CacheClear(long bytes,boolean deferred){}
    private final SelfoniumApp app;private final ExecutorService worker=Executors.newSingleThreadExecutor();
    public StorageRepository(SelfoniumApp app){this.app=app;}
    public void inspect(Consumer<Snapshot> done){worker.execute(()->{
        File cache=app.getCacheDir(),files=app.getFilesDir();long offline=0,audio=ManagedFiles.bytes(new File(cache,"audio-v2")),art=ManagedFiles.bytes(new File(cache,"artwork-v1")),temporary=ManagedFiles.bytes(new File(cache,"updates"));
        for(Track track:app.library.offlineTracks()){File file=new File(app.library.offline(track.id));if(app.offline.files.owns(file)&&file.isFile())offline+=file.length();}
        temporary+=Math.max(0,ManagedFiles.bytes(app.offline.directory)-offline);
        File data=files.getParentFile();long database=ManagedFiles.bytes(new File(data,"databases"));long all=ManagedFiles.bytes(files)+ManagedFiles.bytes(cache)+ManagedFiles.bytes(app.getCodeCacheDir())+ManagedFiles.bytes(new File(data,"shared_prefs"))+database+ManagedFiles.bytes(new File(data,"no_backup"));
        int count=0;for(Track track:app.library.offlineTracks())if(app.offline.available(track.id))count++;
        Snapshot snapshot=new Snapshot(offline,count,audio,art,temporary,database,Math.max(0,all-offline-audio-art-temporary-database),files.getUsableSpace(),files.getTotalSpace());app.main.post(()->done.accept(snapshot));
    });}
    public void clearAudio(Consumer<CacheClear> done){worker.execute(()->{File folder=new File(app.getCacheDir(),"audio-v2");long before=ManagedFiles.bytes(folder);boolean cleared=app.audio.clear();CacheClear result=new CacheClear(Math.max(0,before-ManagedFiles.bytes(folder)),!cleared);app.main.post(()->done.accept(result));});}
    public void configureAudio(long bytes,boolean prefetch,Runnable done){worker.execute(()->{app.audio.configure(bytes,prefetch);app.main.post(done);});}
    public void clearArtwork(Consumer<Long> done){worker.execute(()->{long bytes=app.artwork.clearCache();app.main.post(()->done.accept(bytes));});}
    public void removeAll(Consumer<Removal> done){worker.execute(()->{
        long bytes=0;int count=0,retained=0;Set<String> active=app.downloads.activeIds();
        for(Track track:app.library.offlineTracks()){File file=new File(app.library.offline(track.id));long size=file.length();if(!active.contains(track.id)&&app.offline.remove(track.id)){bytes+=size;count++;}else retained++;}
        Removal result=new Removal(bytes,count,retained);app.main.post(()->done.accept(result));
    });}
    public void removeOne(String id,Consumer<Boolean> done){worker.execute(()->{boolean removed=app.offline.remove(id);app.main.post(()->done.accept(removed));});}
    public void verify(Consumer<OfflineStore.Verification> done,Consumer<Exception> error){worker.execute(()->{try{OfflineStore.Verification result=app.offline.verify();app.main.post(()->done.accept(result));}catch(Exception failure){app.main.post(()->error.accept(failure));}});}
    public void cleanup(Consumer<Long> done){worker.execute(()->{long bytes; synchronized(app.downloads){bytes=app.offline.cleanup(app.downloads.activeIds());}long removed=bytes;app.main.post(()->app.updates.cleanupStale(updateBytes->done.accept(removed+updateBytes)));});}
}
