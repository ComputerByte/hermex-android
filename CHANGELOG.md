# Hermex Android v0.12.7-preview

## Chat streams survive backgrounding

Backgrounding the app during a streaming reply used to get the connection frozen and killed by
Android ("Software caused connection abort"), cutting the response short. A foreground service
now keeps the process alive for the duration of an active chat stream, shown as a low-priority
"Response in progress" notification, and is torn down on every terminal path (normal
completion, cancel, error, reattach).

Follow-up fixes to that mechanism, found during review:
- A reattach from the notification while the app was still backgrounded could crash the
  service-start call; it now retries cleanly instead of getting stuck.
- Replacing one stream with another no longer leaves the old service running orphaned.
- A stream that ends with a clean connection close (no explicit terminal event) now still
  releases the service and finalizes state correctly — including when reached via reattach.
- Android 15's 24-hour foreground-service timeout is now handled explicitly, and the app's
  internal bookkeeping resyncs afterward instead of silently blocking future streams from
  getting foreground protection.

## Installation

**Minimum Android version:** 8.0 (API 26)
**Target Android version:** 16 (API 36)
**Signed:** Yes, v2 APK Signature Scheme (signer CN=Hermex Android), current rotated certificate

**SHA-256 (APK):** `8f3c0246d6a52670429b4853109401b1439dbc70db4b9a76d3521174b3013afc`

---

# Hermex Android v0.12.6-preview

## Sidebar: Active Profile

The navigation drawer was missing an entry for switching profiles, even though the
underlying navigation route was already wired up — the "Active Profile" row simply
wasn't rendered. Added it back between Insights and Projects, matching the iOS layout.

## Installation

**Minimum Android version:** 8.0 (API 26)
**Target Android version:** 16 (API 36)
**Signed:** Yes, v2 APK Signature Scheme (signer CN=Hermex Android), current rotated certificate

**SHA-256 (APK):** `073b5b8776a7050f59d448c8e7d1b3a3ccc4c0196f1d025d0825d224f39c34e3`

---

# Hermex Android v0.12.5-preview

## Chat reliability and attachment history

This preview fixes two chat reliability issues reported on long-running conversations:

- Sent user turns now appear immediately, with safe rollback of the draft and attachments if
  `/api/chat/start` fails.
- Image attachment metadata is retained in optimistic state and offline cache; historical images
  render through the authenticated `/api/file/raw` endpoint.
- In-flight chat streams are retained outside individual navigation destinations, so switching to
  another conversation no longer cancels the original response. Returning to the conversation
  reuses its live state without creating a duplicate SSE collector.
- Retained chat state is cleared on logout and server changes to prevent cross-server leakage.

## Installation

**Minimum Android version:** 8.0 (API 26)
**Target Android version:** 16 (API 36)
**Signed:** Yes, v2 APK Signature Scheme (signer CN=Hermex Android), current rotated certificate

**SHA-256 (APK):** `390320e640f30ab0759efb208092905a955cdb6abb95a0ea3f250a39a8215177`

---

# Hermex Android v0.12.4-preview

## Security maintenance release — signing key rotation

**v0.12.3-preview and all release-signed builds back to v1.0.0-rc1 are superseded and must
not be installed.** Their signing key must be treated as compromised (see below). Install
this release instead.

The signing certificate used for earlier preview APKs may have been exposed in previous
repository history and has been replaced.

Because this release uses a new signing certificate, users with an older preview APK must
uninstall it before installing this version. Uninstalling clears locally stored app data.

This release also includes the Android TTFT connection-reuse fix from v0.12.3-preview.

### What happened

`release.jks` and `signing.properties` were briefly committed to this repository in commit
`cdb6d2c` (2026-07-07) and removed 28 seconds later in `79f3a61`. The repository was public
for the entire time those files existed in its history. Because Git retains blob content from
every commit that was ever pushed, the keystore and credentials remain permanently recoverable
from the public history (`git show cdb6d2c:release.jks`) even though the current working tree
no longer contains them removing them from the current tree does not revoke the exposure.

**Compromised certificate SHA-256:**
`30:96:FB:52:82:7C:FB:6B:56:71:3F:4C:AB:6C:59:FF:79:D1:20:D0:3F:00:EF:3D:E8:25:5D:7A:4B:75:DB:F3`

**Releases signed with the compromised key** (confirmed by downloading and verifying each
release asset): v1.0.0-rc1, v0.12.1-preview, v0.12.2-preview, v0.12.3-preview.
(v0.12.0-preview's release APK asset was never validly signed at all -- unrelated to this
issue. Releases v0.11.2-preview and earlier only ever shipped debug-signed APKs, which use a
different, non-committed keystore and are not affected by this exposure.)

**New upload certificate SHA-256:**
`BB:A4:76:0B:59:72:98:37:A6:6E:1B:56:EB:F0:05:6C:D1:4A:3C:02:A2:BE:F2:D5:8D:59:AA:E3:BF:78:2C:E1`

