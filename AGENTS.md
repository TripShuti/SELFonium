# SELFonium development

This directory is the repository root. Keep product source, tests, build configuration and public documentation here.

- Work from a focused task branch and preserve unrelated changes. Keep the Java/Android Views/Media3 architecture unless a task requires migration.
- Preserve sources, favorites, playlists, queue state and downloads across upgrades.
- Keep all repository Markdown documentation in English. Do not translate UI strings, code comments or identifiers solely for documentation consistency.
- Run checks appropriate to the change. `bash scripts/build.sh` builds/tests the debug app; release tooling also needs its policy tests and workflow validation. UI interaction changes require device checks where available.
- State which checks actually ran. Compilation, local workflow lint and hosted/device runtime execution are different results.
- Inspect staged changes and run `python3 scripts/check-repository.py` before committing. Commit completed, checked work unless asked to leave it uncommitted.
- Do not amend shared history or force-push without explicit authorization. Push or publish only when requested.
- Never commit credentials, signing material, personal captures, caches or build outputs. Do not upload signing secrets or enable real automated publication without explicit authorization.
- Preserve the existing signing certificate. Use the protected environment and reviewed-main tag checks implemented in `.github/workflows/release.yml`; pull requests must not receive release secrets.
- Keep README, feature limitations, build/release instructions, roadmap and changelog consistent with the implementation and verification evidence.
