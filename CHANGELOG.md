# Changelog

## Unreleased

## 0.5.1

- Initial public APK release as SELFonium-0.5.1.apk, with a direct download and installation steps in the README.
- Refresh the project README with Resonance Mint branding, a feature overview, server support status and four screenshots from Android 0.5.1.
- Adopt the Resonance Mint launcher icon with an adaptive Android mask and safe margins.
- Replace the narrow source alert with a full-screen, keyboard-aware editor: full-width fields, persistent labels, protocol help and password visibility.
- Fix Jellyfin authentication by sending client identity and the session token in the Authorization header; retain Emby compatibility.
- Distinguish DNS, TLS, connection, timeout and HTTP failures in source synchronization messages.

## 0.5.0

- Fixed clipped library navigation: three equal tabs and a visible More menu replace horizontally scrolling category buttons.
- Inset track artwork and controls within the rounded touch highlight.
- Appearance settings: accent palette/custom HEX color, AMOLED black background and cover-grid columns, with a preview before applying.
- Independent ten-band PCM equalizer with Preamp, 18 original presets, saved user presets and automatic headroom.
- Classic fader controls, precise gain input and a calculated response graph; legacy EQ settings migrate once.
- Added measured DSP tests for gain response, stereo isolation, preamp, headroom, bypass, reset and migration.

## 0.4.0

- Music-first library with direct album/artist/track tabs, compact shortcuts and a persistent two/three-column choice.
- Distinct album and artist pages, scrolling artwork headers, numbered album tracks and artist discography.
- Full-screen live queue with artwork, artist names, explicit removal and move-up/move-down actions.
- Graphite surfaces, consistent typography, compact home actions and grouped settings/track actions.
- Search prompt, clear button and five recent queries; Ukrainian count forms and album placeholders.
- Artwork-tinted player/detail backgrounds, adaptive player artwork, and a mini player shown only after selecting music.
- Compact landscape library cards and controls, with a two-column landscape player.
- Equalizer graph scales, active/custom preset indication and explicit reset.

- Trust system and user-installed Android CAs for HTTPS home servers while retaining certificate and hostname verification.
- Repository documentation and MIT license.
- Removed the GitHub build workflow and its Dependabot configuration during active development; builds and checks run locally.

## 0.3.0

- Direct track-row playback and separate overflow action targets.
- Refreshed navigation, library categories, search and settings.
- Full-screen player/equalizer, dark dialogs and track action sheet.
- Current-track highlighting and consistent album ordering.
- Android instrumentation coverage for row interaction regressions.

## 0.2.0

- Subsonic/Navidrome artwork and bounded image caching.
- Library migration preserving favorites, playlists, history and downloads.
- Album grid, artwork in the mini player and revised playback controls.
- Equalizer frequency/gain display, graph and presets.

## 0.1.0

- Initial Android player, local library, remote source adapters, playback service,
  queue, favorites, playlists, offline downloads and system equalizer.
