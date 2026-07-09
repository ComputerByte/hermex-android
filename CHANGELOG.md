# Hermex Android v0.12.2-preview

## Issue #10 Hotfix — Active Stream Conflict Recovery

This is a **hotfix** for tester-reported issue #10: tapping Stop mid-stream, then immediately
tapping Edit + Resend (or Regenerate / Retry), caused the server to return
`409 {"error": "session already has an active stream"}` and the resent message was lost.

## Installation

**Minimum Android version:** 8.0 (API 26)
**Target Android version:** 16 (API 36)
**Signed:** Yes, v2 APK Signature Scheme (signer CN=Hermex Android)

**SHA-256:** `d488e15dea708f7eaf915ac6e8775a0ebdd31c3179211eb043abf2e633663fa8`

## What changed

### Fixed: stop → edit/resend active stream conflict
The Stop button fires a server-side `POST /api/chat/cancel`, but the local finalize ran
immediately, so an immediate Edit + Resend would fire `truncateSession` + `chat/start`
before the server had finished processing the cancel. The new `chat/start` then hit the
server while it still owned the previous `active_stream_id` and was rejected with 409.

After this fix, `ChatViewModel` tracks the in-flight server cancel and waits for it to
complete before any of `sendMessage`, `regenerate`, `editMessage`, or `retryLastMessage`
mutates the session.

### Fixed: stop → regenerate / retry active stream conflict
Same lifecycle ordering — regenerate and retry now `cancelStream()` + `awaitPendingCancel()`
before truncating.

### Fixed: one-shot `ResponseBody` error-body consumption
`safeApiCall` was reading `errorBody()?.string()` twice (once for the 409 StreamConflict
check, once for the `ApiError.Http` fallback). OkHttp's `ResponseBody.string()` is one-shot;
the second read returned `""`, silently losing the error body. Now read once and reused.

### Added: graceful 409 stream conflict handling
A 409 with body `"session already has an active stream"` is now mapped to a new
`ApiError.StreamConflict(activeStreamId)`. The chat/start path has a 2-attempt recovery:

1. First attempt → 409 with `active_stream_id` → poll `/api/session` every 150ms for up to
   ~4s, waiting for the server to release the slot.
2. If the slot clears, retry `chat/start` once and stream normally.
3. If the slot stays stuck (real server-side lock), surface a clear user-facing message:
   `"Previous stream is still stopping. Please wait a moment and tap Send again."`

