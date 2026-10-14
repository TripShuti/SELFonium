# Contributing

Use the existing Java, Android Views and Media3 architecture unless a change requires otherwise. Start with [building](docs/BUILDING.md) and work on a focused branch from `main`.

## Changes and validation

Describe the problem and resulting behavior in each pull request. Preserve stored sources, favorites, playlists, downloads and queue state across upgrades. Add regression coverage for meaningful behavior changes; avoid tests that merely repeat the implementation.

Run `bash scripts/build.sh` for Android changes. Release tooling also has `python3 scripts/test-release.py` and `python3 scripts/test-ci-signing.py`. Run `bash scripts/check-workflows.sh` for workflow changes, and stage intended files before `python3 scripts/check-repository.py`.

CI builds the debug app, runs host-side tests and lint, and uploads short-lived development artifacts. Android instrumentation tests require a device or emulator; see [testing](docs/TESTING.md). Compiling a test does not mean it ran.

Keep repository documentation in English. The application's UI strings and code comments are a separate concern. Update feature documentation and the changelog when behavior changes; distinguish released features from planned work.

## Security and releases

Do not commit server credentials, keystores, password files, personal captures, SDKs, caches or APKs. New dependencies require a purpose and upstream license reference in [DEPENDENCIES.md](docs/DEPENDENCIES.md).

Pull requests must not receive signing credentials. Changes to workflows, Gradle configuration and release tooling need particular review because they affect distributed artifacts. CODEOWNERS identifies the maintainer; GitHub branch and environment protection must be configured separately to enforce reviews.

Official release signing uses the existing pinned certificate. Do not generate a replacement key or change the certificate pin to bypass an error. Publishing remains disabled until the maintainer explicitly enables it and configures the protected signing environment. Maintainer-specific key, backup and activation instructions are kept outside the repository.

Use descriptive commits and do not rewrite shared history without agreement. Publishing or uploading signing secrets is a separate maintainer action, not part of a contribution build.
