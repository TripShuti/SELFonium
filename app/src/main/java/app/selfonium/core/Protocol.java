package app.selfonium.core;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Protocol {
    private Protocol() {}
    public static String base(String value) {
        URI uri;
        try { uri = URI.create(value.trim()); } catch (RuntimeException e) { throw new IllegalArgumentException("Некоректна адреса сервера"); }
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Вкажіть http(s)://сервер[:порт][/шлях] без пароля в адресі");
        String normalized = uri.toASCIIString();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
    public static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public static String query(String url, Map<String, String> params) {
        StringBuilder result = new StringBuilder(url);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result.append(result.indexOf("?") < 0 ? '?' : '&').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return result.toString();
    }
    public static Map<String, String> params(String... values) {
        if (values.length % 2 != 0) throw new IllegalArgumentException("Parameter pairs required");
        Map<String, String> map = new LinkedHashMap<>();
        for (int i=0;i<values.length;i+=2) map.put(values[i],values[i+1]);
        return map;
    }
    public static String md5(String value) {
        try { return hex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    public static String subsonic(Source source, String method, Map<String, String> args, String salt) {
        Map<String, String> params = params("u", source.username, "t", md5(source.password + salt), "s", salt,
                "v", "1.16.1", "c", "SELFonium", "f", "json");
        params.putAll(args);
        return query(source.baseUrl + "/rest/" + method + ".view", params);
    }
    public static String subsonic(Source source, String method, Map<String, String> args) {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return subsonic(source, method, args, hex(salt));
    }
    public static boolean sameOrigin(String first, String second) {
        URI a=URI.create(first), b=URI.create(second);
        return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost()!=null && a.getHost().equalsIgnoreCase(b.getHost()) && port(a)==port(b);
    }
    private static int port(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80; }
    public static boolean audio(String path) {
        return path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp3|flac|m4a|m4b|ogg|opus|wav|aac|aiff|aif|alac|wma|ape|dsf|dff)$");
    }
    public static String time(long ms) {
        long seconds=Math.max(0,ms)/1000;
        return seconds>=3600 ? String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
                : String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
}
