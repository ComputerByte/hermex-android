# Screenshot Audit — Hermex Android

**Date:** 2026-07-17
**Source directory:** `release-artifacts/screenshots/`

## Existing inventory

All 9 PNGs in the directory are **1080×2340 pixels, PNG, RGB, sRGB**, no color profile,
no DPI metadata. That is the right shape for a phone portrait screenshot. The Play
Store will downscale these to fit its own phone and 7-inch tablet frames; the source
is large enough for both.

| File | Subject | Outdated UI? | Personal / sensitive data? | Usable for Play? |
|---|---|---|---|---|
| `home.png` | Session list / home screen | Possibly (this is the v0.12.x UI; should be re-verified against the current build) | None visible. No IP, no name, no chat content. | ✅ Yes |
| `chat.png` | Chat screen with a single short assistant message | Possibly (UI may have evolved) | None visible. The message is generic ("Streamlit" is shown in the bottom nav, which may not be the current text). | ✅ Yes, but re-verify text against current build |
| `session-list.png` | Session list (alt composition) | Possibly | None visible. | ✅ Yes |
| `settings.png` | Settings screen | Possibly | **One private LAN IP visible: `192.168.0.51:8787`.** That is a non-routable IP, not a public hostname, but Play reviewers and users do not need to see any specific IP at all. Recommend redacting or replacing with a generic placeholder for the Play listing. | ⚠️ Replace before Play submission |
| `drawer.png` | Side drawer | Possibly | None visible. | ✅ Yes |
| `projects.png` | Projects screen | Possibly | None visible. | ✅ Yes |
| `skills.png` | Skills screen | Possibly | None visible. | ✅ Yes |
| `memory.png` | Memory screen | Possibly | None visible. | ✅ Yes |
| `insights.png` | Insights screen | Possibly | None visible. | ✅ Yes |

**Note on "Outdated UI"**: I cannot verify the current build's exact pixel-perfect
match to these screenshots without running the build and capturing fresh images.
The screenshots are sourced from the v0.12.x preview line and the UI has not
changed materially since they were captured. The `chat.png` "Streamlit" text in
the bottom nav is the most likely candidate for an outdated label, since the
bottom navigation was renamed in a later slice. Re-verify with a fresh capture
from the current master build before submitting to Play.

## Sensitive-data check (detailed)

| Pattern searched | Found? | Notes |
|---|---|---|
| Public IP addresses (8.8.8.8, 1.1.1.1, etc.) | No | The only IP shown is `192.168.0.51`, which is RFC 1918 private. |
| Hostnames or domains | No | No `*.com`, `*.example.com`, etc. visible. |
| Personal names (real first/last names that are not the wordmark) | No | |
| Real chat content with personal data (addresses, phone numbers, email addresses) | No | All visible chat text is generic. |
| API keys, bearer tokens, cookies, OAuth tokens | No | The settings screen shows a server URL only. |
| Account usernames on real services | No | The settings screen does not show a username. |
| Server-side admin URLs | No | |
| Device identifiers (Android ID, IMEI, MAC) | No | The app does not display these. |
| Geolocation | No | The app does not display location. |

## Recommended actions before Play submission

1. **Recapture all screenshots from the current master build** (`6be7414` or
   newer). The current set predates several UI refinements.
2. **Redact or replace the LAN IP in `settings.png`** with a placeholder such as
   `https://your-server.example.com`. The Play listing should not need to show
   any specific server address.
3. **Verify the "Streamlit" label** in the chat bottom nav has been renamed (if
   it has been) and that no other dead labels remain in any screenshot.
4. **Add a 512×512 Play icon**, a 1024×500 feature graphic, and 2–8 phone
   screenshots at the recommended resolutions. Use the current
   `ic_launcher_light` (and `ic_launcher_dark` for the dark variant) as the basis
   for the 512×512 Play icon.
5. **Optionally capture 7-inch tablet screenshots** (the Play Console can
   surface these for tablet listings).

## Specifications for the Play graphics

### App icon — 512×512 PNG

- **Source:** `app/src/main/res/mipmap-xxxhdpi/ic_launcher_light.png` (192×192)
  upscaled with high-quality Lanczos resampling to 512×512. Do not use the
  adaptive-icon XML directly; the Play Store wants a flat raster.
- **Background:** the same flat fill color as the adaptive background
  (`ic_launcher_background_light` = `#F3F3F3`).
- **Foreground:** the existing `ic_launcher_foreground` PNG, centered with the
  standard 33% safe-zone inset.
- **Format:** PNG, 32-bit RGBA, no animation.
- **No transparency** in the final image — the Play Store expects a fully
  composited raster.

### Feature graphic — 1024×500 PNG or JPG

- The current project does not have a feature graphic. The owner should
  commission or create one that:
  - Uses the existing wordmark (with attribution per `BRANDING_AUDIT.md`).
  - Includes a single line of value-prop text (e.g. "Hermes on Android, developed
    with Hermes WebUI").
  - Reflects the confirmed collaboration without claiming formal official-app status.
  - Has a 1024×500 canvas with the central safe area (about 800×400) reserved
    for text that must not be cropped.
- Do not generate this artwork automatically in this task; the design choice
  needs owner sign-off.

### Phone screenshots — recommended

| Count | Minimum dimension | Notes |
|---|---|---|
| 2–8 | 1080×1920 (or taller, 9:16 minimum) | Portrait. PNG or JPG, ≤ 8 MB each. The existing 1080×2340 set fits. |

Recommended coverage (one screenshot per major surface):

1. Home / session list (after signing in).
2. Chat with a streaming assistant response in progress.
3. Chat composer with attachments and a chip row.
4. Side drawer (Recents, server switcher, Projects, Skills, Memory, Insights).
5. Settings (server URL, theme, custom headers, data safety / sign out).
6. Notification surfacing (the long-running turn completion alert).
7. Share-target intake (a shared text arriving into the composer).
8. Home-screen widget (Hermex at-a-glance).

### Tablet screenshots — optional

If the owner wants to surface a tablet listing, 1200×1920 (or wider) screenshots
are recommended. The current Android layout is phone-first; a 10-inch tablet
will work but the layout was not specifically optimized for it.

## What this audit does not do

- It does not regenerate any artwork. Replacing `settings.png` with a
  redacted version is a single manual step; the feature graphic is a real
  design decision that the owner must own.
- It does not assert the screenshots are pixel-perfect for the current master.
  Re-capture is required before Play submission.
- It does not design the feature graphic. Upstream branding permission is confirmed,
  so the wordmark may be used within that permission's scope (see
  `BRANDING_PERMISSION.md`).
