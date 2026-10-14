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
| Optional discovery | `getOpenSubsonicExtensions` |

OpenSubsonic discovery runs only when the server advertises support. Discovery errors fall back to baseline behavior. Discovery does not enable lyrics, API-key authentication or other unimplemented extensions. Debug-only `SELFoniumSubsonic` messages distinguish API/HTTP/network/response errors without source URLs or credentials.

## Catalog

Full synchronization reads album-list pages of 500 and fetches album contents in batches of at most three concurrent requests. Results retain server order. A failed album, repeated page, missing required container or incorrectly typed array aborts the snapshot before it reaches the library. A valid empty collection is accepted.

Catalog networking uses a separate executor from serialized favorite/playlist operations. Pruning retains downloads, favorited tracks, playlist entries and tracks with pending favorite actions. Retaining an entry does not make a deleted server file remotely playable.

There is no incremental catalog synchronization. A newest-albums list alone would miss removals and edits; an incremental design needs a separate change-detection strategy.

## Favorites

Tapping a favorite updates the UI immediately. SQLite persists the desired value and revision in `favorite_outbox`; repeated actions on a track coalesce. A successful reply removes only the matching revision/value, so an older request cannot acknowledge a newer action.

Pending changes survive process restarts and are retried on app startup, connectivity restoration and after failed sends. Incoming snapshots exclude pending local actions. Foreground refresh is throttled to about one minute. On the first social synchronization, existing local track favorites for the source are seeded to the server before pulling its state.

Only track stars are synchronized. Ratings, album/artist stars and scrobbling are absent.

## Playlists

Remote identity is `(source, remote_id)`; local playlists remain independent. Imported server order and duplicate entries are retained. Known other-owner playlists are read-only.

Owned lists can be created, renamed, edited and deleted while online. Adding a track already present does not add another duplicate. Tracks must belong to the same source. Local state changes after a successful server response; most edits read back the resulting list. Creation uses the returned playlist payload and requires its ID.

Removal checks a freshly fetched index and track ID. The API has no version-conditional edit, so concurrent changes between read and write cannot be fully prevented. After an ambiguous creation failure, refresh before trying again to avoid creating a duplicate. There is no durable offline queue of playlist mutations.

A successful list snapshot removes missing remote mappings while preserving independent local lists. SQLite migration from v2 to v3 preserves library state and changes playlist membership keys to support ordered repeats.

## Artwork, audio cache and recovery

Cover identifiers are opaque. A missing cover ID can be resolved with `getSong`; it is not inferred from the song ID. Artwork has a 32 MB memory cache and 96 MB disk limit, with priority for the active cover.

Remote audio uses a 192 MB LRU cache and cancelable prefetch of up to 512 KB from the next track. Local files, deliberate offline copies and radio/HLS bypass this cache. Stable cache keys exclude random authentication salts, and the resolver reports the original internal URI rather than persisting an authenticated stream URL as a cache redirect.

Queue/index/position are saved together when the timeline changes, with additional position checkpoints on pause, seek, transitions and every five seconds. A force-stop can lose an unwritten checkpoint. Missing selected tracks are remapped to a surviving entry at position zero; startup does not autoplay.

After a terminal stream error, one Play action prepares a new attempt. Restored connectivity does not automatically start playback. Device checks must wait for the intended network to be ready; enabling the Wi-Fi radio alone does not establish DNS/server reachability.

Protocol references: [OpenSubsonic endpoints](https://opensubsonic.netlify.app/docs/), [extension discovery](https://opensubsonic.netlify.app/docs/endpoints/getopensubsonicextensions/), [playlist updates](https://opensubsonic.netlify.app/docs/endpoints/updateplaylist/).
