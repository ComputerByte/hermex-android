# Hermex Android

Hermex is the native Android control plane for a self-hosted [Hermes](https://github.com/nesquena/hermes-webui)
AI agent server, built with Kotlin and Jetpack Compose — the Android counterpart to the iOS
Hermex app.

## Status

**Active development / preview build — current version: v0.9.0-preview.**

This is not a final Play Store release yet, but the app is already running on real Android
hardware against a real Hermes server. See `API_CONTRACT.md` for the verified server API contract
this app targets.

## Current Features

- Native Android / Jetpack Compose UI
- Real server authentication and session loading
- Chat with SSE token streaming and a stop control for in-progress runs
- Markdown rendering in chat (bold/italic, inline & fenced code, lists), plus tool call cards and
  collapsible reasoning blocks; copy message via long-press
- Attachment upload: pick a file from the composer, it uploads immediately, and is attached to
  the next sent message (with upload-failure handling that preserves the composer's text/state)
- Session list with search
- Tasks, Skills, Memory, Profiles, Projects, and Insights (usage analytics) screens
- Chat-scoped model switching (updates a session in place, matching iOS)
- Multi-server switching
- Per-server cookies
- Per-server custom HTTP headers
- Runtime app icon switching (Light/Dark/Disco/System), using the real iOS icon assets
- Header logo color theming
- Design tokens (colors, shape, typography) aligned to the Hermex design system; chat message
  bubbles and the composer field styled from those tokens
- Room-based offline cache for the session list, with per-server cache isolation
- Room-based offline cache for chat/message history, with cache-first load and fallback banners
- Offline cache retention: pruned once per app startup, per server, to the 50 most recently
  active sessions or 90 days old (whichever is stricter); orphaned cached messages are swept too
- Session-scoped read-only workspace browser with folder navigation and text file preview
- Android share target support for shared text, single files, and multiple files
- Deep link entry routes for `hermex://` and `hermes-agent://`
- Notification channel and notification routing foundation
- Home screen widget entry surface

## In Progress / Not Finished Yet

- Workspace actions polish: copy path, refresh, search, and raw/open/download support
- Git status/diff panels pending backend endpoint discovery
- Full chat composer redesign per the design system (pill-shaped chip row, capsule icon buttons,
  translucent field architecture) — v0.9.0-preview only aligned the composer field's existing
  shape/radius to design tokens, not the wider component architecture
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
