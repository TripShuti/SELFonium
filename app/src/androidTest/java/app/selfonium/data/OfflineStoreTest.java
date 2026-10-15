package app.selfonium.data;

import android.content.*;
import android.test.*;
import app.selfonium.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Isolated files, preferences and database; never touches the user's library. */
@SuppressWarnings("deprecation")
public final class OfflineStoreTest extends InstrumentationTestCase {
    private Context context;private File directory;private Library library;private OfflineStore offline;private String prefix;
    @Override protected void setUp()throws Exception{
        super.setUp();Context target=getInstrumentation().getTargetContext();prefix="storage_test_"+UUID.randomUUID()+"_";directory=new File(target.getCacheDir(),prefix);assertTrue(directory.mkdirs());
        context=new RenamingDelegatingContext(target,prefix){
            @Override public File getFilesDir(){return directory;}
            @Override public Context createConfigurationContext(android.content.res.Configuration config){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences(prefix+name,mode);}
        };
        library=new Library(context);offline=new OfflineStore(context,library);assertTrue(offline.directory.mkdirs());
    }
    @Override protected void tearDown()throws Exception{
        library.close();context.deleteDatabase("selfonium.db");context.getSharedPreferences("offline_integrity",0).edit().clear().commit();
        try(var paths=Files.walk(directory.toPath())){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}super.tearDown();
    }
    private Track add(String id){Track track=new Track(id,"fixture",id,id,"Artist","Album","",1000,1);library.merge("fixture",List.of(track),false);return track;}
    private void download(String id)throws Exception{File partial=offline.partial(id);Files.write(partial.toPath(),new byte[]{1,2,3});offline.publish(id,new DownloadCopy.Result(3,DownloadCopy.digest(partial)));}
    public void testLeasedRemovalKeepsLibraryFavoritesAndPlaylists()throws Exception{
        Track track=add("one");download(track.id);library.toggleFavorite(track.id);long playlist=library.createPlaylist("User playlist");library.addToPlaylist(playlist,track.id);
        OfflineStore.Read read=offline.open(track.id);assertNotNull(read);assertFalse(offline.remove(track.id));assertTrue(read.file().exists());read.lease().close();assertTrue(offline.remove(track.id));
        assertEquals("",library.offline(track.id));assertNotNull(library.get(track.id));assertTrue(library.favorite(track.id));assertEquals(1,library.playlist(playlist).size());
    }
    public void testMissingAndDamagedCopiesCanBeDownloadedAgain()throws Exception{
        add("one");add("two");download("one");download("two");assertTrue(offline.verify().damaged().isEmpty());
        Files.write(offline.target("one").toPath(),new byte[]{3,2,1});assertTrue(offline.target("two").delete());
        assertEquals(2,offline.verify().damaged().size());assertEquals("",library.offline("one"));assertNotNull(library.get("one"));download("one");assertTrue(offline.available("one"));assertTrue(offline.verify().damaged().isEmpty());
    }
    public void testDamagedLeasedFileReportedAsRetained()throws Exception{
        add("leased");download("leased");Files.write(offline.target("leased").toPath(),new byte[]{3,2,1});OfflineStore.Read read=offline.open("leased");
        try{OfflineStore.Verification result=offline.verify();assertEquals(1,result.retained());assertTrue(result.damaged().isEmpty());assertTrue(offline.available("leased"));}finally{read.lease().close();}
        assertEquals(1,offline.verify().damaged().size());
    }
    public void testCleanupRetainsLiveDownloadsAndOriginalFiles()throws Exception{
        add("one");download("one");File orphan=offline.target("orphan"),active=offline.partial("active");Files.write(orphan.toPath(),new byte[]{1});Files.write(active.toPath(),new byte[]{2});
        File original=new File(directory,"original.audio");Files.write(original.toPath(),new byte[]{3});
        assertEquals(1,offline.cleanup(Set.of("active")));assertTrue(active.exists());assertTrue(original.exists());assertTrue(offline.target("one").exists());assertFalse(orphan.exists());
    }
    public void testQueueDeduplicationCancellationAndRestart()throws Exception{
        app.selfonium.SelfoniumApp app=(app.selfonium.SelfoniumApp)android.app.Instrumentation.newApplication(app.selfonium.SelfoniumApp.class,context);
        app.library=library;app.offline=offline;app.subsonic=new app.selfonium.net.SubsonicClient(null);
        File original=new File(directory,"source.audio");Files.write(original.toPath(),new byte[]{1,2,3,4});
        Track local=new Track("files:one","files","one","One","Artist","Album",original.toURI().toString(),1000,1);library.merge("files",List.of(local),false);
        DownloadRepository queue=new DownloadRepository(app);queue.enqueue(List.of(local.id,local.id));assertEquals(1,queue.entries().size());queue.cancel(local.id);assertEquals(DownloadRepository.Status.CANCELLED,queue.entries().get(0).status());
        queue.enqueue(List.of(local.id));assertTrue(queue.runNext());assertEquals(DownloadRepository.Status.COMPLETE,queue.entries().get(0).status());assertTrue(offline.available(local.id));assertTrue(original.exists());
        queue.enqueue(List.of(local.id));assertEquals(1,queue.entries().size());assertFalse(queue.runNext());
        context.getSharedPreferences("download_queue",0).edit().putString("entries","[{\"id\":\"files:one\",\"status\":\"RUNNING\",\"error\":\"NONE\"}]").commit();
        DownloadRepository restored=new DownloadRepository(app);assertEquals(DownloadRepository.Status.COMPLETE,restored.entries().get(0).status());
        offline.remove(local.id);DownloadRepository interrupted=new DownloadRepository(app);assertEquals(DownloadRepository.Status.FAILED,interrupted.entries().get(0).status());assertEquals(DownloadRepository.Error.INTERRUPTED,interrupted.entries().get(0).error());
        context.getSharedPreferences("download_queue",0).edit().clear().commit();
    }
    public void testPlaylistSourceGroupingKeepsMixedListsIntact(){
        Track local=new Track("device:one","device","one","Local","Artist","Album","",1000,1);library.merge("device",List.of(local),false);Track remote=add("server:one");
        long empty=library.createPlaylist("Empty"),mixed=library.createPlaylist("Mixed");library.addToPlaylist(mixed,local.id);library.addToPlaylist(mixed,remote.id);
        for(Library.Playlist playlist:library.playlistInfo()){
            if(playlist.id()==empty){assertTrue(library.playlistMatchesSource(playlist,LibraryFilter.LOCAL));assertFalse(library.playlistMatchesSource(playlist,"fixture"));}
            if(playlist.id()==mixed){assertTrue(library.playlistMatchesSource(playlist,LibraryFilter.LOCAL));assertTrue(library.playlistMatchesSource(playlist,"fixture"));assertFalse(library.playlistMatchesSource(playlist,"other"));}
        }
        assertEquals(2,library.playlist(mixed).size());
    }
    public void testScrobbleSubmissionMarkerSurvivesAcknowledgement(){
        Track track=add("server:one");long now=System.currentTimeMillis();ListeningSession.State state=new ListeningSession.State("first",track.id,now,90_000,false);
        assertTrue(library.saveListening(state,"fixture",track.remoteId,"identity",180_000,now));assertEquals(1,library.scrobbleCount());
        library.acknowledgeScrobble("first");assertEquals(0,library.scrobbleCount());assertTrue(library.saveListening(state,"fixture",track.remoteId,"identity",180_000,now));assertEquals(0,library.scrobbleCount());assertTrue(library.listeningCheckpoint().state().submitted());
        ListeningSession.State repeat=new ListeningSession.State("repeat",track.id,now+1,90_000,false);assertTrue(library.saveListening(repeat,"fixture",track.remoteId,"identity",180_000,now));assertEquals(1,library.scrobbleCount());
        library.retryScrobble(library.dueScrobbles(now).get(0),now+60000);assertTrue(library.dueScrobbles(now).isEmpty());
        library.clearScrobbleQueue();assertTrue(library.saveListening(repeat,"fixture",track.remoteId,"identity",180_000,now));assertEquals(0,library.scrobbleCount());
        library.removeSource("fixture");assertNull(library.listeningCheckpoint());
    }
    public void testLegacyDownloadCheckPreservesValidFile()throws Exception{
        add("legacy");File file=offline.target("legacy");Files.write(file.toPath(),new byte[]{1});library.offline("legacy",file.getAbsolutePath());assertTrue(offline.verify().damaged().isEmpty());assertTrue(file.exists());
    }
}
