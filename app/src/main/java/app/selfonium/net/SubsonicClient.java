package app.selfonium.net;

import app.selfonium.core.Protocol;
import app.selfonium.core.Source;
import app.selfonium.core.Track;
import app.selfonium.core.ServerPlaylist;
import app.selfonium.data.SourceStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SubsonicClient {
    public record Stream(String url,Map<String,String> headers) {}
    public interface Progress {void update(String status);}
    private final SourceStore store;
    private final java.util.function.Consumer<String> diagnostics;
    private static final int ALBUM_WORKERS=3;
    public static final class ApiException extends IOException {
        public final int code;
        ApiException(int code){super("Subsonic API error "+code);this.code=code;}
    }
    private static String failure(Exception error){
        if(error instanceof ApiException api)return "api-code="+api.code;
        if(error instanceof Http.StatusException http)return "http-status="+http.status;
        if(error instanceof org.json.JSONException)return "invalid-response";
        if(error instanceof IOException)return "network-"+error.getClass().getSimpleName();
        return "unexpected-"+error.getClass().getSimpleName();
    }
    private static JSONArray optionalArray(JSONObject object,String name) throws Exception {
        return object.isNull(name)?new JSONArray():object.getJSONArray(name);
    }
    private final Map<String,Map<String,Set<Integer>>> extensions=new java.util.concurrent.ConcurrentHashMap<>();
    public boolean supports(Source source,String name,int version){return extensions.getOrDefault(source.id,Map.of()).getOrDefault(name,Set.of()).contains(version);}
    /** Optional discovery never makes the baseline Subsonic library depend on extensions. */
    private void discover(Source source,JSONObject ping){discover(source,ping,null);}
    private void discover(Source source,JSONObject ping,Http.Cancel cancel){
        Map<String,Set<Integer>> found=new java.util.HashMap<>();
        if(ping.optBoolean("openSubsonic",false))try{
            JSONArray entries=sub(source,"getOpenSubsonicExtensions",Map.of(),cancel).getJSONArray("openSubsonicExtensions");
            for(int i=0;i<entries.length();i++){
                JSONObject entry=entries.getJSONObject(i);JSONArray versions=entry.getJSONArray("versions");Set<Integer> supported=new HashSet<>();
                for(int at=0;at<versions.length();at++)supported.add(versions.getInt(at));
                found.put(entry.getString("name"),Set.copyOf(supported));
            }
            diagnostics.accept("extensions discovered="+found.size());
        }catch(Exception error){found.clear();diagnostics.accept("extensions fallback "+failure(error));}
        else diagnostics.accept("extensions not advertised");
        extensions.put(source.id,Map.copyOf(found));
    }
    public SubsonicClient(SourceStore store){this(store,message->{});}
    public SubsonicClient(SourceStore store,java.util.function.Consumer<String> diagnostics){this.store=store;this.diagnostics=diagnostics;}
    private JSONObject sub(Source s,String method,Map<String,String> args) throws Exception {return sub(s,method,args,null);}
    private JSONObject sub(Source s,String method,Map<String,String> args,Http.Cancel cancel) throws Exception {
        JSONObject response=new JSONObject(Http.text("GET",Protocol.subsonic(s,method,args),Map.of(),null,cancel)).getJSONObject("subsonic-response");
        if(!"ok".equals(response.optString("status")))throw new ApiException(response.optJSONObject("error")==null?-1:response.getJSONObject("error").optInt("code",-1));
        return response;
    }
    public void discoverPlayback(Source source,Http.Cancel cancel)throws Exception{if(!extensions.containsKey(source.id))discover(source,sub(source,"ping",Map.of(),cancel),cancel);}
    public void disablePlaybackReport(Source source){Map<String,Set<Integer>> known=new java.util.HashMap<>(extensions.getOrDefault(source.id,Map.of()));known.remove("playbackReport");extensions.put(source.id,Map.copyOf(known));}
    public void scrobble(Source source,String remote,long time,boolean submission,Http.Cancel cancel)throws Exception{
        if(remote==null||remote.isEmpty()||time<=0)throw new IllegalArgumentException();
        sub(source,"scrobble",Map.of("id",remote,"time",Long.toString(time),"submission",Boolean.toString(submission)),cancel);
    }
    public void reportPlayback(Source source,String remote,String state,long position,float rate,Http.Cancel cancel)throws Exception{
        if(!Set.of("starting","playing","paused","stopped").contains(state)||!Float.isFinite(rate)||rate<=0||remote==null||remote.isEmpty())throw new IllegalArgumentException();
        sub(source,"reportPlayback",Map.of("mediaId",remote,"mediaType","song","state",state,"positionMs",Long.toString(Math.max(0,position)),"playbackRate",Float.toString(rate),"ignoreScrobble","true"),cancel);
    }
    public List<Track> synchronize(Source s,Progress progress) throws Exception {
        return subsonic(s,progress);
    }
    private Track track(Source s,String id,String title,String artist,String album,String uri,long duration,int number) {
        return new Track(s.id+":"+id,s.id,id,title,artist,album,uri,duration,number);
    }
    private List<Track> album(Source source,String id) throws Exception {
        JSONObject album=sub(source,"getAlbum",Map.of("id",id)).getJSONObject("album");
        JSONArray songs=optionalArray(album,"song");List<Track> result=new ArrayList<>();
        for(int i=0;i<songs.length();i++){
            JSONObject song=songs.getJSONObject(i);
            result.add(track(source,song.getString("id"),song.optString("title"),song.optString("artist"),song.optString("album",album.optString("name")),"",song.optLong("duration")*1000,song.optInt("track")).withArtwork(song.optString("coverArt",album.optString("coverArt"))));
        }
        return result;
    }
    private List<Track> subsonic(Source source,Progress progress) throws Exception {
        long started=System.nanoTime();discover(source,sub(source,"ping",Map.of()));
        List<Track> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(ALBUM_WORKERS);
        List<java.util.concurrent.Future<List<Track>>> pending=new ArrayList<>();
        try{
            for(int offset=0;;offset+=500){
                JSONObject list=sub(source,"getAlbumList2",Protocol.params("type","alphabeticalByName","size","500","offset",Integer.toString(offset))).getJSONObject("albumList2");
                JSONArray albums=optionalArray(list,"album");if(albums.length()==0)break;
                List<String> ids=new ArrayList<>();
                for(int at=0;at<albums.length();at++){
                    String id=albums.getJSONObject(at).getString("id");
                    if(!seen.add(id))throw new IOException("Сервер повторює сторінки альбомів");ids.add(id);
                }
                // Submit only one bounded batch; merge in server order regardless of completion order.
                for(int first=0;first<ids.size();first+=ALBUM_WORKERS){
                    pending.clear();
                    for(int at=first;at<Math.min(first+ALBUM_WORKERS,ids.size());at++){
                        String id=ids.get(at);pending.add(workers.submit(()->album(source,id)));
                    }
                    for(var task:pending){
                        try{result.addAll(task.get());}
                        catch(java.util.concurrent.ExecutionException error){if(error.getCause() instanceof Exception cause)throw cause;throw new IOException("Не вдалося прочитати альбом");}
                        progress.update(source.name+": "+result.size()+" композицій");
                    }
                }
                if(albums.length()<500)break;
            }
            diagnostics.accept("catalog complete albums="+seen.size()+" tracks="+result.size()+" elapsedMs="+((System.nanoTime()-started)/1_000_000));
            return result;
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw error;}
        finally{for(var task:pending)task.cancel(true);workers.shutdownNow();}
    }
    private Track subTrack(Source source,JSONObject song) throws Exception {
        return track(source,song.getString("id"),song.optString("title"),song.optString("artist"),song.optString("album"),"",song.optLong("duration")*1000,song.optInt("track")).withArtwork(song.optString("coverArt"));
    }
    public List<Track> starred(Source source) throws Exception {
        JSONObject response=sub(source,"getStarred2",Map.of()).getJSONObject("starred2");
        JSONArray songs=optionalArray(response,"song");List<Track> result=new ArrayList<>();
        if(songs!=null)for(int i=0;i<songs.length();i++)result.add(subTrack(source,songs.getJSONObject(i)));return result;
    }
    public void star(Source source,String remoteId,boolean value) throws Exception {sub(source,value?"star":"unstar",Map.of("id",remoteId));}
    private ServerPlaylist playlist(Source source,JSONObject data) throws Exception {
        JSONArray songs=optionalArray(data,"entry");List<Track> tracks=new ArrayList<>();
        if(songs!=null)for(int i=0;i<songs.length();i++)tracks.add(subTrack(source,songs.getJSONObject(i)));
        return new ServerPlaylist(data.getString("id"),data.optString("name"),data.optString("owner"),tracks);
    }
    public ServerPlaylist playlist(Source source,String id) throws Exception {return playlist(source,sub(source,"getPlaylist",Map.of("id",id)).getJSONObject("playlist"));}
    public List<ServerPlaylist> playlists(Source source) throws Exception {
        JSONArray entries=optionalArray(sub(source,"getPlaylists",Map.of()).getJSONObject("playlists"),"playlist");List<ServerPlaylist> result=new ArrayList<>();
        if(entries!=null)for(int i=0;i<entries.length();i++)result.add(playlist(source,entries.getJSONObject(i).getString("id")));return result;
    }
    public ServerPlaylist createPlaylist(Source source,String name,Track initial) throws Exception {
        Map<String,String> args=Protocol.params("name",name);if(initial!=null)args.put("songId",initial.remoteId);
        JSONObject response=sub(source,"createPlaylist",args);JSONObject created=response.optJSONObject("playlist");
        if(created==null)throw new IOException("Сервер створив плейлист, але не повернув його ID. Оновіть джерело, перш ніж повторювати створення.");
        return playlist(source,created);
    }
    public ServerPlaylist renamePlaylist(Source source,String id,String name) throws Exception {sub(source,"updatePlaylist",Protocol.params("playlistId",id,"name",name));return playlist(source,id);}
    public ServerPlaylist addToPlaylist(Source source,String id,Track track) throws Exception {
        ServerPlaylist current=playlist(source,id);
        if(current.tracks().stream().noneMatch(t->t.remoteId.equals(track.remoteId)))sub(source,"updatePlaylist",Protocol.params("playlistId",id,"songIdToAdd",track.remoteId));
        return playlist(source,id);
    }
    public ServerPlaylist removeFromPlaylist(Source source,String id,int index,String expectedId) throws Exception {
        ServerPlaylist current=playlist(source,id);
        if(index<0||index>=current.tracks().size()||!current.tracks().get(index).remoteId.equals(expectedId))throw new IOException("Плейлист змінився на сервері. Оновіть його перед видаленням композиції.");
        sub(source,"updatePlaylist",Protocol.params("playlistId",id,"songIndexToRemove",Integer.toString(index)));return playlist(source,id);
    }
    public void deletePlaylist(Source source,String id) throws Exception {sub(source,"deletePlaylist",Map.of("id",id));}
    public record Artwork(String reference, Stream stream) {}
    /** OpenSubsonic coverArt IDs are opaque: a song ID is not a coverArt ID. */
    public Artwork artwork(Track track) throws Exception {
        Source s=store.get(track.sourceId);
        if(s==null||s.type!=Source.Type.SUBSONIC)return null;
        String reference=track.artwork;
        if(reference.isEmpty()) {
            JSONObject song=sub(s,"getSong",Map.of("id",track.remoteId)).getJSONObject("song");
            reference=song.optString("coverArt");
        }
        if(reference.isEmpty())return null;
        String url=Protocol.subsonic(s,"getCoverArt",Protocol.params("id",reference,"size","768"));
        return new Artwork(reference,new Stream(url,Map.of()));
    }
    public Stream stream(Track track) throws Exception {
        if(track.sourceId.equals("device")||track.sourceId.equals("files")||track.sourceId.equals("radio"))return new Stream(track.uri,Map.of());
        Source s=store.get(track.sourceId);if(s==null)throw new IOException("Джерело недоступне: підтримується лише Subsonic / OpenSubsonic");
        return new Stream(Protocol.subsonic(s,"stream",Protocol.params("id",track.remoteId,"format","raw")),Map.of());
    }
}
