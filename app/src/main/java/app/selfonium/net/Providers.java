package app.selfonium.net;

import app.selfonium.core.Protocol;
import app.selfonium.core.Source;
import app.selfonium.core.Track;
import app.selfonium.core.ServerPlaylist;
import app.selfonium.data.SourceStore;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;

public final class Providers {
    public record Stream(String url,Map<String,String> headers) {}
    public interface Progress {void update(String status);}
    private final SourceStore store;
    public Providers(SourceStore store){this.store=store;}
    private Map<String,String> headers(Source s) {
        Map<String,String> h=new LinkedHashMap<>();h.put("Accept","application/json");
        switch(s.type){
            case JELLYFIN,EMBY -> {
                String authorization="MediaBrowser Client=\"SELFonium\", Device=\"Android\", DeviceId=\"selfonium-"+s.id+"\", Version=\"0.5.0\"";
                if(s.type==Source.Type.JELLYFIN){
                    // Current Jellyfin accepts client identity and session token in Authorization.
                    // X-Emby-* alone can fail even before credential validation.
                    if(!s.token.isEmpty())authorization+=", Token=\""+s.token+"\"";
                    h.put("Authorization",authorization);
                }else{
                    h.put("X-Emby-Authorization",authorization);
                    if(!s.token.isEmpty())h.put("X-Emby-Token",s.token);
                }
            }
            case PLEX -> {h.put("X-Plex-Token",s.token);h.put("X-Plex-Product","SELFonium");h.put("X-Plex-Client-Identifier",s.id);}
            case AUDIOBOOKSHELF -> {if(!s.token.isEmpty())h.put("Authorization","Bearer "+s.token);}
            case WEBDAV,KODI -> {if(!s.username.isEmpty())h.put("Authorization","Basic "+Base64.encodeToString((s.username+":"+s.password).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));}
            default -> {}
        }return h;
    }
    private JSONObject get(Source s,String path) throws Exception{return new JSONObject(Http.text("GET",s.baseUrl+path,headers(s),null));}
    private JSONObject post(Source s,String path,JSONObject data) throws Exception {
        Map<String,String> h=headers(s);h.put("Content-Type","application/json");return new JSONObject(Http.text("POST",s.baseUrl+path,h,data.toString()));
    }
    private JSONObject sub(Source s,String method,Map<String,String> args) throws Exception {
        JSONObject response=new JSONObject(Http.text("GET",Protocol.subsonic(s,method,args),Map.of(),null)).getJSONObject("subsonic-response");
        if(!"ok".equals(response.optString("status")))throw new IOException("Subsonic відхилив запит; перевірте облікові дані та сумісність API");
        return response;
    }
    public List<Track> synchronize(Source s,Progress progress) throws Exception {
        List<Track> tracks=switch(s.type){
            case SUBSONIC -> subsonic(s,progress);
            case JELLYFIN,EMBY -> jellyfin(s,progress);
            case PLEX -> plex(s,progress);
            case WEBDAV -> webdav(s,progress);
            case KODI -> kodi(s,progress);
            case AUDIOBOOKSHELF -> audiobookshelf(s,progress);
        };
        // Persist newly issued session tokens only after a successful sync.
        store.save(s);return tracks;
    }
    private Track track(Source s,String id,String title,String artist,String album,String uri,long duration,int number) {
        return new Track(s.id+":"+id,s.id,id,title,artist,album,uri,duration,number);
    }
    private List<Track> subsonic(Source s,Progress progress) throws Exception {
        sub(s,"ping",Map.of());List<Track> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int offset=0;;offset+=500){
            JSONObject list=sub(s,"getAlbumList2",Protocol.params("type","alphabeticalByName","size","500","offset",Integer.toString(offset))).optJSONObject("albumList2");
            JSONArray albums=list==null?null:list.optJSONArray("album");if(albums==null||albums.length()==0)break;
            for(int a=0;a<albums.length();a++){
                String id=albums.getJSONObject(a).getString("id");if(!seen.add(id))throw new IOException("Сервер повторює сторінки альбомів");
                JSONObject album=sub(s,"getAlbum",Map.of("id",id)).getJSONObject("album");JSONArray songs=album.optJSONArray("song");
                if(songs!=null)for(int i=0;i<songs.length();i++){
                    JSONObject t=songs.getJSONObject(i);result.add(track(s,t.getString("id"),t.optString("title"),t.optString("artist"),t.optString("album",album.optString("name")),"",t.optLong("duration")*1000,t.optInt("track")).withArtwork(t.optString("coverArt",album.optString("coverArt"))));
                }
                progress.update(s.name+": "+result.size()+" композицій");
            }if(albums.length()<500)break;
        }return result;
    }
    private Track subTrack(Source source,JSONObject song) throws Exception {
        return track(source,song.getString("id"),song.optString("title"),song.optString("artist"),song.optString("album"),"",song.optLong("duration")*1000,song.optInt("track")).withArtwork(song.optString("coverArt"));
    }
    public List<Track> starred(Source source) throws Exception {
        JSONObject response=sub(source,"getStarred2",Map.of()).getJSONObject("starred2");
        JSONArray songs=response.optJSONArray("song");List<Track> result=new ArrayList<>();
        if(songs!=null)for(int i=0;i<songs.length();i++)result.add(subTrack(source,songs.getJSONObject(i)));return result;
    }
    public void star(Source source,String remoteId,boolean value) throws Exception {sub(source,value?"star":"unstar",Map.of("id",remoteId));}
    private ServerPlaylist playlist(Source source,JSONObject data) throws Exception {
        JSONArray songs=data.optJSONArray("entry");List<Track> tracks=new ArrayList<>();
        if(songs!=null)for(int i=0;i<songs.length();i++)tracks.add(subTrack(source,songs.getJSONObject(i)));
        return new ServerPlaylist(data.getString("id"),data.optString("name"),data.optString("owner"),tracks);
    }
    public ServerPlaylist playlist(Source source,String id) throws Exception {return playlist(source,sub(source,"getPlaylist",Map.of("id",id)).getJSONObject("playlist"));}
    public List<ServerPlaylist> playlists(Source source) throws Exception {
        JSONArray entries=sub(source,"getPlaylists",Map.of()).getJSONObject("playlists").optJSONArray("playlist");List<ServerPlaylist> result=new ArrayList<>();
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
    private List<Track> jellyfin(Source s,Progress progress) throws Exception {
        if(s.token.isEmpty()||s.userId.isEmpty()){
            JSONObject auth=post(s,"/Users/AuthenticateByName",new JSONObject().put("Username",s.username).put("Pw",s.password));
            s.token=auth.getString("AccessToken");s.userId=auth.getJSONObject("User").getString("Id");
        }
        List<Track> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int start=0;;){
            String path=Protocol.query("/Users/"+Protocol.encode(s.userId)+"/Items",Protocol.params("Recursive","true","IncludeItemTypes","Audio","Fields","MediaSources","Limit","500","StartIndex",Integer.toString(start),"SortBy","SortName","SortOrder","Ascending"));
            JSONObject data=get(s,path);JSONArray items=data.getJSONArray("Items");
            for(int i=0;i<items.length();i++){
                JSONObject t=items.getJSONObject(i);String id=t.getString("Id");if(!seen.add(id))throw new IOException("Сервер повторює сторінки композицій");
                JSONArray artists=t.optJSONArray("Artists");result.add(track(s,id,t.optString("Name"),artists==null?"":artists.join(", ").replace("\"",""),t.optString("Album"),"",t.optLong("RunTimeTicks")/10000,t.optInt("IndexNumber")));
            }
            start+=items.length();progress.update(s.name+": "+result.size()+" композицій");
            if(items.length()==0||start>=data.optInt("TotalRecordCount",Integer.MAX_VALUE)||items.length()<500)break;
        }return result;
    }
    private List<Track> plex(Source s,Progress progress) throws Exception {
        if(s.token.isEmpty())s.token=s.password;
        JSONArray sections=get(s,"/library/sections").getJSONObject("MediaContainer").optJSONArray("Directory");List<Track> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        if(sections==null)return result;
        for(int n=0;n<sections.length();n++){
            JSONObject section=sections.getJSONObject(n);if(!"artist".equals(section.optString("type")))continue;
            for(int offset=0;;){
                JSONObject container=get(s,"/library/sections/"+Protocol.encode(section.getString("key"))+"/all?type=10&X-Plex-Container-Start="+offset+"&X-Plex-Container-Size=500").getJSONObject("MediaContainer");
                JSONArray items=container.optJSONArray("Metadata");if(items==null||items.length()==0)break;
                for(int i=0;i<items.length();i++){
                    JSONObject t=items.getJSONObject(i);String id=t.getString("ratingKey");if(!seen.add(id))throw new IOException("Plex повторює сторінки");
                    result.add(track(s,id,t.optString("title"),t.optString("grandparentTitle"),t.optString("parentTitle"),"",t.optLong("duration"),t.optInt("index")));
                }offset+=items.length();progress.update(s.name+": "+result.size()+" композицій");
                if(offset>=container.optInt("totalSize",Integer.MAX_VALUE)||items.length()<500)break;
            }
        }return result;
    }
    private JSONObject rpc(Source s,String method,JSONObject params) throws Exception {
        JSONObject response=post(s,"/jsonrpc",new JSONObject().put("jsonrpc","2.0").put("id",1).put("method",method).put("params",params));
        if(response.has("error"))throw new IOException("Kodi відхилив команду "+method);return response.getJSONObject("result");
    }
    private List<Track> kodi(Source s,Progress progress) throws Exception {
        List<Track> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int offset=0;;){
            JSONObject data=rpc(s,"AudioLibrary.GetSongs",new JSONObject().put("properties",new JSONArray(List.of("title","artist","album","duration","track","file")))
                    .put("limits",new JSONObject().put("start",offset).put("end",offset+500)));
            JSONArray songs=data.optJSONArray("songs");if(songs==null||songs.length()==0)break;
            for(int i=0;i<songs.length();i++){
                JSONObject t=songs.getJSONObject(i);String id=t.get("songid").toString();if(!seen.add(id))throw new IOException("Kodi повторює сторінки");
                JSONArray artists=t.optJSONArray("artist");result.add(track(s,id,t.optString("title"),artists==null?"":artists.join(", ").replace("\"",""),t.optString("album"),t.optString("file"),t.optLong("duration")*1000,t.optInt("track")));
            }offset+=songs.length();progress.update(s.name+": "+result.size()+" композицій");
            JSONObject limits=data.optJSONObject("limits");if(songs.length()<500||(limits!=null&&offset>=limits.optInt("total",Integer.MAX_VALUE)))break;
        }return result;
    }
    private List<Track> audiobookshelf(Source s,Progress progress) throws Exception {
        if(s.token.isEmpty())s.token=post(s,"/login",new JSONObject().put("username",s.username).put("password",s.password)).getJSONObject("user").getString("token");
        JSONArray libraries=get(s,"/api/libraries").getJSONArray("libraries");List<Track> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int lib=0;lib<libraries.length();lib++)for(int page=0;;page++){
            JSONObject data=get(s,"/api/libraries/"+Protocol.encode(libraries.getJSONObject(lib).getString("id"))+"/items?limit=100&page="+page);
            JSONArray items=data.getJSONArray("results");if(items.length()==0)break;
            for(int i=0;i<items.length();i++){
                String id=items.getJSONObject(i).getString("id");if(!seen.add(id))throw new IOException("Сервер повторює сторінки");
                JSONObject item=get(s,"/api/items/"+Protocol.encode(id)+"?expanded=1");JSONObject media=item.getJSONObject("media"), meta=media.getJSONObject("metadata");
                JSONArray tracks=media.optJSONArray("tracks");if(tracks==null)continue;
                for(int j=0;j<tracks.length();j++){
                    JSONObject t=tracks.getJSONObject(j);String content=t.getString("contentUrl");
                    result.add(track(s,id+":"+j,tracks.length()>1?meta.optString("title")+" · "+(j+1):meta.optString("title"),meta.optString("authorName"),meta.optString("title"),content,(long)(t.optDouble("duration")*1000),j+1));
                }progress.update(s.name+": "+result.size()+" аудіофайлів");
            }if((page+1)*100>=data.optInt("total",Integer.MAX_VALUE)||items.length()<100)break;
        }return result;
    }
    private List<Track> webdav(Source s,Progress progress) throws Exception {
        ArrayDeque<String> pending=new ArrayDeque<>();Set<String> visited=new HashSet<>();List<Track> result=new ArrayList<>();pending.add(s.baseUrl+"/");
        while(!pending.isEmpty()){
            String folder=pending.remove();if(!visited.add(folder))continue;
            if(visited.size()>10000)throw new IOException("Понад 10 000 каталогів; оберіть вужчу WebDAV-папку");
            Map<String,String> h=headers(s);h.put("Depth","1");h.put("Content-Type","application/xml; charset=utf-8");
            String xml=Http.text("PROPFIND",folder,h,"<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>");
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            NodeList responses=factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getElementsByTagNameNS("DAV:","response");
            for(int i=0;i<responses.getLength();i++){
                Element e=(Element)responses.item(i);String href=xmlValue(e,"href");if(href.trim().isEmpty())continue;
                String target=URI.create(folder).resolve(href).normalize().toASCIIString();
                if(!Protocol.sameOrigin(s.baseUrl,target))continue;
                String rootPath=URI.create(s.baseUrl+"/").getPath();if(!URI.create(target).getPath().startsWith(rootPath))continue;
                if(e.getElementsByTagNameNS("DAV:","collection").getLength()>0){if(!target.endsWith("/"))target+="/";if(!visited.contains(target))pending.add(target);}
                else if(Protocol.audio(URI.create(target).getPath())){
                    String name=xmlValue(e,"displayname");if(name.trim().isEmpty())name=URI.create(target).getPath().substring(URI.create(target).getPath().lastIndexOf('/')+1);
                    result.add(track(s,target,name,"",s.name,target,0,0));
                }
            }progress.update(s.name+": "+result.size()+" аудіофайлів");
        }return result;
    }
    private static String xmlValue(Element e,String tag){NodeList list=e.getElementsByTagNameNS("DAV:",tag);return list.getLength()==0?"":list.item(0).getTextContent();}
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
        Source s=store.get(track.sourceId);if(s==null)throw new IOException("Джерело видалено");
        String url;
        switch(s.type){
            case SUBSONIC -> {return new Stream(Protocol.subsonic(s,"stream",Protocol.params("id",track.remoteId,"format","raw")),Map.of());}
            case JELLYFIN,EMBY -> url=s.baseUrl+"/Audio/"+Protocol.encode(track.remoteId)+"/stream?static=true";
            case PLEX -> {JSONObject item=get(s,"/library/metadata/"+Protocol.encode(track.remoteId)).getJSONObject("MediaContainer").getJSONArray("Metadata").getJSONObject(0);String path=item.getJSONArray("Media").getJSONObject(0).getJSONArray("Part").getJSONObject(0).getString("key");url=URI.create(s.baseUrl+"/").resolve(path).toString();}
            case KODI -> {JSONObject prepared=rpc(s,"Files.PrepareDownload",new JSONObject().put("path",track.uri));String path=prepared.getJSONObject("details").getString("path");url=s.baseUrl+"/"+path.replaceFirst("^/","");}
            case WEBDAV -> url=track.uri;
            case AUDIOBOOKSHELF -> url=URI.create(s.baseUrl+"/").resolve(track.uri).toString();
            default -> throw new IOException("Невідоме джерело");
        }
        if(!Protocol.sameOrigin(s.baseUrl,url))throw new IOException("Аудіофайл розміщено на іншому хості; авторизацію не передано");
        return new Stream(url,headers(s));
    }
}
