# Hermex Android

Native Android (Kotlin + Jetpack Compose) client for a self-hosted
[`hermes-webui`](https://github.com/nesquena/hermes-webui) server — the Android counterpart to
the iOS Hermex app.

This is an MVP slice: onboarding/auth, session list, and chat with SSE streaming. See
`API_CONTRACT.md` for the verified server API contract this app targets, and the implementation
plan this repo was scaffolded from for phased build order and architecture decisions.

## Requirements

- JDK 17+ (developed against Temurin/OpenJDK 21)
- Android SDK (`compileSdk`/`targetSdk` 35, `minSdk` 26)

## Build

```
./gradlew assembleDebug
./gradlew test
```
