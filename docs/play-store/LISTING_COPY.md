# Play Store Listing Copy — Hermex Android

> **Status:** Draft for owner review. Do not paste into Play Console until the privacy
> policy (see `docs/PRIVACY_POLICY_DRAFT.md`) is hosted at a stable public URL.
> The project license decision is complete (MIT; see `docs/LICENSING_REPORT.md`).
>
> **Last updated:** 2026-07-17

---

## App title (≤ 30 characters)

```
Hermex — Hermes client
```

Alternatives (any of these is also acceptable):

- `Hermex Android` (16 chars)
- `Hermex` (6 chars, but loses discoverability for "Hermes")

The current recommendation emphasizes the relationship to the Hermes server without
claiming a formal official-app designation.

## Short description (≤ 80 characters)

```
Android client for the self-hosted Hermes AI server, built with Hermes WebUI.
```

Char count: 77.

Alternatives:

- `Connect to your self-hosted Hermes AI server from Android.` (58 chars)
- `Hermes on Android, developed in collaboration with Hermes WebUI.` (64 chars)

The recommended wording reflects the confirmed collaboration while avoiding an unsupported
formal official-app claim.

## Full description (≤ 4000 characters)

```
Hermex is an Android client for Hermes, the self-hosted AI server, developed in
collaboration with Hermes WebUI. Connect to a Hermes server you control and use
your assistant from your phone — no account with us, no hosted service, no
telemetry.

What Hermex is
---------------
- An Android client for the open-source Hermes WebUI server (MIT licensed, see
  https://github.com/nesquena/hermes-webui).
- A thin shell that talks to a server URL you provide.
- Developed hand in hand with Hermes WebUI, with permission to use Hermes branding.

What Hermex is not
------------------
- Not a hosted service. The app does not run any backend.
- Not an analytics or advertising platform. There are no third-party SDKs that
  collect data from this app.
- Not a wrapper around someone else's commercial assistant.

What you can do
---------------
- Sign in to one or more Hermes servers by URL.
- Hold a familiar chat with your assistant, including streaming responses.
- Attach files and images (sent to the server you configured).
- Use voice input (microphone permission requested only when you tap the mic).
- Get a notification when a long-running turn finishes, if you grant the
  permission (Android 13+).
- Browse sessions, switch models / workspaces / profiles, and copy messages.
- Run a homescreen widget and use Hermex as a share target.

What you are agreeing to
------------------------
- Anything you type or attach is sent to the server you configured. The server
  operator controls what happens to it.
- If you connect over plain HTTP (for local self-hosting), the app warns you and
  recommends HTTPS or a trusted local network.
- See the in-app privacy policy for the full data-handling summary.

Permissions used
----------------
- INTERNET: talk to your server.
- POST_NOTIFICATIONS: optional, only if you want background turn-completion alerts.
- RECORD_AUDIO: only when you tap the mic for voice input.
- File access via the system picker: only what you pick.

Privacy and data
----------------
- No analytics, no ads, no telemetry SDKs, no crash-reporting SDKs.
- Authentication cookies and bearer tokens are stored in
  EncryptedSharedPreferences on your device, scoped to this app.
- Cached conversations, attachments, and server settings are stored in the app's
  private storage and removed when you sign out or uninstall the app.
- For the full policy, see the privacy-policy URL on the Play listing.

Open source
-----------
- Android client source: see the project repository (link in the developer
  website field).
- Upstream server: https://github.com/nesquena/hermes-webui (MIT).

Disclaimer
----------
Hermex is developed in collaboration with Hermes WebUI. The Hermes branding is
used with permission. Hermex is distributed as a separate Android project and
does not claim a formal official-app designation. If you are unsure whether you
should use this app to connect to a particular server, ask the operator of that
server.
```

Char count: ~2,400. Well under the 4,000 limit; room to expand with screenshots and
changelog.

## Project relationship disclosure

> **Hermex is an Android client for the self-hosted Hermes server, developed in
> collaboration with Hermes WebUI. The Hermes branding used by the app is used
> with permission. Hermex is separately distributed and does not claim formal
> official-app designation.**

This disclosure should appear in:
- The short description (where space allows)
- The full description (in the "What Hermex is" and "Disclaimer" sections above)
- The in-app About / Open-source licenses screen
- The developer website and privacy-policy pages

## Support email (placeholder)

```
<OWNER TO PROVIDE — replace with a real monitored inbox before Play submission>
```

## Website (placeholder)

```
https://<OWNER TO PROVIDE>
```

If the project has no website yet, GitHub Pages is acceptable for the privacy
policy URL even if a primary project website is not established. The Play Console
"Website" field can also be set to the GitHub repository URL in the interim.

## Reviewer access instructions

See `docs/play-store/REVIEWER_ACCESS.md` for two concrete options
(temporary reviewer server vs. built-in demo mode) and the recommended
trade-offs.

In short for the Play Console's "Notes to reviewer" field:

