# Changelog

## Unreleased

## 0.6.7

- Restore open player, queue, equalizer, storage, listening-report and update screens across activity recreation, including scroll/list positions. Wait for the media controller and restored timeline before reopening playback views; never replay a destructive action.
- Let bottom-navigation labels use two lines and grow the bar with larger fonts, retaining full accessible names and selected-tab state.
- Give equalizer bands enough width for their gain labels and allow horizontal scrolling when they cannot fit. DSP, presets and stored gains are unchanged.
- Isolate catalog fault-fixture responses and request counts from late HTTP requests belonging to earlier cancelled cases.
- Record the Android 14 review of 0.6.6 and add Android regression coverage for native viewport state and large-font navigation. The new instrumentation tests require execution on a device/emulator.

## 0.6.6

- Add Subsonic now-playing notifications and qualified scrobbles, with optional OpenSubsonic playback timelines using client-controlled counting.
- Count actual active listening time, preserve session submission markers across restarts, and queue offline server listens with original timestamps.
- Bound unsent listens to 10,000 entries/90 days; add reporting controls, pending count, retry and queue clearing in all six UI languages.
- Add an additive SQLite v4 migration and local time/protocol/storage regression fixtures. Local files and radio are not reported.

- Put all eight library sections in one horizontally scrolling row, including the playlists page, and reveal the selected tab automatically.
- Combine scanned and manually added music under a Local files source filter, preserving stored source identities and server/offline distinctions.
- Remove the redundant global overflow menu and the appearance-screen column selector; keep the grid switch in the library and honor it in both orientations.
- Add distinct sources, language, appearance and grid icons, selected-filter feedback, and restore the source filter after activity recreation.

## 0.6.5

- Add Settings → Storage with private-data usage, device free space, offline totals and separate cache/temporary-file maintenance.
- Add audio-cache limits and a next-track prefetch switch; defer active-cache cleanup and retain leased offline files.
- Add a durable download queue, progress, cancellation, retry and Wi-Fi-only remote downloads using the existing foreground service.
- Record size/SHA-256 for new offline copies, check downloads and offer damaged/missing tracks for re-download without deleting library entries.
- Add host storage/cancellation fixtures, isolated Android storage tests and storage UI translations in all six languages.

## 0.6.4

- Fix Media3 artist metadata localization for CharSequence values.

- Add English, Ukrainian, Polish, German, French and Spanish UI resources, localized quantities and equalizer preset labels.
- Follow supported device languages with an English fallback, and add a persistent language selector using Android 13 per-app language settings where available. Keep navigation and preset storage keys unchanged.

- Add in-app stable GitHub release checks with daily foreground scheduling, an opt-out, persisted check times and one reminder per version.
- Add a themed Updates screen, literal release notes, cancellable private-cache APK downloads and progress reporting.
- Verify APK size/hash, package/version/SDK and installed signing identity before read-only FileProvider handoff to Android’s user-confirmed installer.
- Reuse the existing release JSON schema and add offline updater HTTP/security fixtures. No release signing key or release pipeline architecture is changed.

## 0.6.3

- Fix a fixture-only concurrency counter race exposed by GitHub runners; retain the three-request limit and 3000-album ordering checks.

- Package version 0.6.3 (versionCode 10) with the existing signing identity; music-player functionality is unchanged from 0.6.2.

- Remove the cache-specific Android checker, custom SDK bootstrap and redundant release wrapper; use SDK tools, Gradle and the release CLI directly.

- Add CI for main pushes and pull requests with Gradle caching, host tests, Android build/lint checks and debug artifacts.
- Prepare a gated tag-release pipeline with reviewed-main validation, increasing versionCode, isolated signing, verified APK assets and draft publication. Publication requires explicit activation and protected-environment approval.
- Rewrite repository documentation in English and document the current Subsonic/OpenSubsonic scope and limitations.

- Add local release automation with a pinned signing certificate, package/version/debuggable checks, ZIP alignment, signature verification and SHA-256/source manifests.
- Add encrypted signing-key backups and restore instructions; retain the existing release identity and keep passwords outside Git and backup exports.
- Add release-policy tests and reject signing configuration/password files in repository publication checks.

## 0.6.2

- Publish SELFonium-0.6.2.apk as a signed release build and update the direct installation link.

- Rename the application protocol field to `subsonic`; add debug-only discovery diagnostics that distinguish API, HTTP, network and malformed-response failures without URLs or credentials.
- Fetch albums in bounded batches of three, preserve server order and stop incomplete catalog snapshots from reaching the library. Keep catalog work separate from serialized favorite/playlist operations.
- Reject malformed catalog, favorite and playlist arrays instead of treating them as empty snapshots.
- Preserve pending favorite actions, favorites, playlist entries and downloads when pruning missing catalog tracks.
- Save timeline/index/position together, checkpoint pauses and seeks, and reset position when restoring a queue whose selected track was removed.
- Show a retryable play action after playback failure; retry preparation with one tap instead of requiring a pause first.
- Add a 3000-album concurrent HTTP fixture, safe-diagnostic failures, queue-remapping checks and collection-preserving prune tests.

