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

Offline downloads save whole files before marking them available. Streaming cache and next-track prefetch are separate from deliberate downloads. Android can remove cached data.

## Storage management (development)

Settings → Storage shows downloaded music and track count, streaming audio, artwork, database, temporary/update files, other private app data and device free space. The overview measures private data, not the installed APK size or other apps. Measurements run on a separate worker and refresh while the screen is open. The Offline tab also shows the size of the listed tracks.

The audio cache defaults to 192 MiB; available limits are disabled, 64 MiB, 192 MiB, 512 MiB, 1 GiB and 2 GiB. Next-track audio prefetch can be switched off independently. Changes apply to newly opened streams. Lowering the limit or clearing an actively used cache waits until its streams close. Cache clearing and eviction do not remove explicit offline downloads. The artwork disk cache retains its existing 96 MiB limit; clearing it also clears in-memory entries and prevents older requests from repopulating the cleared cache.

The download queue supports progress with bytes/percentages, individual or batch cancellation, retry, and a bounded completed/cancelled history. Pending requests survive restarts; a transfer interrupted by process death is marked for retry, unless its finished file was already committed. Repeated requests for the same track are deduplicated. Failed downloads do not loop automatically. Wi-Fi-only mode applies to remote offline downloads, not playback or local file copies. Waiting downloads are started when the app is in the foreground and the connection permits it; ongoing transfers use the existing foreground service. Waiting entries do not keep a service running. Downloads restart from the beginning rather than resume a partial file.

Deleting offline copies preserves server/original local files, library records, favorites and playlists. Files leased by playback are retained; the screen reports retained copies so they can be removed later. Cache/maintenance/delete-all actions show selected sizes and require confirmation. The file checker validates SHA-256 and byte length recorded for new downloads. Legacy downloads receive existence and nonzero-size checks, not a claim of cryptographic verification. Missing/damaged offline references can be removed and offered for re-download without deleting library entries.

Temporary cleanup only removes managed abandoned partial/orphan downloads and stale update files. Active transfers and update APKs ready for installation are retained. Downloads reserve 8 MiB of free space, verify known response lengths and publish a completed file before marking it offline. Storage remains private and internal; uninstalling the app removes these files. SD-card selection, offline storage quotas and automatic playlist-download policies are not implemented.

Storage management is not included in the published 0.6.4 APK. See [testing](TESTING.md#storage-management) for validation scope.

## Interface and audio

Version 0.6.4 includes English, Ukrainian, Polish, German, French and Spanish. The app follows the device language by default (English fallback); Settings → Language selects an override. Android 13 and later also expose these languages in system app settings. Appearance settings provide accent colors, a custom HEX accent, an AMOLED background and two/three-column cover grids. Portrait and landscape players use adaptive layouts.

The [graphic equalizer](EQUALIZER.md) has ten fixed bands, preamp, 18 built-in presets, saved presets and automatic headroom. It processes this app's PCM16 output. PEQ, AutoEQ import, system-wide equalization, encoded passthrough, audio offload and bit-perfect output are not available.

## Network and storage

HTTPS trusts Android system and user-installed CAs while retaining hostname, chain and validity checks. For a home server using a private CA, install that CA in Android and use a hostname included in the server certificate. Per-source certificate import is not implemented. HTTP is accepted; use HTTPS over untrusted networks.

Source credentials are encrypted in private storage with Android Keystore. Automatic app backup is disabled; credential export is not implemented. Authenticated cross-origin redirects are rejected.

Android Auto, Cast, UPnP/DLNA, SMB, cloud OAuth and widgets are not implemented. See the [roadmap](ROADMAP.md) for planned work; it is not a support or delivery guarantee.

## Application updates

Version 0.6.4 includes stable-release checks, private APK downloads and verified system-installer handoff. See [UPDATES.md](UPDATES.md) for behavior, security, metadata and Android limitations.
