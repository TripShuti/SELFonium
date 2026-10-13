package app.selfonium.core;

public final class Source {
    public enum Type { SUBSONIC, JELLYFIN, EMBY, PLEX, WEBDAV, KODI, AUDIOBOOKSHELF }
    public String id, name, baseUrl, username, password, token, userId;
    public Type type;
    public Source(String id, String name, Type type, String baseUrl, String username, String password) {
        this.id = id; this.name = name; this.type = type; this.baseUrl = Protocol.base(baseUrl);
        this.username = username; this.password = password; this.token = ""; this.userId = "";
    }
}
