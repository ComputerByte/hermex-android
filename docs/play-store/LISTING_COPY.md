# Play Store Listing Copy — Hermex Android

> **Status:** Draft for owner review. Do not paste into Play Console until the privacy
> policy (see `docs/privacy/index.md`) is hosted at its stable public URL.
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
- Authentication cookies are encrypted using a key protected by the Android
  Keystore. Custom headers, including bearer tokens, remain in Android private app
  storage but are not separately encrypted at the application layer.
- Cached conversations and server settings are stored in the app's private storage.
  Sign out clears the selected server's authentication cookie; Forget Server removes
  that server's configuration, custom headers, and conversation cache.
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

## Support email

```
brentduarte28@gmail.com
```

## Website

```
https://github.com/ComputerByte/hermex-android
```

## Privacy policy

```
https://computerbyte.github.io/hermex-android/privacy/
```

The GitHub Pages URL must be enabled and verified after these changes reach the
repository's default branch and before it is entered in Play Console.

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
It follows Google's current definition of collection as transmitting data off-device:
https://support.google.com/googleplay/android-developer/answer/10787469

| Play Data Safety question | Answer for Hermex | Reasoning |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes — collects; user-initiated transfers are not treated as sharing** | Google defines collection as transmitting data off-device, regardless of whether it goes to the developer or a third-party server. Hermex sends chat content and optional attachments to the selected server, and Android's speech-recognition provider may process optional voice input. These transfers are initiated by the user and are not sold. |
| Is all of the user data collected by your app encrypted in transit? | **No** | Hermex supports HTTPS but also permits cleartext HTTP for trusted LAN servers after a warning. The Console question is all-or-nothing, so the accurate answer is No. |
| Do you provide a way for users to request that their data is deleted? | **No developer-hosted deletion service** | Forget Server and Android Clear Data delete local data. Data retained by a configured server must be deleted through that server or its operator; speech-recognition data is controlled by that provider. |
| Is your app's data collection optional? | **Mixed** | Server authentication and chat messages are required to use the core client. Attachments and voice input are optional. |
| Messages → Other in-app messages | **Collected; app functionality; required for chat; not shared under the user-initiated-transfer exception** | Chat content is sent to the user-selected server. |
| Photos and videos → Photos / Videos | **Collected only when attached; app functionality; optional** | Only media explicitly selected by the user is uploaded. |
| Audio files → Voice or sound recordings | **Collected only when voice input is used; app functionality; optional; may be processed ephemerally** | Android's installed speech-recognition service may send audio to its provider. |
| Files and docs | **Collected only when attached; app functionality; optional** | Only files explicitly selected by the user are uploaded. |
| App activity → Other user-generated content | **Collected when applicable; app functionality; optional** | Open-ended content such as workspace edits may be sent to the configured server. |
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
| `RECORD_AUDIO` | Used only when the user taps the microphone icon in the chat composer. Audio is supplied to Android's installed speech-recognition service. Depending on the device and provider, recognition may happen on-device or remotely. Hermex does not create a persistent audio recording. |

Attachments use Android's system picker. The release manifest does not request
`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `FOREGROUND_SERVICE`, or
`RECEIVE_BOOT_COMPLETED`.

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
- The user can remove the selected server's cookie by signing out. Forget Server
  also removes its configuration, custom headers, and cache. Android Clear Data or
  uninstall removes all app-local data.

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
