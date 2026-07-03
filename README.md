# Hermex Android

Native Android client for [Hermex](https://github.com/nesquena/hermes-webui), built with Kotlin
and Jetpack Compose — the Android counterpart to the iOS Hermex app, targeting a self-hosted
`hermes-webui` server.

## Status

**Active development / preview build.**

This is not a final Play Store release yet, but the app is already running on real Android
hardware against a real Hermes server. See `API_CONTRACT.md` for the verified server API contract
this app targets.

## Current Features

- Native Android / Jetpack Compose UI
- Real server authentication and session loading
- Chat with SSE token streaming
- Session list with search
- Tasks, Skills, Memory, Profiles, Projects, and Insights screens
- Chat-scoped model switching (updates a session in place, matching iOS)
- Multi-server switching
- Per-server cookies
- Per-server custom HTTP headers
- Runtime app icon switching (Light/Dark/Disco/System), using the real iOS icon assets
- Header logo color theming
- Room-based offline cache for the session list, with per-server cache isolation
- Room-based offline cache for chat/message history, with cache-first load and fallback banners

## In Progress / Not Finished Yet

- Workspace and git panels
- More iOS parity polish
- Play Store release hardening (this preview ships a debug build; release signing isn't set up yet)

## Download

A preview APK is available under [GitHub Releases](../../releases).

This is a manual-install APK, not distributed through the Play Store. Android will warn that it's
from an unknown source — that's expected for a debug preview build.

## Screenshots

| Sessions | Multi-server |
|---|---|
| ![Sessions](release-artifacts/screenshots/session-list.png) | ![Multi-server](release-artifacts/screenshots/multi-server.png) |

| Insights | App Icons |
|---|---|
| ![Insights](release-artifacts/screenshots/insights.png) | ![App Icons](release-artifacts/screenshots/app-icon-picker.png) |

More screenshots (connection headers, header logo color, chat model picker, offline cache
banner, projects) are in [`release-artifacts/screenshots/`](release-artifacts/screenshots).

## Requirements

- JDK 17+ (developed against Temurin/OpenJDK 21)
- Android SDK (`compileSdk`/`targetSdk` 36, `minSdk` 26)

## Build

```bash
./gradlew test
./gradlew assembleDebug
```
