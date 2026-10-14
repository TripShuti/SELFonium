# Features and limitations

## Sources

| Source | Implemented | Limits |
|---|---|---|
| Subsonic / OpenSubsonic | Token authentication, album/track catalog, raw streaming, covers, track favorites and server playlists | Navidrome is the primary device-tested server; other implementations are best-effort |
| Local audio | Android MediaStore scan, document-picker import, embedded artwork | No recursive folder scanner |
| Radio | Direct HTTP(S) audio streams and `.m3u8` HLS URLs | No station directory or M3U/PLS playlist resolution |

Only Subsonic/OpenSubsonic is supported as a remote library API. OpenSubsonic extension names and versions are discovered when advertised, but optional extension features are not implemented merely because discovery succeeds. API-key authentication, scrobbling, ratings, album/artist stars and lyrics are not implemented. [SYNCHRONIZATION.md](SYNCHRONIZATION.md) lists the endpoints and behavior.

Retired source records remain encrypted and hidden after upgrades. Their stored library entries and downloaded files are retained; their removed online connectors cannot play remote files.

## Library and playback

Browse albums, artists and tracks; search locally, filter sources and view favorites, history, playlists and offline files. Search keeps up to five confirmed queries. Album pages have ordered tracks; artist pages group albums. Artist artwork uses an album cover or placeholder, not a downloaded artist photograph.

The queue supports selection, removal and move-up/move-down actions. Shuffle, repeat, playback speed, media notifications, lock-screen controls and a sleep timer are available. Queue/index/position are restored paused after restart. Drag-and-drop reordering and crossfade are not implemented.

Server favorites sync both ways and retain pending changes offline. Server playlists preserve ordering and repeated entries; creating, renaming, editing and deleting owned lists requires connectivity. Local playlists are separate.

Offline downloads save whole files before marking them available. There is no download resume, automatic download policy or offline-file quota. The 192 MB evictable audio cache and next-track prefetch are separate from deliberate downloads. Android can remove cached data.

## Interface and audio

The UI is currently Ukrainian. Appearance settings provide accent colors, a custom HEX accent, an AMOLED background and two/three-column cover grids. Portrait and landscape players use adaptive layouts.

The [graphic equalizer](EQUALIZER.md) has ten fixed bands, preamp, 18 built-in presets, saved presets and automatic headroom. It processes this app's PCM16 output. PEQ, AutoEQ import, system-wide equalization, encoded passthrough, audio offload and bit-perfect output are not available.

## Network and storage

HTTPS trusts Android system and user-installed CAs while retaining hostname, chain and validity checks. For a home server using a private CA, install that CA in Android and use a hostname included in the server certificate. Per-source certificate import is not implemented. HTTP is accepted; use HTTPS over untrusted networks.

Source credentials are encrypted in private storage with Android Keystore. Automatic app backup is disabled; credential export is not implemented. Authenticated cross-origin redirects are rejected.

Android Auto, Cast, UPnP/DLNA, SMB, cloud OAuth and widgets are not implemented. See the [roadmap](ROADMAP.md) for planned work; it is not a support or delivery guarantee.
