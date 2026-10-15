package app.selfonium.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import app.selfonium.core.Track;
import app.selfonium.core.LibraryFilter;
import app.selfonium.core.ServerPlaylist;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Library extends SQLiteOpenHelper {
    private final Map<String,Track> trackCache=new ConcurrentHashMap<>();
    private final Map<String,Boolean> favoriteCache=new ConcurrentHashMap<>();
    private final Map<String,Preview> previews=new ConcurrentHashMap<>();
    private final AtomicLong previewVersion=new AtomicLong();
    public record Preview(long version,boolean value) {}
    public record FavoriteChange(String track,String source,String remoteId,boolean value,long revision) {}
    public record Playlist(long id,String name,String source,String remoteId,boolean writable,int count) {public boolean remote(){return !remoteId.isEmpty();}}
    public Library(Context context) { super(context,"selfonium.db",null,3); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging(); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY,source TEXT NOT NULL,remote_id TEXT,title TEXT,artist TEXT,album TEXT,uri TEXT,duration INTEGER,number INTEGER,favorite INTEGER NOT NULL DEFAULT 0,played INTEGER NOT NULL DEFAULT 0,offline TEXT NOT NULL DEFAULT '',artwork TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX tracks_source ON tracks(source)");
        db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE playlist_tracks(playlist INTEGER REFERENCES playlists(id) ON DELETE CASCADE,track TEXT REFERENCES tracks(id) ON DELETE CASCADE,position INTEGER NOT NULL,PRIMARY KEY(playlist,position))");
        createSocialTables(db);
    }
    private static void createSocialTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE remote_playlists(playlist INTEGER PRIMARY KEY REFERENCES playlists(id) ON DELETE CASCADE,source TEXT NOT NULL,remote_id TEXT NOT NULL,remote_name TEXT NOT NULL,writable INTEGER NOT NULL,UNIQUE(source,remote_id))");
        db.execSQL("CREATE TABLE favorite_outbox(track TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,value INTEGER NOT NULL,revision INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE social_sources(source TEXT PRIMARY KEY)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to) {
        if(from<2)db.execSQL("ALTER TABLE tracks ADD COLUMN artwork TEXT NOT NULL DEFAULT ''");
        if(from<3){
            db.execSQL("CREATE TABLE playlist_entries(playlist INTEGER REFERENCES playlists(id) ON DELETE CASCADE,track TEXT REFERENCES tracks(id) ON DELETE CASCADE,position INTEGER NOT NULL,PRIMARY KEY(playlist,position))");
            db.execSQL("INSERT INTO playlist_entries SELECT playlist,track,position FROM playlist_tracks");
            db.execSQL("DROP TABLE playlist_tracks");
            db.execSQL("ALTER TABLE playlist_entries RENAME TO playlist_tracks");
            createSocialTables(db);
        }
    }
    public synchronized void merge(String source,List<Track> tracks,boolean prune) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS sync_ids(id TEXT PRIMARY KEY)"); db.delete("sync_ids",null,null);
            for(Track track:tracks) {
                if (!source.equals(track.sourceId)) throw new IllegalArgumentException("Source mismatch");
                ContentValues v=new ContentValues(); v.put("id",track.id);v.put("source",source);v.put("remote_id",track.remoteId);
                v.put("title",track.title);v.put("artist",track.artist);v.put("album",track.album);v.put("uri",track.uri);
                v.put("duration",track.durationMs);v.put("number",track.number);
                if(!track.artwork.isEmpty())v.put("artwork",track.artwork);
                if(db.update("tracks",v,"id=?",new String[]{track.id})==0)db.insertOrThrow("tracks",null,v);
                ContentValues id=new ContentValues();id.put("id",track.id);db.insertWithOnConflict("sync_ids",null,id,SQLiteDatabase.CONFLICT_IGNORE);
            }
            // Never prune downloads, favorites, playlist entries or durable pending actions.
            if(prune)db.delete("tracks","source=? AND offline='' AND favorite=0 AND id NOT IN (SELECT track FROM favorite_outbox) AND id NOT IN (SELECT track FROM playlist_tracks) AND id NOT IN (SELECT id FROM sync_ids)",new String[]{source});
            db.delete("sync_ids",null,null); db.setTransactionSuccessful();
            if(prune){Set<String> kept=new HashSet<>();try(Cursor c=db.rawQuery("SELECT id FROM tracks WHERE source=?",new String[]{source})){while(c.moveToNext())kept.add(c.getString(0));}
                trackCache.entrySet().removeIf(e->{boolean removed=source.equals(e.getValue().sourceId)&&!kept.contains(e.getKey());if(removed)favoriteCache.remove(e.getKey());return removed;});}
            for(Track track:tracks)trackCache.compute(track.id,(key,old)->track.artwork.isEmpty()&&old!=null?track.withArtwork(old.artwork):track);
        } finally { db.endTransaction(); }
    }
    public List<Track> all() { return query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE",null); }
    public Track get(String id) { Track cached=trackCache.get(id);if(cached!=null)return cached;List<Track> list=query("SELECT * FROM tracks WHERE id=?",new String[]{id});return list.isEmpty()?null:list.get(0); }
    public List<Track> recent() { return query("SELECT * FROM tracks WHERE played>0 ORDER BY played DESC LIMIT 30",null); }
    public List<Track> favorites() { return query("SELECT * FROM tracks WHERE favorite=1 ORDER BY title COLLATE NOCASE",null); }
    public List<Track> offlineTracks() { return query("SELECT * FROM tracks WHERE offline<>'' ORDER BY title COLLATE NOCASE",null); }
    public List<Track> playlist(long id) { return query("SELECT t.* FROM tracks t JOIN playlist_tracks p ON p.track=t.id WHERE p.playlist=? ORDER BY p.position",new String[]{Long.toString(id)}); }
    public boolean playlistMatchesSource(Playlist playlist,String filter){
        String value=LibraryFilter.normalize(filter);if(value.isEmpty())return true;
        if(playlist.remote())return LibraryFilter.matches(value,playlist.source());
        if(playlist.count()==0)return value.equals(LibraryFilter.LOCAL);
        String selection=value.equals(LibraryFilter.LOCAL)?"t.source IN ('device','files')":"t.source=?";
        String[] args=value.equals(LibraryFilter.LOCAL)?new String[]{Long.toString(playlist.id())}:new String[]{Long.toString(playlist.id()),value};
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT 1 FROM playlist_tracks p JOIN tracks t ON t.id=p.track WHERE p.playlist=? AND "+selection+" LIMIT 1",args)){return cursor.moveToFirst();}
    }
    private List<Track> query(String sql,String[] args) {
        List<Track> tracks=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)) {while(c.moveToNext()){Track track=new Track(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getLong(7),c.getInt(8),c.getString(c.getColumnIndexOrThrow("artwork")));
            tracks.add(track);trackCache.put(track.id,track);favoriteCache.putIfAbsent(track.id,c.getInt(c.getColumnIndexOrThrow("favorite"))!=0);}}
        return tracks;
    }
    private String value(String id,String field) {
        try(Cursor c=getReadableDatabase().query("tracks",new String[]{field},"id=?",new String[]{id},null,null,null)) {return c.moveToFirst()?c.getString(0):"";}
    }
    public boolean favorite(String id) {Preview preview=previews.get(id);return preview!=null?preview.value():favoriteCache.computeIfAbsent(id,key->"1".equals(value(key,"favorite")));}
    public void discardFavorite(String id,Preview preview){previews.remove(id,preview);}
    public Preview previewFavorite(String id,boolean favorite){Preview preview=new Preview(previewVersion.incrementAndGet(),favorite);previews.put(id,preview);return preview;}
    public synchronized void saveFavorite(String id,Preview preview,boolean remote){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            db.execSQL("UPDATE tracks SET favorite=? WHERE id=?",new Object[]{preview.value()?1:0,id});
            if(remote){
                db.execSQL("UPDATE favorite_outbox SET value=?,revision=revision+1 WHERE track=?",new Object[]{preview.value()?1:0,id});
                db.execSQL("INSERT OR IGNORE INTO favorite_outbox(track,value,revision) VALUES (?,?,1)",new Object[]{id,preview.value()?1:0});
            }
            db.setTransactionSuccessful();favoriteCache.put(id,preview.value());
        }finally{db.endTransaction();previews.remove(id,preview);}
    }
    public void toggleFavorite(String id) {saveFavorite(id,previewFavorite(id,!favorite(id)),false);}
    public void played(String id) {ContentValues v=new ContentValues();v.put("played",System.currentTimeMillis());getWritableDatabase().update("tracks",v,"id=?",new String[]{id});}
    public String offline(String id) {return value(id,"offline");}
    public void offline(String id,String path) {ContentValues v=new ContentValues();v.put("offline",path);getWritableDatabase().update("tracks",v,"id=?",new String[]{id});}
    public void artwork(String id,String reference) {trackCache.computeIfPresent(id,(key,track)->track.withArtwork(reference));ContentValues v=new ContentValues();v.put("artwork",reference);getWritableDatabase().update("tracks",v,"id=?",new String[]{id});}
    public synchronized void removeSource(String id) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            db.execSQL("DELETE FROM playlists WHERE id IN (SELECT playlist FROM remote_playlists WHERE source=?)",new Object[]{id});
            db.delete("tracks","source=?",new String[]{id});db.delete("social_sources","source=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}trackCache.entrySet().removeIf(e->{boolean removed=id.equals(e.getValue().sourceId);if(removed)favoriteCache.remove(e.getKey());return removed;});
    }
    public long createPlaylist(String name) {
        if(name.trim().isEmpty())throw new IllegalArgumentException("Введіть назву");
        ContentValues v=new ContentValues();v.put("name",name.trim());return getWritableDatabase().insertOrThrow("playlists",null,v);
    }
    public List<String[]> playlists() {
        List<String[]> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id,p.name,COUNT(t.track) FROM playlists p LEFT JOIN playlist_tracks t ON t.playlist=p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE",null)){
            while(c.moveToNext())result.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});
        }return result;
    }
    public void addToPlaylist(long playlist,String track) {
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO playlist_tracks(playlist,track,position) SELECT ?,?,COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist=? HAVING NOT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlist=? AND track=?)",new Object[]{playlist,track,playlist,playlist,track});
    }
    public void removeFromPlaylist(long playlist,String track) {getWritableDatabase().delete("playlist_tracks","playlist=? AND track=?",new String[]{Long.toString(playlist),track});}
    public void deletePlaylist(long id) {getWritableDatabase().delete("playlists","id=?",new String[]{Long.toString(id)});}

    public synchronized void seedFavorites(String source){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT source FROM social_sources WHERE source=?",new String[]{source})){if(c.moveToFirst())return;}
            db.execSQL("INSERT OR IGNORE INTO favorite_outbox SELECT id,1,1 FROM tracks WHERE source=? AND favorite=1",new Object[]{source});
            db.execSQL("INSERT INTO social_sources VALUES (?)",new Object[]{source});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public List<FavoriteChange> pendingFavorites(){
        List<FavoriteChange> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT f.track,t.source,t.remote_id,f.value,f.revision FROM favorite_outbox f JOIN tracks t ON t.id=f.track",null)){
            while(c.moveToNext())result.add(new FavoriteChange(c.getString(0),c.getString(1),c.getString(2),c.getInt(3)!=0,c.getLong(4)));
        }return result;
    }
    public void acknowledgeFavorite(FavoriteChange change){getWritableDatabase().delete("favorite_outbox","track=? AND revision=? AND value=?",new String[]{change.track(),Long.toString(change.revision()),change.value()?"1":"0"});}
    public synchronized void importFavorites(String source,List<Track> starred){
        merge(source,starred,false);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            db.execSQL("UPDATE tracks SET favorite=0 WHERE source=? AND id NOT IN (SELECT track FROM favorite_outbox)",new Object[]{source});
            for(Track track:starred)db.execSQL("UPDATE tracks SET favorite=1 WHERE id=? AND id NOT IN (SELECT track FROM favorite_outbox)",new Object[]{track.id});
            db.setTransactionSuccessful();
            try(Cursor c=db.rawQuery("SELECT id,favorite FROM tracks WHERE source=?",new String[]{source})){while(c.moveToNext())favoriteCache.put(c.getString(0),c.getInt(1)!=0);}
        }finally{db.endTransaction();}
    }
    public List<Playlist> playlistInfo(){
        List<Playlist> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id,COALESCE(r.remote_name,p.name),COALESCE(r.source,''),COALESCE(r.remote_id,''),COALESCE(r.writable,1),(SELECT COUNT(*) FROM playlist_tracks t WHERE t.playlist=p.id) FROM playlists p LEFT JOIN remote_playlists r ON r.playlist=p.id ORDER BY COALESCE(r.remote_name,p.name) COLLATE NOCASE",null)){
            while(c.moveToNext())result.add(new Playlist(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)!=0,c.getInt(5)));
        }return result;
    }
    public Track playlistFirst(long id){List<Track> tracks=query("SELECT t.* FROM tracks t JOIN playlist_tracks p ON p.track=t.id WHERE p.playlist=? ORDER BY p.position LIMIT 1",new String[]{Long.toString(id)});return tracks.isEmpty()?null:tracks.get(0);}
    public Playlist playlistInfo(long id){for(Playlist item:playlistInfo())if(item.id()==id)return item;return null;}
    public synchronized long importPlaylist(String source,String username,ServerPlaylist remote){
        merge(source,remote.tracks(),false);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            long local=-1;
            try(Cursor c=db.rawQuery("SELECT playlist FROM remote_playlists WHERE source=? AND remote_id=?",new String[]{source,remote.id()})){if(c.moveToFirst())local=c.getLong(0);}
            if(local<0){String name=remote.name();int suffix=0;
                while(true){try(Cursor c=db.rawQuery("SELECT id FROM playlists WHERE name=?",new String[]{name})){if(!c.moveToFirst())break;}name=remote.name()+" ("+(++suffix)+")";}
                local=createPlaylist(name);
            }
            ContentValues values=new ContentValues();values.put("playlist",local);values.put("source",source);values.put("remote_id",remote.id());values.put("remote_name",remote.name());values.put("writable",remote.owner().isEmpty()||remote.owner().equals(username)?1:0);
            if(db.update("remote_playlists",values,"playlist=?",new String[]{Long.toString(local)})==0)db.insertOrThrow("remote_playlists",null,values);
            db.delete("playlist_tracks","playlist=?",new String[]{Long.toString(local)});
            for(int i=0;i<remote.tracks().size();i++)db.execSQL("INSERT INTO playlist_tracks VALUES (?,?,?)",new Object[]{local,remote.tracks().get(i).id,i});
            db.setTransactionSuccessful();return local;
        }finally{db.endTransaction();}
    }
    public synchronized void importPlaylists(String source,String username,List<ServerPlaylist> playlists){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            Set<String> ids=new HashSet<>();for(ServerPlaylist remote:playlists){ids.add(remote.id());importPlaylist(source,username,remote);}
            for(Playlist local:playlistInfo())if(local.source().equals(source)&&!ids.contains(local.remoteId()))deletePlaylist(local.id());
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void renamePlaylist(long id,String name){if(name.trim().isEmpty())throw new IllegalArgumentException("Введіть назву");ContentValues v=new ContentValues();v.put("name",name.trim());getWritableDatabase().update("playlists",v,"id=?",new String[]{Long.toString(id)});}
}
