package app.selfonium.core;

import java.util.Objects;

/** Stable identity never contains credentials or short-lived stream tokens. */
public final class Track {
    public final String id, sourceId, remoteId, title, artist, album, uri, artwork;
    public final long durationMs;
    public final int number;
    public Track(String id, String sourceId, String remoteId, String title, String artist,
                 String album, String uri, long durationMs, int number) {
        this(id, sourceId, remoteId, title, artist, album, uri, durationMs, number, "");
    }
    public Track(String id, String sourceId, String remoteId, String title, String artist,
                 String album, String uri, long durationMs, int number, String artwork) {
        this.id = Objects.requireNonNull(id);
        this.sourceId = sourceId;
        this.remoteId = remoteId;
        this.title = title == null || title.trim().isEmpty() ? "Без назви" : title;
        this.artist = artist == null || artist.trim().isEmpty() ? "Невідомий виконавець" : artist;
        this.album = album == null || album.trim().isEmpty() ? "Без альбому" : album;
        this.uri = uri == null ? "" : uri;
        this.durationMs = Math.max(0, durationMs);
        this.number = Math.max(0, number);
        this.artwork = artwork == null ? "" : artwork;
    }
    public Track withArtwork(String value) {
        return new Track(id, sourceId, remoteId, title, artist, album, uri, durationMs, number, value);
    }
}
