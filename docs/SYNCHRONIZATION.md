# Subsonic synchronization and playback state

## Compatibility and endpoints

The client uses the Subsonic protocol, not Navidrome-specific APIs. Requests advertise API version 1.16.1 and JSON responses, with username, random salt and an MD5 password-plus-salt token. Navidrome is the primary tested server. Other implementations must support the endpoints used below and remain best-effort.

| Purpose | Endpoints |
|---|---|
| Connection and catalog | `ping`, `getAlbumList2`, `getAlbum` |
| Cover lookup and image | `getSong` when needed, `getCoverArt` |
| Playback/download | `stream` with `format=raw` |
| Track favorites | `getStarred2`, `star`, `unstar` |
| Server playlists | `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist` |
| Listening reports (0.6.6) | `scrobble`; optional `reportPlayback` |
| Optional discovery | `getOpenSubsonicExtensions` |

OpenSubsonic discovery runs only when the server advertises support. Discovery errors fall back to baseline behavior. Discovery does not enable lyrics, API-key authentication or other unimplemented extensions. Debug-only `SELFoniumSubsonic` messages distinguish API/HTTP/network/response errors without source URLs or credentials.

## Catalog

Full synchronization reads album-list pages of 500 and fetches album contents in batches of at most three concurrent requests. Results retain server order. A failed album, repeated page, missing required container or incorrectly typed array aborts the snapshot before it reaches the library. A valid empty collection is accepted.

Catalog networking uses a separate executor from serialized favorite/playlist operations. Pruning retains downloads, favorited tracks, playlist entries and tracks with pending favorite actions. Retaining an entry does not make a deleted server file remotely playable.

There is no incremental catalog synchronization. A newest-albums list alone would miss removals and edits; an incremental design needs a separate change-detection strategy.

## Favorites

Tapping a favorite updates the UI immediately. SQLite persists the desired value and revision in `favorite_outbox`; repeated actions on a track coalesce. A successful reply removes only the matching revision/value, so an older request cannot acknowledge a newer action.

Pending changes survive process restarts and are retried on app startup, connectivity restoration and after failed sends. Incoming snapshots exclude pending local actions. Foreground refresh is throttled to about one minute. On the first social synchronization, existing local track favorites for the source are seeded to the server before pulling its state.

Only track stars are synchronized. Ratings and album/artist stars are absent.

## Playlists

Remote identity is `(source, remote_id)`; local playlists remain independent. Imported server order and duplicate entries are retained. Known other-owner playlists are read-only.

Owned lists can be created, renamed, edited and deleted while online. Adding a track already present does not add another duplicate. Tracks must belong to the same source. Local state changes after a successful server response; most edits read back the resulting list. Creation uses the returned playlist payload and requires its ID.

Removal checks a freshly fetched index and track ID. The API has no version-conditional edit, so concurrent changes between read and write cannot be fully prevented. After an ambiguous creation failure, refresh before trying again to avoid creating a duplicate. There is no durable offline queue of playlist mutations.

A successful list snapshot removes missing remote mappings while preserving independent local lists. SQLite migration from v2 to v3 preserves library state and changes playlist membership keys to support ordered repeats.

## Listening reports (0.6.6)

Listening reports require 0.6.6 or later. Settings → Listening reports enables server reporting by default and shows pending listens, manual retry and queue clearing. Disabling reporting cancels requests and clears unsent records and the listening checkpoint; it does not delete local history or previously submitted server records. Requests go only to the configured Subsonic source. The server's own scrobbler handles external services, with no Last.fm/ListenBrainz credentials or SDK in SELFonium.

A session accumulates monotonic elapsed time while Media3 reports active playback. Pauses, buffering and seek jumps do not add listening time; playback speed does not multiply that clock. A listen qualifies after half the known duration or four minutes, whichever is earlier. Unknown-duration media is not submitted until duration is known. A repeat or replay after an ended item starts a new session; pause/resume and seeking within the same item retain the session. Downloaded/cached server tracks retain server IDs and use the same policy; local files and radio are excluded.