The app has never been uploaded to Google Play and Google Play App Signing was never enabled,
so this rotation only affects GitHub preview distribution -- there is no Play app-signing
identity to preserve or conflict with. See `SIGNING.md` for the new local signing setup, which
also now fails the build loudly if release-signing credentials are missing or incomplete,
instead of silently producing an unsigned artifact.

**Not done as part of this fix:** git history was *not* rewritten. Rewriting history does not
un-expose a secret that may already have been cloned, cached, or scraped, and force-pushing a
rewritten history is a separate, disruptive decision (breaks every existing clone/fork/PR) that
needs its own explicit sign-off. The fix here is the same one used for any leaked credential:
treat it as burned and rotate away from it, which is what this release does.

## Installation

**Minimum Android version:** 8.0 (API 26)
**Target Android version:** 16 (API 36)
**Signed:** Yes, v2 APK Signature Scheme (signer CN=Hermex Android) -- **new certificate**, see
above. Installing over an older v0.12.x/v1.0.0-rc1 build requires an uninstall first (signature
mismatch); this clears local app data.

**SHA-256 (APK):** `6e59fe8945d0b8cdf6461a9b1ef9f9c879fde6d4be6e60d26594026e18e19c6d`

## Previous release

See [v0.12.3-preview](#hermex-android-v0123-preview) below -- **superseded, do not install**.

---

# Hermex Android v0.12.3-preview

## Android chat time-to-first-token connection-reuse fix

`/api/chat/start` and `/api/chat/stream` were served by two independent `OkHttpClient`
instances, each with its own private connection pool, so every chat turn's SSE request paid a
redundant TCP (and potentially TLS) connection setup immediately after `chat/start` had just
opened or reused a connection to the exact same host. This release removes that client-side
transport penalty; actual model/provider generation time is unaffected and may still account
for most of the wait before the first token appears.

## Installation

**Minimum Android version:** 8.0 (API 26)
**Target Android version:** 16 (API 36)
**Signed:** Yes, v2 APK Signature Scheme (signer CN=Hermex Android)

**SHA-256:** `2d2fa5169e4197ba83e208c5240d77a1e2b6938d9cd1f11308fd87bb7d7d29f2`

## What changed

### Fixed
- Reduced Android chat time-to-first-token overhead by reusing the existing HTTP connection
  between `/api/chat/start` and the SSE stream.
- This removes an unnecessary connection handshake on each chat turn, with the largest benefit
  on mobile and higher-latency networks.
- Added debug-only TTFT tracing for future performance diagnostics.

Actual model generation time is unchanged and may still account for most of the wait before
the first token appears.

### Details
`NetworkModule.restClient` (used for `chat/start`) and `NetworkModule.sseClient` (used for the
SSE stream) are now both derived from one shared base `OkHttpClient` via `newBuilder()`, so
they share its `ConnectionPool`. Confirmed on-device via OkHttp `EventListener` instrumentation
(debug builds only) and, independently, via the server's own view of TCP client ports: the SSE
request now consistently reuses the same underlying connection `chat/start` just used, instead
of opening a new one every turn.

The `Dispatcher` is intentionally *not* shared between the two clients -- it only governs async
request scheduling and per-host concurrency limits, neither of which the synchronous SSE call
uses, and sharing it would only couple the two clients' `cancelAll()` semantics for no benefit.

## Files changed

- `app/src/main/kotlin/com/hermex/android/core/network/NetworkModule.kt` -- shared base
  `OkHttpClient` + `newBuilder()`; explicit separate `Dispatcher` for the SSE client;
  debug-only `EventListener` attachment.
- `app/src/main/kotlin/com/hermex/android/core/network/TtftEventListenerFactory.kt` (new) --
  OkHttp connection-timing instrumentation, scoped to `chat/start`/`chat/stream` only.
- `app/src/main/kotlin/com/hermex/android/core/util/TtftTracer.kt` (new) -- debug-only
  stage-timing logger; no-ops entirely in release builds.
- `app/src/main/kotlin/com/hermex/android/chat/ChatViewModel.kt` -- TTFT trace armed from
  `sendMessage()` itself (so `regenerate`/`retryLastMessage` re-arm it too); trace disarmed in
  `finalizeStream()` and `reattachStream()`.
- `app/src/main/kotlin/com/hermex/android/chat/ChatScreen.kt` -- first-rendered-frame timing
  mark.
- `app/src/main/kotlin/com/hermex/android/core/network/SseClient.kt` -- first-SSE-event timing
  mark.

## Known issue (not fixed in this release)

A pre-existing, unrelated crash (`IllegalArgumentException: Key "assistant-<timestamp>-<n>"
was already used`) can occur in the chat transcript's `LazyColumn` when two assistant messages
land with colliding timestamp-derived keys during rapid consecutive sends. Reproduced on both
the previous and this release's builds, so it predates this fix. Tracked as the next piece of
work.

## Previous release

See [v0.12.2-preview](#hermex-android-v0122-preview) below for the prior hotfix.

---

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
