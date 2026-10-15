# Testing and validation

Run commands from the repository root. Tests are grouped by what they actually verify; successful compilation is not a runtime result.

## Host-side checks

| Command | Scope |
|---|---|
| `bash scripts/test-core.sh` | Queue/listening/source-filter logic, measured DSP, storage, localization, Java syntax, XML/manifest and SQLite migrations |
| `python3 scripts/test-providers.py` | Production Subsonic, listening-report and updater HTTP/JSON clients against isolated fixtures; requires Gradle dependency caches |
| `python3 scripts/test-release.py` | Signing/release policy, tag/version/ancestry checks, artifact integrity and notes |
| `python3 scripts/test-ci-signing.py` | Fake CI secrets, private file permissions, cleanup and error redaction |
| `bash scripts/check-workflows.sh` | Workflow syntax, contexts and expressions via checksum-pinned actionlint |
| `python3 scripts/check-repository.py` | Indexed-file publication policy and local Markdown paths, images and anchors |

Java syntax parsing does not resolve Android types. SQLite tests execute extracted SQL in desktop SQLite, not Android SQLiteOpenHelper. HTTP fixtures stub credential storage, not the production HTTP client. The 3000-album fixture checks ordering, pagination, bounded concurrency and failed snapshots; its localhost timings are not a remote-server benchmark.

## Android builds

`bash scripts/build.sh` runs core checks, real debug assembly, Android Lint and HTTP fixtures. CI additionally runs release/security tooling tests and workflow validation, without release secrets.

`python3 scripts/release.py build` runs release-tooling/core/HTTP checks, real release assembly/lint, signing and APK verification. Unsigned CI builds run the same build checks without loading a key; a separate protected job signs the verified artifact without invoking Gradle.

Lint reports are under `app/build/reports`. Existing builds have warnings; a successful lint task is not a claim of zero warnings.

## Device tests

```sh
bash gradlew --no-daemon :app:assembleDebugAndroidTest
bash gradlew --no-daemon :app:connectedDebugAndroidTest
```

The first command only compiles the instrumentation APK. The second needs an authorized compatible device/emulator and permission to install the test APK. `TrackRowTouchTest` covers row/cover/overflow callbacks, rebinding and gesture cancellation. `InstalledCaTrustTest` checks system and installed user CAs using the app's HTTP client. These tests do not validate every server or audio route.

Manual release checks should cover update installation with data retention, direct track selection, queue editing and recovery, play/pause/seek/shuffle/repeat, offline files, source synchronization and loss/restoration of connectivity. Wait for the intended network and DNS to be ready before judging reconnect behavior. Inspect logs for crashes and underruns without sharing credentials or full authenticated URLs.

## Recorded scope and limits

Earlier 0.6.2 development checks on Android 14 covered Navidrome catalog synchronization, downloaded playback with Wi-Fi disabled, durable offline favorite changes across force-stop, queue/index/position restoration, failed offline playlist creation and one-tap stream retry after reconnection. They do not establish universal Bluetooth stability or full Subsonic compatibility.

GitHub-hosted CI and protected release publication have run successfully for 0.6.3, 0.6.4 and 0.6.5. Official releases retain the existing signing identity. Each future tag must still pass the workflow checks and signing approval; a previous successful run does not validate unmerged development code.

The maintainer confirmed in-app update discovery, download and installation on the test phone on 2026-10-15. Selected screen/font checks were also performed during earlier UI work. These results do not cover every language, device size, Android version or failure path.

The unreleased navigation and listening-report changes passed host fixtures and separate Java/resource compilation checks. They have not had an APK build, Android Lint run, instrumentation execution or device/server integration test. Version 0.6.5's hosted build covers the released storage implementation, not these later changes.

