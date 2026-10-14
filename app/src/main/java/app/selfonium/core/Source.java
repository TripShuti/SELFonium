package app.selfonium.core;

public final class Source {
    public enum Type { SUBSONIC }
    public String id, name, baseUrl, username, password;
    public Type type;
    public Source(String id, String name, Type type, String baseUrl, String username, String password) {
        this.id = id; this.name = name; this.type = type; this.baseUrl = Protocol.base(baseUrl);
        this.username = username; this.password = password;
    }
}
