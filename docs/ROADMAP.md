# Roadmap

## Implemented

- Subsonic/OpenSubsonic catalog playback, track favorites and server playlists, with Navidrome as the primary tested server.
- Bounded parallel catalog requests, audio/artwork caching, durable favorite retries and queue recovery.
- Host-side protocol/DSP/storage tests, including a 3000-album fixture; selected network/restart scenarios on Android 14.
- Portrait/landscape player layouts and appearance settings.
- Signed GitHub releases through 0.6.4 using the same certificate.
- Local signing/backup tooling and working protected GitHub release automation, including successful 0.6.3 and 0.6.4 runs.
- In-app GitHub updates and six UI languages, published in 0.6.4; new updater/language device scenarios remain separate validation work.

## Development branch

- Storage overview, configurable audio cache/prefetch, offline maintenance and a durable download queue with Wi-Fi policy, cancellation and retry. Host checks and Java/resource compilation have been performed; Android storage-screen and foreground-service scenarios remain pending. See [FEATURES.md](FEATURES.md#storage-management-development).

## Next

- Exercise more lifecycle, audio-focus, Bluetooth and network interruption cases, including the minimum supported Android version.
- Continue Navidrome device testing and expand protocol fixtures for optional capabilities and server errors.
- Audit TalkBack accessibility, proofread translations and test more screen sizes.
- Review branch/tag protections periodically.
- Verify an in-app update from 0.6.4 to a newer release and exercise all language overrides on Android.
- Move the encrypted signing backup off the development disk and store its password separately when another destination is available.

## Later

- Incremental synchronization with protocol-compatible change/removal detection.
- Playlist file import/export, richer reordering and offline server-playlist editing.
- Resumable downloads, offline storage quotas and automatic playlist-download policies.
- Lyrics through supported optional APIs.
- Android Auto, widgets and casting/renderer support.
- A separate certificate-migration plan if store distribution is introduced.

These are development directions, not delivery dates or guarantees of server compatibility.
