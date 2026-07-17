# Branding and Official-Status Audit — Hermex Android

**Date:** 2026-07-17
**Scope:** Identify the origin and licensing of every branded asset shipped in the
Hermex Android app, and assess the risk of claims about official status.

---

## Inventory of branded assets

| Asset | File | Apparent origin | Apparent owner | Code license | Trademark licensed? | Notes |
|---|---|---|---|---|---|---|
| Hermes app icon (webp) | `app/src/main/res/drawable-nodpi/hermes_app_icon.webp` | `hermes-webui` upstream | Hermes Web UI Contributors | MIT (inferred from upstream) | **Yes — owner confirmed** | Used as source artwork for derived launcher icons. See `BRANDING_PERMISSION.md`. |
| Hermex icon (png) | `app/src/main/res/drawable-nodpi/hermex_icon.png` | This project (Android) | Brent Duarte (Hermex project) | MIT | **Owner-controlled** | Used as source artwork for foreground drawables. |
| Hermes wordmark — fill mask | `app/src/main/res/drawable-nodpi/hermes_wordmark_fill_mask.png` | `hermes-webui` upstream | Hermes Web UI Contributors | MIT (inferred) | **Yes — owner confirmed** | Wordmark used in splash/welcome. See `BRANDING_PERMISSION.md`. |
| Hermes wordmark — highlight | `app/src/main/res/drawable-nodpi/hermes_wordmark_highlight.png` | `hermes-webui` upstream | Hermes Web UI Contributors | MIT (inferred) | **Yes — owner confirmed** | Same. |
| Hermes wordmark — outline shadow | `app/src/main/res/drawable-nodpi/hermes_wordmark_outline_shadow.png` | `hermes-webui` upstream | Hermes Web UI Contributors | MIT (inferred) | **Yes — owner confirmed** | Same. |
| Hermes wordmark — shading overlay | `app/src/main/res/drawable-nodpi/hermes_wordmark_shading_overlay.png` | `hermes-webui` upstream | Hermes Web UI Contributors | MIT (inferred) | **Yes — owner confirmed** | Same. |
| Adaptive launcher icon foreground | `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | Derived from `hermes_app_icon.webp` | upstream + this project | MIT (inferred for the source) | **Yes — owner confirmed** | Composed from permitted upstream artwork. |
| Adaptive launcher icon foreground (disco) | `app/src/main/res/drawable-nodpi/ic_launcher_foreground_disco.png` | Derived from `hermes_app_icon.webp` (disco variant) | upstream + this project | MIT (inferred) | **Yes — owner confirmed** | Same. |
| Adaptive launcher icon monochrome | `app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png` | Derived from `hermes_app_icon.webp` (single-color) | upstream + this project | MIT (inferred) | **Yes — owner confirmed** | Used for Android 13+ themed icons. |
| Adaptive icon backgrounds (color) | `app/src/main/res/values/colors.xml` `ic_launcher_background_*` | This project | Brent Duarte (Hermex project) | MIT | n/a | Just fill colors. |
| Legacy launcher icons (PNG, mipmap-mdpi..xxxhdpi) | `app/src/main/res/mipmap-*/ic_launcher_*.png` | This project | Brent Duarte (Hermex project) | MIT | **Owner-controlled** | Pre-adaptive-era raster. |
| Adaptive launcher icons (XML, anydpi-v26) | `app/src/main/res/mipmap-anydpi-v26/ic_launcher_*.xml` | This project | Brent Duarte (Hermex project) | MIT | **Owner-controlled** | Wraps foreground / background / monochrome. |
| App name string | `app/src/main/res/values/strings.xml` `app_name` | This project | Brent Duarte (Hermex project) | MIT | **Owner-controlled** | Currently "Hermex" — distinct from upstream "Hermes". |
| "Hermes" name in code / comments | various | `hermes-webui` upstream | Hermes Web UI Contributors | n/a — name reference | **Yes — owner confirmed** | References the upstream server project within the confirmed collaboration. |

## Trademark and branding

- **MIT** (and any permissive code license) does **not** grant trademark rights. The
  copyright owner of `hermes-webui` and any entity that holds a "Hermes" trademark have
  independent control over the name and any logo.
- The Android app's name is "Hermex" — distinct, which avoids one layer of confusion
  (the user-facing app name does not claim to be "Hermes"). The internal name
  reference to "Hermes" (server, API) is descriptive, not a brand claim.
- The launcher icon (foreground, monochrome) and the wordmark are derived from upstream
  artwork. The project owner confirmed permission to use that branding and confirmed
  that Hermex is developed hand in hand with `hermes-webui`; see
  `docs/BRANDING_PERMISSION.md`.

## Claims to avoid in Play listing / app copy

- ❌ "Official Hermes client" unless the upstream project explicitly designates it so
- ❌ "Published by the Hermes team" unless the publisher account and authorship support it
- ❌ "Hermes™ for Android" (using the ™ mark implies a trademark claim that the Android
  project does not own)
- ❌ Claims that extend the confirmed collaboration or branding permission beyond its
  actual scope

## Safe wording for the Android app

- ✅ "Hermex — Android client for the Hermes server"
- ✅ "Developed in collaboration with Hermes WebUI"
- ✅ "Hermes branding used with permission"
- ✅ "Connect to a Hermes server you control"
- ✅ "Compatible with the open-source Hermes WebUI server"
- ✅ Listing the upstream server URL and an attribution line in the app description
  and in the in-app About screen
- ✅ Calling the server "the Hermes server" descriptively (a server, not a brand)

## Recommended actions

1. **Branding permission confirmed.** The owner confirmed that the upstream project
   permits the launcher-icon artwork and wordmark and that the projects are developed
   collaboratively. The attestation is recorded in `BRANDING_PERMISSION.md`; add a link
   to the original upstream record there when available.
2. **Add an "About" / "Open-source licenses" screen** in-app that credits
   `hermes-webui` (MIT, "Copyright (c) 2025 Hermes Web UI Contributors") and lists
   third-party notices. This is also required by Apache-2.0 dependencies.
3. **Describe the project relationship accurately on the Play listing:** Hermex is
   developed in collaboration with `hermes-webui` and has permission to use its branding.
   Do not claim formal "official app" status unless upstream explicitly designates it so.
4. **Do not introduce new branding** in this task. The launcher-icon work in this
   branch reuses existing source artwork; it does not invent a new logo.
5. **No rename or visual rebrand is required for branding-permission reasons.** Revisit
   only if the scope of upstream permission or the collaboration changes.

## Status

- **Done:** upstream branding permission and collaboration confirmed by the owner; see
  `BRANDING_PERMISSION.md`.
- **Done:** the owner selected MIT and the root `LICENSE` records the decision.
- **Done:** `THIRD_PARTY_NOTICES.md` records the upstream and resolved runtime licenses.
- **Done:** launcher-icon work uses existing source artwork without inventing branding.
- **Done:** store-listing copy describes the collaborative relationship without claiming
  formal official-app status (see `docs/play-store/LISTING_COPY.md`).
