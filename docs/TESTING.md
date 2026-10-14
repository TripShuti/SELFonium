# Testing and validation

Run commands from the repository root. Tests are grouped by what they actually verify; successful compilation is not a runtime result.

## Host-side checks

| Command | Scope |
|---|---|
| `bash scripts/test-core.sh` | Protocol/queue logic, measured DSP, Java syntax, XML/manifest and SQLite schema/migrations |
| `python3 scripts/test-providers.py` | Production Subsonic and updater HTTP/JSON clients against isolated fixtures; requires Gradle dependency caches |
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

The published 0.6.2 APK was checked for release manifest flags, versionCode 9, matching certificate and ZIP alignment. Its SHA-256 is `964a3c4c9accf54ea5a479b0ff858676b27a48ff33847581f34e2d35f6f9f068`. Local tooling verification builds are separate artifacts and must not replace that published asset silently.

GitHub-hosted runs, protected-environment reviews and actual automated publication must be verified after the maintainer configures and activates the workflow; local validation alone does not demonstrate those hosted controls.

For the distribution automation update, local policy, workflow and host-side checks are recorded separately from hosted builds. No new APK build or device update test is claimed: the maintainer will test installation from GitHub Releases manually.

Local checks for the automation/documentation update passed: 17 release-policy tests, four CI-signing tests, 39 core checks, 69 DSP checks, SQLite migration/outbox checks, 119 social API checks and 61 catalog checks. Actionlint validated both workflows; local Markdown paths/images/anchors and 16 external documentation URLs were checked. The existing 0.6.2 asset and encrypted backup were verified against the pinned certificate. These results do not establish that GitHub-hosted signing or publication has run successfully.

Updater-specific fixtures and the required Android permission/install/lifecycle checks are described in [UPDATES.md](UPDATES.md#validation). Host fixtures are not evidence of successful Android APK verification or installation.

## Localization

Run `bash scripts/test-core.sh` for language selection and resource checks, including all six language sets, format placeholders, plural categories and stable preset/navigation keys. The checks are offline and are also part of the existing CI build. They do not build an APK or emulate Android resource selection.

On Android 8–12 and Android 13+, check the device-language default, unsupported-language English fallback, explicit overrides and returning to the system language. On Android 13+, also change the language in system app settings. Restart the app and confirm the choice persists. Switch languages during playback; verify music continues, sources and favorites remain intact, and custom playlist/preset names stay unchanged. Check navigation, player, source forms, update dates, notification labels and equalizer dialogs at large font sizes in each language. Review 0/1/2/5/11/21 item counts, truncated labels and accent colors. These device checks have not yet been performed for this change.

`app.selfonium.locale.LocalizationTest` adds instrumentation checks for Android resource selection, Ukrainian/Polish quantity rules, formatting and literal templates. It does not modify language preferences or playback. These instrumentation tests have been added but have not been compiled or executed as part of the localization work.
