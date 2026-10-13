# SELFonium development

This directory is the repository root. Keep product source, tests, build configuration and public documentation here.

- Work from a task branch when the change is substantial. Preserve unrelated user changes.
- Keep changes focused. Use the existing Java/Android Views/Media3 architecture unless the task requires a migration.
- Preserve stored sources, favorites, playlists, queue state and downloads across upgrades.
- Run checks appropriate to the change. `bash scripts/build.sh` runs core checks, debug assembly and lint. UI interaction changes also require device testing where available.
- State clearly which checks ran and which did not. Never describe type compilation as a passed runtime test.
- Inspect staged changes and run `python3 scripts/check-repository.py` before committing.
- Commit completed, checked work with a concise descriptive message unless the user asks to leave it uncommitted. Do not amend shared history or force-push without explicit authorization.
- Push or publish only when requested. Never add credentials, signing keys, personal captures, local tooling or build outputs to Git.
- Keep README, feature limitations, test instructions and CHANGELOG consistent with the implementation.