The client uses `scrobble` with `submission=false` for basic now-playing updates and `submission=true` for qualified listens, preserving the original first-playing Unix timestamp in milliseconds for submissions. Basic Subsonic has no precise pause/stop/position report. If discovery advertises OpenSubsonic `playbackReport` version 1, the client sends starting/playing/paused/stopped state, position and rate through `reportPlayback`, with `ignoreScrobble=true` to leave counting to the explicit submission. State/seek/rate changes and roughly 30-second samples update live state. An unsupported advertised endpoint falls back to baseline behavior. Only the newest live state is retained, and reports older than 90 seconds are ignored; historical now-playing states are never replayed from disk.

SQLite schema v4 adds a bounded `scrobble_outbox` and one `listening_checkpoint` without rewriting library data. Qualifying a session and recording its submitted marker are one transaction. Acknowledged events are deleted; the marker prevents a later checkpoint or restart from submitting the same session again. Checkpoints occur approximately every five seconds and on playback events. Process death can lose the latest uncommitted seconds; restoring a queue does not count downtime or autoplay.

The pending queue retains at most 10,000 entries for up to 90 days, pruning expired/oldest records when active. It stores IDs, timestamps, retry fields and a hash of the source URL/account, not audio, covers or passwords. SQLite reuses freed pages; the database file need not shrink after every acknowledgement. Deleting a source clears its queued listens; changing its URL/account prevents delivery under a different identity. Catalog pruning does not delete pending listens. Missing server tracks are discarded after the server's not-found response.

A dedicated network worker submits batches of up to 25, independently of playback, artwork, catalog and playlist work. Failures use bounded backoff (30 seconds up to 15 minutes; authentication/authorization failures wait longer). App foreground entry, connectivity restoration and in-process retries trigger delivery; manual retry resets pending backoff and is throttled. No new background service or guaranteed delivery after process termination is introduced. The next app/playback session can resume delivery. External services may reject records older than their own limits.

Local session deduplication does not provide exactly-once delivery over an ambiguous network failure: if a server accepts a request but its response is lost, a retry can duplicate it on servers without deduplication. No Subsonic idempotency key is assumed.

Protocol references: [scrobble](https://opensubsonic.netlify.app/docs/endpoints/scrobble/), [playbackReport extension](https://opensubsonic.netlify.app/docs/extensions/playbackreport/), [reportPlayback](https://opensubsonic.netlify.app/docs/endpoints/reportplayback/).

## Artwork, audio cache and recovery

Cover identifiers are opaque. A missing cover ID can be resolved with `getSong`; it is not inferred from the song ID. Artwork has a 32 MiB memory cache and 96 MiB disk limit, with priority for the active cover.

Remote audio defaults to a configurable 192 MiB LRU cache and optional cancelable prefetch of up to 512 KiB from the next track. Local files, deliberate offline copies and radio/HLS bypass this cache. Stable cache keys exclude random authentication salts, and the resolver reports the original internal URI rather than persisting an authenticated stream URL as a cache redirect.

Queue/index/position are saved together when the timeline changes, with additional position checkpoints on pause, seek, transitions and every five seconds. A force-stop can lose an unwritten checkpoint. Missing selected tracks are remapped to a surviving entry at position zero; startup does not autoplay.

After a terminal stream error, one Play action prepares a new attempt. Restored connectivity does not automatically start playback. Device checks must wait for the intended network to be ready; enabling the Wi-Fi radio alone does not establish DNS/server reachability.

Protocol references: [OpenSubsonic endpoints](https://opensubsonic.netlify.app/docs/), [extension discovery](https://opensubsonic.netlify.app/docs/endpoints/getopensubsonicextensions/), [playlist updates](https://opensubsonic.netlify.app/docs/endpoints/updateplaylist/).
