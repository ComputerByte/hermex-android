# Licensing Report — Hermex Android

**Date:** 2026-07-17
**Repository:** github.com/ComputerByte/hermex-android
**Working tree:** `feature/play-readiness` @ `6be7414`

This report inventories licenses covering the Android client, its copied/adapted assets,
and its third-party dependencies. The owner selected the **MIT License** for the Android
project on 2026-07-16; the root `LICENSE` records that decision.

---

## 1. Upstream `hermes-webui`

- **Source:** `https://github.com/nesquena/hermes-webui` (also vendored at
  `/Users/byte/dev/hermes-webui` on this machine).
- **License file:** `LICENSE` at repo root, present and unmodified.
- **License:** **MIT License**.
- **Copyright:** "Copyright (c) 2025 Hermes Web UI Contributors".
- **Redistribution/modification:** Permitted, with the standard MIT notice preserved.
- **Attribution required:** MIT permission notice + copyright line in all copies / substantial
  portions. Standard `THIRD_PARTY_NOTICES` boilerplate is sufficient when attribution is
  bundled there.
- **Trademark/branding:** MIT does not automatically grant trademark rights. For this
  project, the owner has confirmed that `hermes-webui` permits the Hermes branding used
  by Hermex and that the projects are developed collaboratively; see
  `docs/BRANDING_PERMISSION.md`.

## 2. Copied or adapted code

| Source | Path in Android repo | Apparent license | Redistribution OK? | Notes |
|---|---|---|---|---|
| `hermes-webui` API contracts | `docs/`, `app/src/main/kotlin/com/hermex/android/core/network/dto/*.kt` (DTO shapes) | Inferred MIT (matches upstream) | Yes, with MIT notice | DTOs are reverse-engineered from API contract documents, not copied verbatim. Where identical, upstream copyright + MIT notice apply. |
| `hermes-webui` docs / onboarding text | `README.md`, `docs/ONBOARDING.md` (if present) | MIT | Yes, with notice | Adapted, not copied. |
| `HermesMobile` (iOS reference) | not vendored; `/Users/byte/Downloads/compare/hermex/HermesMobile/` was used as a UI reference only | unknown — no LICENSE file inspected | **Unknown** | Visual/UX patterns only; no iOS source was copied. Confirm before claiming. |
| This project (Android) | `app/src/main/kotlin/...` | MIT | Yes, with the root MIT notice | Copyright (c) 2026 Brent Duarte. |

## 3. Copied or adapted artwork / branding

| Asset | Source | Apparent owner | License | OK to ship? |
|---|---|---|---|---|
| `app/src/main/res/drawable-nodpi/hermes_app_icon.webp` | `hermes-webui` upstream assets | Hermes Web UI Contributors | MIT (inferred); branding permission confirmed | Yes, with attribution; see `BRANDING_PERMISSION.md`. |
| `app/src/main/res/drawable-nodpi/hermex_icon.png` | This project (Android) | Brent Duarte (Hermex project) | MIT | Yes, subject to any separate trademark rights. |
| `app/src/main/res/drawable-nodpi/hermes_wordmark_*.png` (fill_mask, highlight, outline_shadow, shading_overlay) | `hermes-webui` upstream assets | Hermes Web UI Contributors | MIT (inferred); branding permission confirmed | Yes, with attribution; see `BRANDING_PERMISSION.md`. |
| `app/src/main/res/mipmap-*/ic_launcher_*.png` (legacy) | This project (Android) | Brent Duarte (Hermex project) | MIT | Yes, subject to any separate trademark rights. |
| `app/src/main/res/drawable-nodpi/ic_launcher_foreground*.png` and `ic_launcher_monochrome.png` | Derived from upstream artwork + this project | upstream + this project | Mixed MIT sources; branding permission confirmed | Yes, with attribution; see `BRANDING_PERMISSION.md`. |
| `app/src/main/res/values/colors.xml` ic_launcher_background_* | This project (Android) | Brent Duarte (Hermex project) | MIT | Yes. |

**Branding / trademark status (independent of code license):** the owner confirmed
permission for the wordmark and upstream-derived icon artwork and confirmed that Hermex is
developed hand in hand with `hermes-webui`. The listing copy describes that collaboration
without claiming formal "official app" designation. See `docs/BRANDING_PERMISSION.md`.

## 4. Third-party dependencies (Android Gradle)

The release runtime resolves 132 artifacts. `THIRD_PARTY_NOTICES.md`, generated from
`:app:releaseRuntimeClasspath` on 2026-07-17, records the shipped component families,
resolved versions, upstream sources, applicable license texts, and the Hermes WebUI
attribution. Highlights:

| Dependency | License | Notes |
|---|---|---|
| AndroidX, Kotlin/Kotlinx, Coil, Google libraries, gRPC, Markwon, OkHttp/Okio/Retrofit, Apache HttpComponents/Commons, and JSpecify | Apache-2.0 | Full license text and resolved project versions are included in `THIRD_PARTY_NOTICES.md`. |
| `com.atlassian.commonmark:*:0.13.0` | BSD-2-Clause | Copyright (c) 2015, Robin Stocker. |
| `org.checkerframework:checker-compat-qual:2.5.5` | Dual GPL-2.0-with-Classpath-Exception / MIT | Hermex relies on the permissive MIT option; published metadata and the missing archive notice are documented explicitly. |
| Test-only and build-only dependencies | Various | Not shipped in the release runtime and excluded from the runtime inventory. |

The notice inventory should be regenerated whenever the release dependency graph changes.

## 5. Recommended next actions

1. **Expose the notices from the in-app About / open-source licenses surface** before
   public distribution so binary recipients can reach the same attributions.
2. **Preserve the branding-permission record.** Add a link to the original upstream
   approval in `docs/BRANDING_PERMISSION.md` when available and keep store copy within
   the confirmed scope.

---

## Root license decision — resolved

The owner selected the **MIT License** on 2026-07-16. The repository root now contains
`LICENSE` with "Copyright (c) 2026 Brent Duarte." This resolves the Android project's
previously undeclared license. The upstream and third-party notices are now recorded in
`THIRD_PARTY_NOTICES.md`.