## 0.6.1

Development milestone; no separate public APK release.

- Focus remote sources on Subsonic/OpenSubsonic, with Navidrome as the primary tested server; remove Jellyfin, Emby, Plex, WebDAV, Kodi and Audiobookshelf adapters and connection choices.
- Rename the protocol implementation to SubsonicClient; discover optional OpenSubsonic extension versions without requiring them for baseline library synchronization.
- Retain retired source records encrypted across saves, and preserve existing library, favorites, playlists, queue and downloads.
- Simplify connection setup to one Subsonic form with optional display name.
- Distribute spare portrait player height between controls; retain scrolling for small displays and two columns in landscape.
- Inset queue artwork inside rounded row highlights and remove the competing ListView selector.

## 0.6.0

Development milestone; no separate public APK release.

- Reuse matching playback queues; move queue serialization, restore and checkpoints off the UI thread.
- Cache track/favorite/source lookups, reuse and coalesce player artwork, avoid repainting the obscured library, prioritize active covers and skip canceled requests.
- Add a bounded 192 MB audio cache and cancelable prefetch of the next track's first 512 KB; lower startup buffering threshold and show buffering state.
- Import Subsonic/Navidrome track stars and server playlists; send stars/unstar changes through a durable, revision-checked outbox.
- Create, rename, add/remove tracks and delete owned server playlists; keep local playlists and read-only shared playlists distinct.
- Preserve server playlist order and duplicate entries with SQLite v3 migration; protect pending likes from incoming server snapshots.
- Add real HTTP provider fixtures and migration/outbox tests; log playback state, readiness and underruns without track URLs or credentials.

## 0.5.1

- Initial public APK release as SELFonium-0.5.1.apk, with a direct download and installation steps in the README.
- Refresh the project README with Resonance Mint branding, a feature overview, server support status and four screenshots from Android 0.5.1.
- Adopt the Resonance Mint launcher icon with an adaptive Android mask and safe margins.
- Replace the narrow source alert with a full-screen, keyboard-aware editor: full-width fields, persistent labels, protocol help and password visibility.
- Fix Jellyfin authentication by sending client identity and the session token in the Authorization header; retain Emby compatibility.
- Distinguish DNS, TLS, connection, timeout and HTTP failures in source synchronization messages.

## 0.5.0

- Fixed clipped library navigation: three equal tabs and a visible More menu replace horizontally scrolling category buttons.
- Inset track artwork and controls within the rounded touch highlight.
- Appearance settings: accent palette/custom HEX color, AMOLED black background and cover-grid columns, with a preview before applying.
- Independent ten-band PCM equalizer with Preamp, 18 original presets, saved user presets and automatic headroom.
- Classic fader controls, precise gain input and a calculated response graph; legacy EQ settings migrate once.
- Added measured DSP tests for gain response, stereo isolation, preamp, headroom, bypass, reset and migration.

## 0.4.0

- Music-first library with direct album/artist/track tabs, compact shortcuts and a persistent two/three-column choice.
- Distinct album and artist pages, scrolling artwork headers, numbered album tracks and artist discography.
- Full-screen live queue with artwork, artist names, explicit removal and move-up/move-down actions.
- Graphite surfaces, consistent typography, compact home actions and grouped settings/track actions.
- Search prompt, clear button and five recent queries; Ukrainian count forms and album placeholders.
- Artwork-tinted player/detail backgrounds, adaptive player artwork, and a mini player shown only after selecting music.
- Compact landscape library cards and controls, with a two-column landscape player.
- Equalizer graph scales, active/custom preset indication and explicit reset.

- Trust system and user-installed Android CAs for HTTPS home servers while retaining certificate and hostname verification.
- Repository documentation and MIT license.
- Removed the GitHub build workflow and its Dependabot configuration during active development; builds and checks run locally.

## 0.3.0

- Direct track-row playback and separate overflow action targets.
- Refreshed navigation, library categories, search and settings.
- Full-screen player/equalizer, dark dialogs and track action sheet.
- Current-track highlighting and consistent album ordering.
- Android instrumentation coverage for row interaction regressions.

## 0.2.0

- Subsonic/Navidrome artwork and bounded image caching.
- Library migration preserving favorites, playlists, history and downloads.
- Album grid, artwork in the mini player and revised playback controls.
- Equalizer frequency/gain display, graph and presets.

## 0.1.0

- Initial Android player, local library, remote source adapters, playback service,
  queue, favorites, playlists, offline downloads and system equalizer.
