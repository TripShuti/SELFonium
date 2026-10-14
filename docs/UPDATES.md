# In-app updates

The updater is available under **Settings → Updates** (currently displayed in Ukrainian). It checks stable GitHub Releases, downloads the selected APK inside SELFonium and hands a verified file to Android's system installer. It does not use an app store, Google Play Services, analytics or a third-party update SDK.

## Checks and delivery

On activity start/return to the foreground, automatic checking runs if enabled and at least 24 hours have passed since the previous attempt. Failed attempts count toward that interval to avoid repeated offline requests. The switch disables automatic network checks. Manual checks have a one-minute minimum interval; GitHub rate-limit cooldowns take precedence. In-flight operations are coalesced, with no polling or automatic retry loop. The last successful check is shown separately from the last attempt.

The endpoint is `https://api.github.com/repos/TripShuti/SELFonium/releases/latest`. Draft and prerelease responses are ignored. No GitHub token is used. Each check fetches release JSON and the matching small JSON manifest; it never downloads an APK just to compare versions. Numeric `versionCode` determines whether an update is newer, including when version names would sort differently as text.

A new version produces one in-app banner per versionCode. The update screen remains available after dismissal. Users can defer the update or suppress reminders for that specific version; this does not hide it from a manual check. Release notes are selectable literal text, including Markdown markers: no HTML rendering, JavaScript, external spans or WebView is involved.

The updater accepts one APK and its matching JSON asset from the configured repository and release tag. Current names are `SELFonium-vX.Y.Z.apk` and `SELFonium-vX.Y.Z.json`; legacy names without the `v` prefix are also understood when both assets exist. Assets must be uploaded and have bounded, positive sizes. Ambiguous assets or missing metadata are rejected. Legacy releases containing only an APK cannot be used for in-app updates.

## Existing manifest contract

The JSON produced by `scripts/release.py` is reused without changing its schema or the release workflow. Required fields:

| Field | Meaning |
|---|---|
| `package` | Must be `app.selfonium` |
| `version` | Must match the stable release tag and the downloaded package |
| `versionCode` | Positive integral Android code; must exceed the installed code |
| `sha256` | SHA-256 of the final signed APK |
| `signerSha256` | SHA-256 of the APK's signing certificate |
| `debuggable` | Must be false |
| `workingTreeDirty` | Must be false |

GitHub's release JSON supplies the publication date, notes, asset sizes and direct download URLs. Other provenance fields in the manifest remain useful to maintainers but are not authentication evidence for the updater. A version tag and filename alone are never sufficient.

## Download and verification

Downloads use the existing OkHttp stack on a separate updater executor, independent of catalog synchronization and playback. Progress reports percent and transferred/total bytes. Cancellation cancels the active HTTP call and deletes partial files. Truncated responses, size/hash mismatches, insufficient free space and network errors stop the download; retrying starts a fresh download rather than resuming ranges.

Files stay under the application's private `cache/updates` directory. Interrupted partials are removed on the next process start, obsolete candidates are removed, and APKs older than 24 hours are removed on process start or foreground return. Last-known release metadata can still be displayed with its previous successful-check date. Android may evict cache files sooner. The process may continue a download while briefly backgrounded, but there is no foreground service: Android can terminate it, and an interrupted download is not resumed automatically.

Before making an APK available for installation, and again when Install is pressed, the updater checks:

1. Exact asset size and SHA-256.
2. Package name, versionName and numeric versionCode against the release metadata and installed version.
3. Non-debuggable package flags and minimum SDK compatibility with the device.
4. Android-parsed APK signing certificates against the currently installed SELFonium identity and the manifest fingerprint.

SHA-256 from the same release protects integrity, not authenticity independently of GitHub. The trust anchor is the installed application's certificate. Android 9+ signing history can authorize forward single-signer rotation using platform-verified proof of rotation; reverting to a past certificate or an unrelated certificate is rejected. Multiple signers require an exact set match. Android 8.0/8.1 use exact legacy signer matching; the manifest fingerprint must match the signer visible to that platform. No key is rotated by this feature, and a future rotated distribution would need dedicated testing across Android versions. Android's installer makes the final decision, including rotation capabilities and device policy.

All original and redirected URLs must use HTTPS. Direct asset URLs must belong to the configured repository/tag; redirect destinations are restricted to GitHub and its known release asset hosts. TLS and hostname checks remain enabled. Redirects, response sizes and request durations are bounded; no credentials are sent. APKs larger than 200 MiB are rejected, and downloads require an additional 8 MiB reserve. These are updater limits, not a promise that Android has enough space to complete installation.

## Android installation

The only added permission is `REQUEST_INSTALL_PACKAGES`. If SELFonium is not an allowed installation source, the screen explains why and opens Android's per-source settings. Returning with permission granted triggers verification again before opening the installer. Denying permission leaves the verified APK available for a later attempt.

A non-exported AndroidX FileProvider exposes only the update cache directory. The installer receives a `content://` URI for the verified APK with a temporary read grant; no storage permission, public download directory, root access or silent-install workaround is used. Android prompts the user to confirm installation. Canceling leaves the APK ready for retry. Installing over the existing package preserves application data when Android accepts the update; the updater never uninstalls or clears data.

Device administrators, OEM restrictions and Play Protect may block or inspect installation. Cache eviction, process death and activity recreation can require reopening Updates or pressing Install again. Successful replacement normally terminates the old app process. A CI/debug build signed with another certificate cannot update the official app and will reject its APK. Users of 0.6.3 and earlier must manually install the first release that contains the updater; this feature cannot add itself to an already installed older binary.

## Validation

`python3 scripts/test-providers.py` runs the updater fixtures alongside the existing Subsonic tests. Tests use a local HTTP server and a test-only OkHttp routing interceptor; they do not require GitHub availability, a token, a signing key or a device. They cover stable/no-newer versions, malformed metadata/version codes, missing assets, HTTP 404/403/429 and cooldowns, connection failures, bounded redirects, HTTPS URL restrictions, hash/size validation, progress, cancellation, interrupted downloads, low space, cleanup and certificate/rotation policy.

These host tests exercise production parsers, transport and streaming logic. Certificate-policy fixtures use synthetic fingerprints; they do not execute Android PackageManager's APK verifier, FileProvider, lifecycle callbacks or the system installer. CI builds/lints the Android app. See [TESTING.md](TESTING.md) for the general test entry points.

Before distributing the first updater release, manually check Android 8.0/8.1 and a modern Android device:

- Automatic checking on foreground return, disabled checking, manual throttling, persisted last-check time and one reminder per version.
- Theme/accent/AMOLED, rotation, small screens, TalkBack, long notes and scrolling while music plays.
- Progress, cancellation, lost connectivity, low storage, process termination and cache eviction.
- Permission denial/grant, return from source settings, installer cancellation and retry.
- A real higher-version APK signed with the current key; preservation of sources, favorites, playlists, downloads and queue after replacement.
- Wrong package, tampered APK, unrelated signer and any planned signing-lineage transition, all rejected before installer handoff where applicable.

Do not publish a test release merely to exercise these scenarios. Use controlled local fixtures or a reviewed future release approved by the maintainer.

Platform references: [SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo), [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider), and [GitHub Releases API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).