Updater-specific fixtures and the required Android permission/install/lifecycle checks are described in [UPDATES.md](UPDATES.md#validation). Host fixtures are not evidence of successful Android APK verification or installation.

## Localization

Run `bash scripts/test-core.sh` for language selection and resource checks, including all six language sets, format placeholders, plural categories and stable preset/navigation keys. The checks are offline and are also part of the existing CI build. They do not build an APK or emulate Android resource selection.

On Android 8–12 and Android 13+, check the device-language default, unsupported-language English fallback, explicit overrides and returning to the system language. On Android 13+, also change the language in system app settings. Restart the app and confirm the choice persists. Switch languages during playback; verify music continues, sources and favorites remain intact, and custom playlist/preset names stay unchanged. Check navigation, player, source forms, update dates, notification labels and equalizer dialogs at large font sizes in each language. Review 0/1/2/5/11/21 item counts, truncated labels and accent colors. The full language/version matrix remains pending; earlier general screen/font checks do not cover it.

`app.selfonium.locale.LocalizationTest` adds instrumentation checks for Android resource selection, Ukrainian/Polish quantity rules, formatting and literal templates. It does not modify language preferences or playback. These instrumentation sources were subsequently compiled against cached Android dependencies with substituted resource IDs, but have not been executed on Android.

## Storage management

`bash scripts/test-core.sh` runs real-file host checks for managed directories, outside/symlink protection, multiple readers, alias-safe leases, atomic publication, SHA-256/size validation, cancellation, low-space rejection and interrupted streams. `python3 scripts/test-providers.py` also cancels the production OkHttp request before execution and during a local fixture's response-header wait. No test uses a personal server.

For the storage change, production and instrumentation Java sources were compiled against the cached Android 35 SDK and dependency classes, substituting generated resource IDs. AAPT2 resource compilation was also checked separately. Neither check builds an APK, runs Android resource linking/lint, or executes instrumentation tests. Existing core/DSP/catalog/Subsonic/updater/storage-schema checks were run separately.

`OfflineStoreTest` uses renamed databases/preferences and a private temporary directory. It covers protected deletion, favorites/playlist preservation, missing/corrupt files, cleanup exclusions, legacy downloads, queue deduplication, cancellation and restart recovery. The tests were compiled but have not run on Android. `LocalizationTest` was also compiled during this source-only validation; its runtime assertions remain unexecuted.

Storage management was released in 0.6.5 with successful hosted assembly/lint. Storage-specific device validation remains pending. Check the screen and all six translations on Android: usage refresh, confirmation sizes, each cache limit, prefetch switching, cleanup during streaming/paused playback, playback of an offline file during delete-all, and seeking after retained-file cleanup. Test download progress and cancellation during connection/read, Wi-Fi loss and restoration, cellular policy, force-stop/restart, failed retry, insufficient space and service timeout behavior on supported Android versions. Exercise the screen with large fonts and many queued tracks (25 rows per page). Verify database/settings/server credentials and original local files remain unchanged. No storage-specific device validation is claimed.

## Library navigation cleanup

Host `LibraryFilterTest` checks combined local sources, legacy filter normalization, independent server/radio filtering and the all-sources case. Existing source IDs and offline metadata are unchanged. These checks run through `bash scripts/test-core.sh`.

`LibraryNavigationTest` adds Android checks for a single row of eight sections, visibility of the selected last tab, natural-width labels with enlarged fonts, search's three categories and stable selection callbacks. The isolated storage instrumentation suite also checks playlist source grouping without changing mixed playlist contents. Production and instrumentation Java sources were compiled against cached Android dependencies with substituted resource IDs, and resources compiled with AAPT2; no APK or instrumentation execution is claimed for this change.

Before release, check all library sections and return navigation, the playlists tab, horizontal scrolling in both orientations and all languages, source-filter restoration after rotation/language changes, mixed local/server playlists, and the single remaining grid selector. Confirm appearance Apply/Reset leaves the grid choice intact and track/player menus still work. The phone has not been accessed for this change.

## Server listening reports

`ListeningSessionTest` covers actual-time accumulation, pauses, unknown duration, threshold edges, repeat sessions, restored checkpoints without downtime, submitted markers, account identity and bounded retry. `ScrobbleApiTest` sends production client requests to a local HTTP fixture, checking opaque IDs, millisecond timestamps, submission flags, all timeline states, client-controlled counting, discovery/fallback, cancellation and HTTP/API failures. SQLite fixtures exercise the additive v3→v4 schema, preserved library data, 10,000-entry pruning, oldest-first retention and independence from catalog rows.

The isolated Android storage suite adds real `Library` checkpoint/acknowledgement/retry/clear tests. These instrumentation tests are compiled against cached Android dependencies with substituted resource IDs, not executed. Resources are compiled separately with AAPT2; no local APK, Android Lint or device run is claimed for this development change.

Before release, use a controlled Navidrome account/scrobbler to check live playing/paused/stopped status, seek/rate changes, threshold counting, rapid skips/repeats, replay after the final item ends, and notification controls. Play a downloaded server track with the server unreachable, restart the app, reconnect and check the original timestamp and one normal submission. Repeat with a fully cached track. Confirm local files/radio are excluded, disabling clears pending entries, source changes never send to the wrong account, and server/auth failures do not interrupt playback. Exercise both basic Subsonic and advertised OpenSubsonic reporting. Ambiguous network acceptance cannot guarantee exactly-once server delivery; see [synchronization](SYNCHRONIZATION.md#listening-reports-066).
