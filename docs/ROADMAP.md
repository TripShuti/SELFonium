# Roadmap

## Implemented

- Subsonic/OpenSubsonic catalog playback, track favorites and server playlists, with Navidrome as the primary tested server.
- Bounded parallel catalog requests, audio/artwork caching, durable favorite retries and queue recovery.
- Host-side protocol/DSP/storage tests, including a 3000-album fixture; selected network/restart scenarios on Android 14.
- Portrait/landscape player layouts and appearance settings.
- Signed GitHub releases 0.5.1 and 0.6.2 using the same certificate.
- Local signing/backup tooling and prepared CI/CD workflows. Automated publication requires explicit activation and environment approval; hosted workflow results are tracked in GitHub Actions.

## Development branch

- In-app GitHub updater implemented with host fixtures; Android permission, installer and lifecycle checks remain pending. See [UPDATES.md](UPDATES.md).

## Next

- Exercise more lifecycle, audio-focus, Bluetooth and network interruption cases, including the minimum supported Android version.
- Continue Navidrome device testing and expand protocol fixtures for optional capabilities and server errors.
- Audit TalkBack accessibility, move UI strings into localization resources and test more screen sizes.
- Review branch/tag protections and verify the first protected GitHub release run.
- Move the encrypted signing backup off the development disk and store its password separately when another destination is available.

## Later

- Incremental synchronization with protocol-compatible change/removal detection.
- Playlist file import/export, richer reordering and offline server-playlist editing.
- Resumable downloads, storage quotas and more offline policies.
- Lyrics through supported optional APIs.
- Android Auto, widgets and casting/renderer support.
- A separate certificate-migration plan if store distribution is introduced.

These are development directions, not delivery dates or guarantees of server compatibility.