### Preserved: expected local cancellation stays hidden
The previous hotfix (v0.12.1-preview, PR #8) silently drops the "Software caused connection
abort" `IOException` from expected local cancellation. This release preserves that behavior:
- `SseClient` still checks `coroutineContext.isActive` before surfacing a `TransportError`
- `cancelStream` still finalizes locally immediately (the user can keep typing)
- `cancelStream` failure on the server POST is still surfaced, so the user knows the server
  may still be running the turn

## Files changed

- `app/src/main/kotlin/com/hermex/android/core/network/ApiError.kt` — new
  `ApiError.StreamConflict` sealed class; `safeApiCall` 409→StreamConflict mapping; body
  read-once.
- `app/src/main/kotlin/com/hermex/android/chat/ChatUiState.kt` — new `isStopping: Boolean`
  field for UI feedback during the server-side cancel.
- `app/src/main/kotlin/com/hermex/android/chat/ChatViewModel.kt` — `cancelJob` tracking;
  `awaitPendingCancel()` and `awaitServerStreamRelease()` helpers; cancel-then-await in
  `regenerate` / `editMessage` / `retryLastMessage`; 2-attempt 409 retry in `sendMessage`.
- `app/src/test/kotlin/com/hermex/android/core/network/ApiErrorTest.kt` (new) — 9 unit
  tests for the 409→StreamConflict translation, the body-consumption fix, and `userMessage`
  copy.
- `app/src/test/kotlin/com/hermex/android/chat/ChatViewModelTest.kt` — 5 new regression
  tests covering send/stop/edit/resend ordering, send/stop/regenerate ordering,
  stop-while-streaming clearing, no-IOException-on-local-cancel, and 409 userMessage copy.

## Previous release

See [v0.12.1-preview](#hermex-android-v0121-preview) below for the prior hotfix.

---

# Hermex Android v0.12.1-preview

## Stream Cancellation Hotfix

This is a **hotfix** for tester-reported issues in v0.12.0-preview / v1.0.0-rc1.

## Installation

**Minimum Android version:** 8.0 (API 26)

**Download:**
- `hermex-android-v0.12.1-preview-release.apk` (release build, R8 minified, signed)

**Verify integrity:**
```
12e3bf1fe748c9519343dc0976af28fb9d11a5b51854fdf53e5ff8040c0a3d4a  hermex-android-v0.12.1-preview-release.apk
```

## Changelog

- **Fixed:** Benign stream cancellation no longer surfaces as "Software caused connection abort" error.
  - Underlying SSE read now checks coroutine `isActive` before emitting a `TransportError`; expected
    cancellation artifacts from user navigation are silently dropped.
- **Fixed:** Drawer "Recents" section now respects the "Subagent Sessions" toggle, matching the main
  session list behavior.
- **Tests:** Added two regression tests in `SseClientTest` for cancelled-stream behavior.

## Known Limitations (carried forward)

- Git workspace is read-only (commit/discard/checkout deferred to 1.1+)
- Full chat pagination beyond `msg_limit=50` deferred to 1.1
- Gateway-only / OpenAI-compatible mode is not in 1.0 (planned for 1.1+)
- Background response completion notifications may be unreliable on some OEM ROMs

## Upgrade

- **From v0.12.0-preview or v1.0.0-rc1:** Direct install. Settings and session data preserved.
- **Fresh install:** Configure server URL on first launch.

## Build Info

- versionName: 0.12.1-preview
- versionCode: 26
- compileSdk: 36
- minSdk: 26
- R8: enabled (release, signed)

---

# Hermex Android v1.0.0-rc1

## 1.0 Release Candidate

This is the **release candidate** for Hermex Android 1.0. All 21 hardening slices from the v0.12.0-preview cycle are carried forward with no new feature work. This build is intended for tester validation prior to the final stable v1.0.0.

## Installation

**Minimum Android version:** 8.0 (API 26)

**Download:**
- `hermex-android-v1.0.0-rc1-release.apk` (release build, R8 minified, unsigned)
- `hermex-android-v1.0.0-rc1-debug.apk` (debug build, for development)

**Verify integrity:**
```
dfda5a96e969afbc5728c5f949b29fa4da87b97cb235e8356b5da00ccdd14db8  hermex-android-v1.0.0-rc1-debug.apk
51fb00db59709780bd782576731f16ef149884a6476664685ac708a37bd22120  hermex-android-v1.0.0-rc1-release.apk
```

> **Note:** the previous v0.12.0-preview release APK (`fcbf1d55…`) was R8-minified but **unsigned**, so Android would refuse to install it. v1.0.0-rc1 release APK above is now properly signed and verified installable on real devices.

## What's In This Candidate

- Carries forward all v0.12.0-preview hardening:
  - R8/minified release build
  - HTTP URL policy classifier
  - SSL error detection
  - Custom header value masking
  - Logging suppression in release
  - Stream reattach regression coverage
  - Offline cache audit
  - Share target regression verification
  - Notifications + widget QA
  - Full regression checklist + 3x green test runs
- README updates: restored screenshots gallery; added Gateway-only / OpenAI-compatible compatibility note (gateway-only mode deferred to 1.1+)
- No new features in this build

## Known Limitations (carried forward)

- Git workspace is read-only (commit/discard/checkout deferred to 1.1+)
- Background response completion notifications may be unreliable on some OEM ROMs
- Full chat pagination beyond `msg_limit=50` deferred to 1.1
- Edit/resend and Regenerate UI deferred to 1.1
- Release APK is unsigned (debug APK is the primary tester distribution)
- Gateway-only / OpenAI-compatible mode is not in 1.0 (planned for 1.1+)

## Upgrade

- **From v0.12.0-preview:** Direct install. Settings and session data preserved.
- **From earlier:** Database migration is automatic.
- **Fresh install:** Configure server URL on first launch.

## Build Info

- versionName: 1.0.0-rc1
- versionCode: 25
- compileSdk: 36
- minSdk: 26
- targetSdk: 36
- R8: enabled (release)

## Reporting Issues

Use **Settings → App → Copy Diagnostics** to copy a snapshot of version, build, server, and headers. Include this in any bug report.

---

# Hermex Android v0.12.0-preview

## 1.0 Hardening Release

This release is the **hardening cut** for 1.0 — all major features in place, with a security, performance, and reliability pass complete.

## Installation

**Minimum Android version:** 8.0 (API 26)

**Download:**
- `hermex-android-v0.12.0-preview-release.apk` (release build, R8 minified, unsigned)
- `hermex-android-v0.12.0-preview-debug.apk` (debug build, for development)

**Verify integrity:**
```
# SHA-256
475bd5ab18d76c13ff58ad0ca29d4e65de451230a2e3ccb00df2f5229a042d6a  hermex-android-v0.12.0-preview-debug.apk
e6c89847d94bd3beb572797f3bfeeadea0b3c93a6ba564d25a9ff5a151e1cad7  hermex-android-v0.12.0-preview-release.apk
```

## What's New

### Security Hardening
- HTTP URL policy: HTTPS always allowed; HTTP localhost/private networks allowed with warning; **HTTP public blocked** before save
- Self-signed certificate detection with clear user messaging
- Custom header values masked by default in Settings; tap to reveal
- Release logging suppression (`Log.v/d/i` stripped via R8)

### Stability & Performance
- Attachment I/O runs on `Dispatchers.IO` (no ANR on large files)
- Stream reattach after background/regain network verified
- Large sessions (200+ messages) verified no-crash
- R8 minification enabled with comprehensive ProGuard rules

### UX Polish
- Slash commands wired to real behavior (`/continue`, `/summarize`)
- Voice input: runtime permission flow with education Snackbar
- Composer: visual polish verified
- "Copy Diagnostics" in Settings for issue reports

### Quality Gates
- Full regression checklist at `qa/1.0-regression-checklist.md`
- 3 consecutive green test suite runs
- Notifications + widget verified via adb dumpsys

## Known Limitations (1.0)

- **Git workspace is read-only.** Commit, discard, and branch checkout are deferred to 1.1. UI shows a "read-only" badge.
- **Background response completion notifications** may be unreliable on some OEM ROMs (Samsung, Xiaomi). Tap-to-deep-link is reliable.
- **Full chat pagination** (infinite scroll) is deferred to 1.1. API limit is 50 messages; cache holds more.
- **Edit/resend and Regenerate** are 1.1 features. Slash command shows a placeholder.
- **Release APK is unsigned.** Debug APK is the primary distribution for testers.

## Self-Hosted Setup Notes

### HTTP vs HTTPS
- **HTTPS servers** work out of the box
- **Local network HTTP** (e.g. `http://192.168.1.x:8787`) is allowed with a warning banner
- **Public HTTP** is blocked for security
- For self-signed certs, use HTTPS with a properly signed cert (Let's Encrypt via reverse proxy recommended)

### Recommended Setup
```
Internet → Reverse Proxy (Caddy/Nginx/Traefik with Let's Encrypt) → Hermes server
```

### Direct Local Access
```
Phone on same Wi-Fi → http://192.168.1.X:8787 (warning shown, works)
```

## Upgrade Notes

- **From v0.11.x:** Direct install. Settings and session data preserved.
- **From earlier versions:** Database migration is automatic.
- **Fresh install:** Configure server URL on first launch.

## What's Deferred to 1.1

- Git commit/discard/checkout
- Chat history pagination beyond msg_limit
- Edit/resend UI
- Regenerate UI
- Encrypted shared preferences for custom headers (currently app-private storage)
- Background response completion reliability on all OEM ROMs
- Widget configuration activity

## Build Info

- versionName: 0.12.0-preview
- versionCode: 24
- compileSdk: 36
- minSdk: 26
- targetSdk: 36
- Kotlin: 2.0+
- AGP: 8.x
- R8: enabled (release)

## Test Coverage

- 35+ JVM unit test files
- 1 androidTest file
- 3 consecutive green full-suite runs
- No quarantined tests

## Files

- `hermex-android-v0.12.0-preview-release.apk` — Minified, R8'd
- `hermex-android-v0.12.0-preview-debug.apk` — For development/testing
- `qa/1.0-regression-checklist.md` — Full test matrix
- `qa/TEST_SUITE_HARDENING.md` — Test verification report
- `docs/*.md` — Various feature QA procedures

## Reporting Issues

Use **Settings → App → Copy Diagnostics** to copy a snapshot of version, build, server, and headers. Include this in any bug report.
