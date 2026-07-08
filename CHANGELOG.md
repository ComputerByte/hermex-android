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
