package app.selfonium.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import app.selfonium.core.Track;
import java.util.ArrayList;
import java.util.List;

public final class Library extends SQLiteOpenHelper {
    public Library(Context context) { super(context,"selfonium.db",null,2); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging(); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY,source TEXT NOT NULL,remote_id TEXT,title TEXT,artist TEXT,album TEXT,uri TEXT,duration INTEGER,number INTEGER,favorite INTEGER NOT NULL DEFAULT 0,played INTEGER NOT NULL DEFAULT 0,offline TEXT NOT NULL DEFAULT '',artwork TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX tracks_source ON tracks(source)");
        db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE playlist_tracks(playlist INTEGER REFERENCES playlists(id) ON DELETE CASCADE,track TEXT REFERENCES tracks(id) ON DELETE CASCADE,position INTEGER,PRIMARY KEY(playlist,track))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to) {
        if(from<2)db.execSQL("ALTER TABLE tracks ADD COLUMN artwork TEXT NOT NULL DEFAULT ''");
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
            // Keep downloaded tracks even when a server temporarily removes them.
            if(prune)db.delete("tracks","source=? AND offline='' AND id NOT IN (SELECT id FROM sync_ids)",new String[]{source});
            db.delete("sync_ids",null,null); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public List<Track> all() { return query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE",null); }
    public Track get(String id) { List<Track> list=query("SELECT * FROM tracks WHERE id=?",new String[]{id});return list.isEmpty()?null:list.get(0); }
    public List<Track> recent() { return query("SELECT * FROM tracks WHERE played>0 ORDER BY played DESC LIMIT 30",null); }
    public List<Track> favorites() { return query("SELECT * FROM tracks WHERE favorite=1 ORDER BY title COLLATE NOCASE",null); }
    public List<Track> offlineTracks() { return query("SELECT * FROM tracks WHERE offline<>'' ORDER BY title COLLATE NOCASE",null); }
    public List<Track> playlist(long id) { return query("SELECT t.* FROM tracks t JOIN playlist_tracks p ON p.track=t.id WHERE p.playlist=? ORDER BY p.position",new String[]{Long.toString(id)}); }
    private List<Track> query(String sql,String[] args) {
        List<Track> tracks=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)) {while(c.moveToNext())tracks.add(new Track(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getLong(7),c.getInt(8),c.getString(c.getColumnIndexOrThrow("artwork"))));}
        return tracks;
    }
    private String value(String id,String field) {
        try(Cursor c=getReadableDatabase().query("tracks",new String[]{field},"id=?",new String[]{id},null,null,null)) {return c.moveToFirst()?c.getString(0):"";}
    }
    public boolean favorite(String id) {return "1".equals(value(id,"favorite"));}
    public void toggleFavorite(String id) {getWritableDatabase().execSQL("UPDATE tracks SET favorite=1-favorite WHERE id=?",new Object[]{id});}
    public void played(String id) {ContentValues v=new ContentValues();v.put("played",System.currentTimeMillis());getWritableDatabase().update("tracks",v,"id=?",new String[]{id});}
    public String offline(String id) {return value(id,"offline");}
    public void offline(String id,String path) {ContentValues v=new ContentValues();v.put("offline",path);getWritableDatabase().update("tracks",v,"id=?",new String[]{id});}
    public void artwork(String id,String reference) {ContentValues v=new ContentValues();v.put("artwork",reference);getWritableDatabase().update("tracks",v,"id=?",new String[]{id});}
    public void removeSource(String id) {getWritableDatabase().delete("tracks","source=?",new String[]{id});}
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
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO playlist_tracks(playlist,track,position) SELECT ?,?,COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist=?",new Object[]{playlist,track,playlist});
    }
    public void removeFromPlaylist(long playlist,String track) {getWritableDatabase().delete("playlist_tracks","playlist=? AND track=?",new String[]{Long.toString(playlist),track});}
    public void deletePlaylist(long id) {getWritableDatabase().delete("playlists","id=?",new String[]{Long.toString(id)});}
}
