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

GitHub-hosted CI and protected release publication have run successfully for 0.6.3 through 0.6.6. Official releases retain the existing signing identity. Each future tag must still pass the workflow checks and signing approval; a previous successful run does not validate unmerged development code.

The maintainer confirmed in-app update discovery, download and installation on the test phone on 2026-10-15. Selected screen/font checks were also performed during earlier UI work. These results do not cover every language, device size, Android version or failure path.

Version 0.6.6 passed its GitHub release build and publication. Its navigation, listening reports and selected storage/lifecycle scenarios were subsequently exercised on Android 14; see the device review below. Instrumentation suites remain unexecuted, and this device review does not cover the full supported Android matrix.

Updater-specific fixtures and the required Android permission/install/lifecycle checks are described in [UPDATES.md](UPDATES.md#validation). Host fixtures are not evidence of successful Android APK verification or installation.

## Version 0.6.6 device review

On 2026-10-15, the GitHub-distributed 0.6.6 APK (versionCode 13) was reviewed over ADB on one Android 14 phone at 1080×2400, density 440. No new APK or instrumentation package was installed. Screenshots and UI hierarchy captures remain private and outside the repository.

- Opened all eight library sections, album/artist/playlist details, search categories, track actions, source filtering, settings, appearance, equalizer/presets, source setup with the keyboard, storage, listening reports and updates. Covers and queue highlight insets rendered correctly in the inspected views. The two/three-column toggle worked, and applying the existing appearance preserved the grid choice.
- Switched among all six languages using Android's per-app locale mechanism and inspected settings, storage, reporting and library views. Ukrainian was also checked with 130% and 150% font scales. The language picker was inspected; this is not an exhaustive proofread or an Android 8–12 language-lifecycle test.
- Played a downloaded server track with Wi-Fi disabled and mobile data already disabled. After more than half its duration, pending listens changed from zero to one. After reconnecting and pressing manual retry, the count returned to zero. This observes the client queue, not an independent confirmation from Navidrome or an external scrobbling service.
- Force-stopped and reopened the paused app. The selected track and approximately 203-second position restored without autoplay; the previously drained listen did not reappear in the pending count. A new remote download queued without connectivity survived the same restart, could be cancelled, and its test-only history entry was removed. Existing downloaded files were retained.
- Exercised background/foreground entry, language/configuration changes during playback and six rapid Next commands. The six logged transitions became ready in 62–133 ms in this run; these timings are not end-to-end audio latency or a Bluetooth benchmark. No SELFonium playback error, audio-underrun warning or Java fatal exception appeared in the inspected app-process logs.
- Manual updater checking without connectivity displayed a network error and retained the earlier successful-check date. A later check after Wi-Fi was ready reported no newer stable release and updated that date. No installation was attempted during this review.

Reproduced issues: rotating with the full player open dismisses it to the library; rotating Storage dismisses it to Settings. These surfaces are not restored across activity recreation. At 150% font scale, the bottom Settings label is ellipsized and the ten equalizer gain labels are crowded. Other inspected settings/storage text wrapped and remained scrollable. These findings are recorded, not fixed by this review.

Network, app language, font scale, orientation and the original appearance/grid preferences were restored; playback was left paused. This was interactive smoke/regression testing, not instrumentation execution, audible quality assessment, TalkBack testing, Bluetooth/USB route validation, destructive storage testing or a full server/API compatibility run.

## Localization

Run `bash scripts/test-core.sh` for language selection and resource checks, including all six language sets, format placeholders, plural categories and stable preset/navigation keys. The checks are offline and are also part of the existing CI build. They do not build an APK or emulate Android resource selection.

On Android 8–12 and Android 13+, check the device-language default, unsupported-language English fallback, explicit overrides and returning to the system language. On Android 13+, also change the language in system app settings. Restart the app and confirm the choice persists. Switch languages during playback; verify music continues, sources and favorites remain intact, and custom playlist/preset names stay unchanged. Check navigation, player, source forms, update dates, notification labels and equalizer dialogs at large font sizes in each language. Review 0/1/2/5/11/21 item counts, truncated labels and accent colors. The full language/version matrix remains pending; earlier general screen/font checks do not cover it.

`app.selfonium.locale.LocalizationTest` adds instrumentation checks for Android resource selection, Ukrainian/Polish quantity rules, formatting and literal templates. It does not modify language preferences or playback. These instrumentation sources were subsequently compiled against cached Android dependencies with substituted resource IDs, but have not been executed on Android.

## Storage management

`bash scripts/test-core.sh` runs real-file host checks for managed directories, outside/symlink protection, multiple readers, alias-safe leases, atomic publication, SHA-256/size validation, cancellation, low-space rejection and interrupted streams. `python3 scripts/test-providers.py` also cancels the production OkHttp request before execution and during a local fixture's response-header wait. No test uses a personal server.

For the storage change, production and instrumentation Java sources were compiled against the cached Android 35 SDK and dependency classes, substituting generated resource IDs. AAPT2 resource compilation was also checked separately. Neither check builds an APK, runs Android resource linking/lint, or executes instrumentation tests. Existing core/DSP/catalog/Subsonic/updater/storage-schema checks were run separately.

`OfflineStoreTest` uses renamed databases/preferences and a private temporary directory. It covers protected deletion, favorites/playlist preservation, missing/corrupt files, cleanup exclusions, legacy downloads, queue deduplication, cancellation and restart recovery. The tests were compiled but have not run on Android. `LocalizationTest` was also compiled during this source-only validation; its runtime assertions remain unexecuted.

Storage management was released in 0.6.5 with successful hosted assembly/lint. Selected overview, file-check action and waiting-queue/restart/cancellation scenarios were exercised in the 0.6.6 review. Remaining checks include the screen and all six translations on Android: usage refresh, confirmation sizes, each cache limit, prefetch switching, cleanup during streaming/paused playback, playback of an offline file during delete-all, and seeking after retained-file cleanup. Test download progress and cancellation during connection/read, Wi-Fi loss and restoration, cellular policy, force-stop/restart, failed retry, insufficient space and service timeout behavior on supported Android versions. Exercise the screen with large fonts and many queued tracks (25 rows per page). Verify database/settings/server credentials and original local files remain unchanged. Cache eviction, protected deletion, low-space handling and active-transfer failure cases remain unverified on this device.

## Library navigation cleanup

Host `LibraryFilterTest` checks combined local sources, legacy filter normalization, independent server/radio filtering and the all-sources case. Existing source IDs and offline metadata are unchanged. These checks run through `bash scripts/test-core.sh`.

`LibraryNavigationTest` adds Android checks for a single row of eight sections, visibility of the selected last tab, natural-width labels with enlarged fonts, search's three categories and stable selection callbacks. The isolated storage instrumentation suite also checks playlist source grouping without changing mixed playlist contents. Production and instrumentation Java sources were compiled against cached Android dependencies with substituted resource IDs, and resources compiled with AAPT2; no APK or instrumentation execution was part of that initial source-only check. Subsequent release/device coverage is recorded above.

The 0.6.6 device review covers the main navigation, source-filter, grid and appearance-Apply paths. Remaining checks include the full orientation/language matrix, selected-filter restoration through rotation/language changes, mixed local/server playlist filtering, appearance Reset and accessibility navigation.

## Server listening reports

`ListeningSessionTest` covers actual-time accumulation, pauses, unknown duration, threshold edges, repeat sessions, restored checkpoints without downtime, submitted markers, account identity and bounded retry. `ScrobbleApiTest` sends production client requests to a local HTTP fixture, checking opaque IDs, millisecond timestamps, submission flags, all timeline states, client-controlled counting, discovery/fallback, cancellation and HTTP/API failures. SQLite fixtures exercise the additive v3→v4 schema, preserved library data, 10,000-entry pruning, oldest-first retention and independence from catalog rows.

The isolated Android storage suite adds real `Library` checkpoint/acknowledgement/retry/clear tests. These instrumentation tests are compiled against cached Android dependencies with substituted resource IDs, not executed. Resources are compiled separately with AAPT2; no local APK, Android Lint or device run was part of that initial source-only check. Subsequent hosted release/device coverage is recorded above.

For further validation, use a controlled Navidrome account/scrobbler to check live playing/paused/stopped status, seek/rate changes, threshold counting, rapid skips/repeats, replay after the final item ends, and notification controls. Play a downloaded server track with the server unreachable, restart the app, reconnect and check the original timestamp and one normal submission. Repeat with a fully cached track. Confirm local files/radio are excluded, disabling clears pending entries, source changes never send to the wrong account, and server/auth failures do not interrupt playback. Exercise both basic Subsonic and advertised OpenSubsonic reporting. Ambiguous network acceptance cannot guarantee exactly-once server delivery; see [synchronization](SYNCHRONIZATION.md#listening-reports-066).