```
Hermex is an Android client developed in collaboration with Hermes WebUI. It does
not provide a hosted service. To exercise the app, the reviewer must point it at
a user-controlled Hermes server. See docs/play-store/REVIEWER_ACCESS.md in the
source repository for two supported access patterns, including a temporary server
the developer can provide on request.
```

---

## Data Safety questionnaire (worksheet)

The Play Console Data Safety form asks specific questions. The following worksheet
records the answers the developer should paste into the form. **All answers must be
confirmed by the owner before submission.** This worksheet does not submit itself.

| Play Data Safety question | Answer for Hermex | Reasoning |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | The app does not collect data to its own backend. It transmits user-supplied content to a user-configured third-party server, but Data Safety is about the developer's data practices, not the user's. The user's transmission to a third-party server is disclosed in the privacy policy but is not "the app collecting data". |
| Is all of the user data collected by your app encrypted in transit? | **Yes, when HTTPS is used; user must opt in to cleartext HTTP** | The app uses HTTPS by default. The user can configure a cleartext-HTTP server, in which case the connection is in the clear. The app shows a clear warning before completing such a connection. |
| Do you provide a way for users to request that their data is deleted? | **n/a — the app does not collect data** | If the user signs out of a server, all cached conversations, attachments, and per-server settings for that server are removed. |
| Is your app's data collection optional? | **n/a** | |
| Data safety section — App activity | **"No data collected"** | |
| Data safety section — Device or other IDs | **"No data collected"** | |
| Data safety section — App info and performance | **"No data collected"** | |

If the Play Console's taxonomy changes or the owner decides to instrument any
optional telemetry in the future, this worksheet must be updated **before** the
telemetry is enabled.

## Permission justification worksheet

| Permission | Justification (for the Play Console "Permission rationale" field) |
|---|---|
| `INTERNET` | The app's only function is to talk to a user-configured server. No network access is possible without this permission. |
| `POST_NOTIFICATIONS` (Android 13+) | Used to alert the user when a long-running chat turn completes while the user is in another app. The user can disable this in the app's settings or in Android's per-app notification settings; the app continues to function without it. |
| `RECORD_AUDIO` | Used only when the user taps the microphone icon in the chat composer. Audio is captured for the duration of the input and streamed to the user-configured server for transcription. The app does not retain audio locally after the request completes. |
| `READ_MEDIA_IMAGES` / legacy `READ_EXTERNAL_STORAGE` | Used to open the system file picker when the user attaches a file. The app only sees the file the user explicitly selects; it does not enumerate or read other media. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Used to keep an in-flight chat stream alive when the user backgrounds the app. The service is started and stopped by the app based on stream state; no background work happens without the user initiating a turn. |
| `RECEIVE_BOOT_COMPLETED` | Used to re-arm any user-enabled background work after a device reboot. No data is read or transmitted by this receiver. |

## Target-audience recommendation

| Field | Recommendation | Rationale |
|---|---|---|
| Target age group | 18+ (or "Not for children", depending on the Console wording) | The app is a developer-facing / power-user tool for connecting to a self-hosted server. The audience is technical adults. The app does not target children and does not include child-directed content. |
| Content rating | "Everyone" or "Teen" — to be confirmed by the owner | No violent, sexual, or otherwise restricted content is displayed by the app itself. The content that flows through the app is determined by the user's server and model. The conservative choice is "Teen" given that the app is a general-purpose interface to a chatbot. |
| Category | **Productivity** or **Developer tools** | "Productivity" is the conventional choice for chat / assistant apps. "Developer tools" is more honest given the self-hosting requirement, but reduces the audience. |
| Ads | **No** | The app has no ad SDKs and shows no ads. |
| In-app purchases | **No** | The app has no IAPs. |
| Contains ads (Play Console) | **No** | |
| Is your app a "Health" or "Medical" app? | **No** | |

## Account-deletion applicability

The Play Console asks whether the app provides an account-deletion mechanism. For
Hermex:

- The app does **not** create, store, or manage user accounts. There is no
  first-party account.
- The app **may** store credentials (cookies, bearer tokens) for one or more
  user-configured third-party servers. These are local to the device and to this
  app.
- "Account deletion" in the Play Console sense does not apply: there is no
  developer-side account to delete.
- The user can remove the app's locally stored credentials and cache at any time
  by signing out, clearing the app's data in Android Settings, or uninstalling
  the app. This should be described in the privacy policy and the in-app About
  screen.

The recommended Play Console answer is: **"Account deletion is not applicable
because the app does not provide user accounts."** Owner should confirm wording
in the Console's actual interface.

## What this listing copy does **not** claim

- ❌ "Official Hermes client" unless the upstream project explicitly designates it so
- ❌ Claims extending the confirmed collaboration or branding permission beyond scope
- ❌ Background streaming as a permanent feature (it is, in fact, optional and
  user-controllable)
- ❌ Production stability guarantees beyond "this is a v0.12.x preview hotfix"
- ❌ Compatibility with a specific model provider, since the server side decides
  that
- ❌ Any unsupported functionality. The description mirrors what the app
  actually does.
