# Dependencies and tooling

| Component | Role | Upstream / license |
|---|---|---|
| AndroidX Media3 1.6.1 | Playback, sessions, HLS, data sources and cache database | [AndroidX Media](https://github.com/androidx/media), Apache-2.0 |
| AndroidX annotation-experimental 1.4.1 | API opt-in annotations | [AndroidX](https://android.googlesource.com/platform/frameworks/support/), Apache-2.0 |
| OkHttp 4.12.0 | HTTP requests | [OkHttp](https://github.com/square/okhttp), Apache-2.0 |
| desugar_jdk_libs 2.1.5 | Java API compatibility on older Android versions | [Upstream licenses and notices](https://github.com/google/desugar_jdk_libs) |
| Android Gradle Plugin 8.9.2 | Android build integration | [Android tools](https://android.googlesource.com/platform/tools/base/), Apache-2.0 |
| Gradle 8.11.1 | Build engine | [Gradle](https://github.com/gradle/gradle), Apache-2.0 |
| JSON-Java 20240303 | Desktop HTTP fixtures only; not packaged by this test helper into the app | [JSON-Java](https://github.com/stleary/JSON-java), see upstream license |
| actionlint 1.7.12 | Workflow validation only | [actionlint](https://github.com/rhysd/actionlint), see upstream license |

Direct application dependencies are declared in `app/build.gradle`. Android supplies its platform JSON API. JVM HTTP fixtures use a separate JSON-Java JAR with a pinned SHA-256; Gradle and actionlint downloads are also checksum-pinned. Workflow actions are pinned to commit SHAs and used only by GitHub Actions.

This is an inventory, not a complete transitive-license report. Dependencies retain their licenses and notices independently of SELFonium's MIT license. Inspect the resolved dependency graph and upstream notices when preparing distribution. Android SDK licenses require separate acceptance.
