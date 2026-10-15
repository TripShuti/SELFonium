# Roadmap

## Implemented

- Subsonic/OpenSubsonic catalog playback, track favorites and server playlists, with Navidrome as the primary tested server.
- Bounded parallel catalog requests, audio/artwork caching, durable favorite retries and queue recovery.
- Host-side protocol/DSP/storage tests, including a 3000-album fixture; selected network/restart scenarios on Android 14.
- Portrait/landscape player layouts and appearance settings.
- Signed GitHub releases through 0.6.6 using the same certificate.
- Local signing/backup tooling and working protected GitHub release automation, including successful 0.6.3 through 0.6.6 runs.
- In-app GitHub updates and six UI languages, published in 0.6.4. The maintainer confirmed successful in-app discovery, download and installation on the test phone; broader updater and language coverage remains separate.
- Storage overview, configurable audio cache/prefetch, offline maintenance and a durable download queue, published in 0.6.5. Selected storage overview and waiting-queue/restart/cancellation scenarios were checked on Android 14; destructive and active-transfer cases remain pending. See [FEATURES.md](FEATURES.md#storage-management).

## Version 0.6.6

- One-row library navigation, combined Local files filtering, removal of duplicate controls and distinct settings/source icons. Host checks and an Android 14 UI review are recorded separately from instrumentation execution.
- Server now-playing reports and qualified listens with a bounded durable offline queue, optional OpenSubsonic timeline reports and user controls. An Android 14 offline-listen queue/retry check complements host fixtures; external server/scrobbler receipt remains unverified.

## Next

- Preserve open player/settings surfaces across rotation and improve large-font navigation/equalizer label spacing.
- Extend 0.6.6 navigation/listening-report device coverage and verify server-side receipt on Navidrome; keep host and device evidence separate.
- Extend the existing network/restart and screen/font checks with lifecycle, audio-focus, Bluetooth and minimum-Android regression coverage.
- Continue Navidrome device testing and expand protocol fixtures for optional capabilities and server errors.
- Audit TalkBack accessibility, proofread translations and test more screen sizes.
- Review branch/tag protections periodically.
- Exercise remaining updater failure/permission/lifecycle cases and all language overrides on Android.

## Possible later work

- Incremental synchronization with protocol-compatible change/removal detection.
- Playlist file import/export, richer reordering and offline server-playlist editing.
- Resumable downloads, offline storage quotas and automatic playlist-download policies.
- Lyrics through supported optional APIs, Android Auto, widgets and casting/renderer support, if user demand justifies them.
- A separate certificate-migration plan if store distribution is introduced.

Auto DJ is deferred: a useful offline similarity source has not been chosen, and adding an on-device model is not currently planned. Improving existing playback, synchronization and storage takes priority over adding integrations.

These are development directions, not delivery dates or guarantees of server compatibility.
