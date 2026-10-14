package app.selfonium.data;

import app.selfonium.locale.Texts;
import app.selfonium.R;

import app.selfonium.core.Source;
import app.selfonium.core.Track;
import app.selfonium.core.ServerPlaylist;
import app.selfonium.SelfoniumApp;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Serialized server operations; durable, coalesced favorites survive failed requests/restarts. */
public final class SocialSync {
    private final SelfoniumApp app;
    private final AtomicBoolean flushQueued=new AtomicBoolean(),refreshQueued=new AtomicBoolean();
    private volatile long lastRefresh;
    private final List<Consumer<String>> listeners=new CopyOnWriteArrayList<>();
    private final Runnable retry=this::requestFlush;
    private boolean failureReported;
    public volatile int pendingCount;
    public SocialSync(SelfoniumApp app){this.app=app;}
    public void listen(Consumer<String> listener){listeners.add(listener);}
    public void unlisten(Consumer<String> listener){listeners.remove(listener);}
    private void changed(String status){app.main.post(()->{for(Consumer<String> listener:listeners)listener.accept(status);});}
    public void toggleFavorite(Track track){
        Library.Preview preview=app.library.previewFavorite(track.id,!app.library.favorite(track.id));
        app.io.execute(()->{
            try{Source source=java.util.List.of("device","files","radio").contains(track.sourceId)?null:app.sources.get(track.sourceId);app.library.saveFavorite(track.id,preview,source!=null&&source.type==Source.Type.SUBSONIC);pendingCount=app.library.pendingFavorites().size();changed("");requestFlush();}
            catch(Exception error){app.library.discardFavorite(track.id,preview);changed(Texts.get(app,R.string.ui_could_not_save_favorites_on_this_phone));}
        });
    }
    public void requestFlush(){
        if(!flushQueued.compareAndSet(false,true))return;app.main.removeCallbacks(retry);
        app.sync.execute(()->{
            try{
                for(int pass=0;pass<3&&!app.library.pendingFavorites().isEmpty();pass++)flushFavorites(null);
                failureReported=false;changed("");
            }catch(Exception error){if(!failureReported){failureReported=true;changed(Texts.get(app,R.string.ui_favorite_changes_are_saved_on_this_phone));}}
            finally{pendingCount=app.library.pendingFavorites().size();flushQueued.set(false);if(pendingCount>0)app.main.postDelayed(retry,30_000);}
        });
    }
    public void networkAvailable(){lastRefresh=0;requestFlush();refreshAll();}
    public void refreshAll(){
        long now=android.os.SystemClock.elapsedRealtime();if((lastRefresh!=0&&now-lastRefresh<60_000)||!refreshQueued.compareAndSet(false,true))return;lastRefresh=now;
        app.sync.execute(()->{try{
            for(Source source:app.sources.all())if(source.type==Source.Type.SUBSONIC){try{synchronize(source);}catch(Exception ignored){/* Retain the last successful server snapshot while offline. */}}
        }catch(Exception ignored){/* An unavailable credential vault must not crash local playback. */}
        finally{refreshQueued.set(false);}});
    }
    private void flushFavorites(String onlySource) throws Exception {
        Exception first=null;
        for(Library.FavoriteChange change:app.library.pendingFavorites()){
            if(onlySource!=null&&!onlySource.equals(change.source()))continue;
            try{
                Source source=app.sources.get(change.source());if(source==null||source.type!=Source.Type.SUBSONIC)continue;
                app.subsonic.star(source,change.remoteId(),change.value());app.library.acknowledgeFavorite(change);
            }catch(Exception failure){if(first==null)first=failure;}
        }
        if(first!=null)throw first;
    }
    /** Call on the serialized sync executor after the track catalog is merged. */
    public void synchronize(Source source) throws Exception {
        if(source.type!=Source.Type.SUBSONIC)return;
        app.library.seedFavorites(source.id);
        // Failed pushes stay in the outbox and are excluded from incoming favorite updates.
        try{flushFavorites(source.id);}catch(Exception error){requestFlush();}
        List<Track> starred=app.subsonic.starred(source);
        List<ServerPlaylist> playlists=app.subsonic.playlists(source);
        app.library.importFavorites(source.id,starred);app.library.importPlaylists(source.id,source.username,playlists);
        pendingCount=app.library.pendingFavorites().size();changed("");
    }
    private Source server(Library.Playlist playlist) throws Exception {
        if(playlist==null||!playlist.remote()||!playlist.writable())throw new java.io.IOException(Texts.get(app,R.string.ui_this_playlist_is_read_only));
        Source source=app.sources.get(playlist.source());if(source==null)throw new java.io.IOException(Texts.get(app,R.string.ui_source_removed));return source;
    }
    public void create(Source source,String name,Track initial) throws Exception {
        if(initial!=null&&!initial.sourceId.equals(source.id))throw new java.io.IOException(Texts.get(app,R.string.ui_the_track_belongs_to_another_source));
        ServerPlaylist playlist=app.subsonic.createPlaylist(source,name,initial);app.library.importPlaylist(source.id,source.username,playlist);
    }
    public void add(Library.Playlist playlist,Track track) throws Exception {
        Source source=server(playlist);if(!source.id.equals(track.sourceId))throw new java.io.IOException(Texts.get(app,R.string.ui_the_track_belongs_to_another_source));
        app.library.importPlaylist(source.id,source.username,app.subsonic.addToPlaylist(source,playlist.remoteId(),track));
    }
    public void rename(Library.Playlist playlist,String name) throws Exception {Source source=server(playlist);app.library.importPlaylist(source.id,source.username,app.subsonic.renamePlaylist(source,playlist.remoteId(),name));}
    public void remove(Library.Playlist playlist,int position,Track track) throws Exception {Source source=server(playlist);app.library.importPlaylist(source.id,source.username,app.subsonic.removeFromPlaylist(source,playlist.remoteId(),position,track.remoteId));}
    public void delete(Library.Playlist playlist) throws Exception {Source source=server(playlist);app.subsonic.deletePlaylist(source,playlist.remoteId());app.library.deletePlaylist(playlist.id());}
}
