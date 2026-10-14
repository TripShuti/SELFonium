# Building SELFonium

## Toolchain

| Component | Version |
|---|---|
| Minimum Android | 8.0 / API 26 |
| Compile / target SDK | 35 / 35 |
| Android Build Tools | 35.0.0 |
| JDK | 17 or 21; Java source level 17 |
| Python | 3.11 or later |
| Android Gradle Plugin | 8.9.2 |
| Gradle | 8.11.1 |

The build scripts target Linux and require Bash, curl, unzip, tar and SHA-256 tooling. From fish, invoke them with `bash` as shown. CI uses Ubuntu 24.04 and Temurin JDK 21.

## Android SDK

Install the Android SDK through Android Studio or the Android command-line tools. With `sdkmanager` available on PATH, accept its licenses and install the declared components:

```sh
sdkmanager --licenses
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
bash scripts/build.sh
```

Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` if necessary. Scripts also look in `.tooling/android-sdk`, `~/Android/Sdk` and `/opt/android-sdk`. Set `JAVA_HOME` to your JDK; when unset, the scripts can select the conventional Linux OpenJDK 21 installation.

The repository's `gradlew` is a Bash launcher for a checksum-pinned Gradle distribution, not the standard wrapper JAR. It defaults `GRADLE_USER_HOME` to `.tooling/gradle-home`; CI uses the same location with Gradle action caching. Android Studio can use the matching local distribution or Gradle version.

## Debug build

```sh
bash scripts/build.sh
```

This runs core, DSP, storage and syntax checks, builds the debug APK, runs Android Lint and executes the HTTP fixtures. Outputs:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `app/build/reports/lint-results-debug.html`

Install on an authorized device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. A debug build from another machine may use a different certificate and cannot necessarily update an official installation.

## Release build

Gradle's `:app:assembleRelease` creates `app/build/outputs/apk/release/app-release-unsigned.apk`. Gradle does not contain signing credentials or automatically choose a release keystore.

For your own distribution, align and sign the unsigned APK with Android Build Tools and your own keystore. Keep the keystore and passwords outside Git. A build signed with your own certificate cannot update an official installation with a different certificate.

Official packaging uses `python3 scripts/release.py build` with private external configuration and the existing pinned signing identity. It produces `SELFonium-v<versionName>.apk`, a SHA-256 checksum and a JSON provenance manifest. The script never pushes tags or uploads assets. Existing 0.5.1/0.6.2 assets retain their original filenames without the `v` prefix.

CI builds and tests debug APKs on pushes to `main` and pull requests without release credentials. The release workflow validates exact `vX.Y.Z` tags against `versionName`, increasing `versionCode` and ancestry from `main`. Each new release needs a matching version heading in `CHANGELOG.md`; existing published tags must not be reused. Separate jobs build without secrets, sign verified artifacts in a protected environment, and publish verified assets. Publication is disabled by default; manual workflow dispatch only performs an unsigned validation build. Personal key-management and activation instructions are maintained outside the repository.

## Other checks

`bash scripts/test-core.sh` needs a JDK and Python but no Android SDK. Use Gradle for Android compilation and lint checks. See [TESTING.md](TESTING.md) for the full validation scope.
