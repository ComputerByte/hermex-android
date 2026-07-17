# Hermex Android — Privacy Policy (Draft)

> **Status:** Draft for review. Not yet published. Owner must supply a contact email
> before this is hosted at a stable public URL.
>
> **Applies to:** the Hermex Android app distributed as `com.hermex.android`.
>
> **Last updated:** 2026-07-17

## 1. Overview

Hermex Android is an Android client for the
[Hermes](https://github.com/nesquena/hermes-webui) self-hosted AI server, developed in
collaboration with Hermes WebUI. The app does not provide a hosted service of its own.
It connects only to servers that the user (or someone on their behalf) has configured.
The user is the operator of those servers and is responsible for understanding what
those servers do with the data they receive.

The app itself:

- Does **not** include advertising or marketing SDKs.
- Does **not** include analytics, attribution, or telemetry SDKs.
- Does **not** include crash-reporting SDKs.
- Does **not** include any service that the app developer operates. There is no
  first-party backend, no account system, no user database, and no first-party
  push-notification service.

## 2. Data the app transmits to a user-configured server

When the user signs in to a server (manually or via a share-target / deep link), the
app transmits to that server only what is necessary to make the user-selected
conversation work:

- The user's chat messages and the assistant's responses streamed back.
- File and image attachments the user explicitly chooses to send, together with any
  metadata the server requires to handle them (typically the file name and MIME
  type).
- The user-supplied `Authorization` header (cookie or bearer token) that
  authenticates the session to that server.
- Any custom HTTP headers the user has configured in the app's settings for that
  server.
- The active workspace, model, model provider, and profile identifiers the user has
  selected for the session (only if those features are enabled on the server).

Anything the server does with that data — including retention, logging, training
on conversations, or onward sharing — is governed by the **server's** privacy and
terms policies, not this one. Hermex is not a party to that relationship.

## 3. Data the app stores on the device

The app stores the following data locally on the device, in private app storage
(`/data/data/com.hermex.android/`) and in the Android EncryptedSharedPreferences
keystore where applicable:

- **Server connection settings** — server URL, friendly name, and any per-server
  custom headers the user has configured. The user can view, edit, and delete each
  server entry.
- **Authentication credentials** — cookies and bearer tokens used to authenticate
  to each server. These are stored using `EncryptedSharedPreferences` backed by the
  Android keystore where the device supports it. They are **never** sent to any
  destination other than the server they authenticate to.
- **Cached conversations and message history** — for offline browsing and
  fast-scroll, the app may cache conversation content locally. This cache lives in
  the app's private storage and is removed when the user signs out of a server or
  uninstalls the app.
- **User preferences** — theme, font size, and other UI options. Some of these may
  use `EncryptedSharedPreferences` if they are sensitive.
- **Cached attachments** — files the user has viewed or sent. Removed on sign-out
  and on app uninstall.

The user can clear all of the above at any time by signing out of the affected
server, or completely by clearing the app's storage in Android Settings → Apps →
Hermex → Storage → Clear data, or by uninstalling the app.

## 4. Permissions

The app requests the following Android permissions. Each is used for the stated
purpose only and is requested at the time it is needed, not on install.

| Permission | When requested | Purpose |
|---|---|---|
| `INTERNET` | At install | The app must make network requests to the user-configured server. |
| `POST_NOTIFICATIONS` (Android 13+) | When the user enables background notifications in settings, or when the app first needs to post a notification | Post chat-related notifications (e.g. a long-running turn finishing while the user is in another app). If denied, notifications are simply not shown; the app continues to work normally. |
| `RECORD_AUDIO` | When the user taps the microphone in the chat composer | Capture voice input for transcription. Audio is streamed to the user-configured server for transcription and is not retained by the app. |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (legacy) | When the user picks a file to attach | Open the system file picker so the user can select an attachment. The app does not enumerate or read media outside the user's explicit selection. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | When the app needs to keep an in-flight chat stream alive while in the background | Keep the chat connection open during a long turn. The user can revoke backgrounding at any time via Android's system controls. |
| `RECEIVE_BOOT_COMPLETED` | At install | Re-arm scheduled background work (if enabled) after a device reboot. No data is read or transmitted. |

No other permissions are requested. The app does not request location, contacts,
SMS, call logs, camera (other than through the system file picker for images), or
device identifiers.

## 5. Insecure network connections (cleartext HTTP)

The app can be configured to connect to servers over plain HTTP. This is supported
specifically to make local self-hosting easy during development — for example,
running the Hermes server on a developer machine on the same Wi-Fi network.

**Cleartext HTTP is insecure.** Any data sent or received over a cleartext HTTP
connection, including chat content and credentials, can be observed or modified by
anyone on the network between the device and the server. The app shows a clear
security warning before completing a cleartext-HTTP connection and recommends HTTPS
or a local-trust setup. Use HTTPS, or a local network that you trust, or a VPN.

The app does not enforce HTTPS. The choice is the user's. If the user does choose
HTTP, the app still transmits only what the user explicitly types, attaches, or
configures — but it transmits it in the clear.

## 6. Custom HTTP headers

Advanced users can configure custom HTTP headers per server (for example, to send
a reverse-proxy token). Any value the user puts in a custom-header field is sent
verbatim to that server on every request. **The app does not validate or sanitize
custom-header values.** Users should not paste secrets that they would not be
comfortable typing into a normal HTTP header. Custom headers are stored in the
server's local settings (see Section 3).

## 7. Microphone / voice input

If the user uses voice input, the app captures audio through the standard Android
microphone API. The captured audio is streamed to the user-configured server for
transcription. The app does not retain the audio locally after the transcription
request completes. The app does not transmit audio to any destination other than
the user-configured server.

If microphone permission is denied, voice input is unavailable; the rest of the app
continues to work.

## 8. Notifications

If the user has granted `POST_NOTIFICATIONS` (Android 13+), the app posts
notifications for chat events such as a long-running turn completing while the user
is in another app. Notification content is taken from the local app state — the
notification surface is rendered by the Android system, not by Hermex.

If `POST_NOTIFICATIONS` is denied, the app does not post notifications; everything
else continues to work. The user can change this in the app's settings or in
Android Settings → Apps → Hermex → Notifications.

## 9. System file picker

When the user attaches a file, the app launches the Android system file picker
(`ACTION_OPEN_DOCUMENT` or the photo picker). The app only sees files the user
explicitly selects. It does not enumerate, read, or upload any other files on the
device.

## 10. What this app does **not** do

- It does not collect, transmit, or sell any data to Hermex or to any third party.
- It does not create a user account with Hermex.
- It does not provide account-creation, account-deletion, or account-recovery
  services directly. The user manages accounts (if any) on the server they
  configure.
- It does not perform background tracking, location tracking, or device-identifier
  collection.
- It does not run any background services that transmit data to anyone other than
  the user-configured server, and only when the user has explicitly enabled that
  feature.

## 11. Children

The app is a general-purpose tool for connecting to a user-configured server. It
does not target children as an audience. The Play Store listing should reflect
the recommended target audience and content rating chosen by the owner (see
`docs/play-store/LISTING_COPY.md`).

## 12. International transfers

Because the app does not operate its own backend, the only international data
transfer that can occur is the one the user causes by connecting to a server
outside their jurisdiction. That transfer is governed by the server's policies,
not by this app.

## 13. Changes to this policy

If the app's data handling changes materially (for example, a new optional
telemetry feature), this policy will be updated and the in-app "About" / "Privacy"
screen will show the new version. The Play Store listing will reflect the new
wording.

## 14. Contact

For privacy questions about the Hermex Android app, contact:

> **Email:** `<OWNER TO PROVIDE — replace with a real monitored inbox before hosting>`
>
> **Subject line prefix:** `[Hermex Android privacy]`

For privacy questions about the underlying Hermes server, contact the operator
of the server the user is connecting to.

---

## Checklist for hosting this policy at a stable public HTTPS URL

Before the Play Store listing goes live, the owner must:

1. [ ] Replace the `<OWNER TO PROVIDE>` placeholder in Section 14 with a real
       monitored email address.
2. [ ] Choose a stable host. The recommended options, in order of preference:
       1. A page on the project website (e.g. `https://hermex.example.com/privacy`).
       2. A page on the upstream `hermes-webui` documentation site (if the
          maintainer agrees to host it there).
       3. A page in the GitHub repository (e.g. served via GitHub Pages at
          `https://<org>.github.io/hermex-android/privacy`), with the canonical
          URL recorded here.
       4. **Avoid** posting the policy only inside the Play Console text field —
          Play requires a stable URL, not a copy-paste blob, and reviewers
          should be able to re-fetch the policy outside the Console.
3. [ ] Confirm the URL is reachable over HTTPS, returns `200 OK`, and serves this
       document as `text/markdown` or `text/html`. (If rendered as HTML, ensure
       headings, tables, and links survive the conversion.)
4. [ ] Add a "View privacy policy" entry in the in-app About screen linking to
       the same URL.
5. [ ] Record the final URL in `docs/PRIVACY_POLICY_URL.md` (TBD) so the
       Play Console, README, and the in-app About screen can be kept in sync.
6. [ ] Re-host / re-verify the URL at least once a year, or whenever this
       document is updated. A broken privacy-policy URL is a common Play
       rejection reason.
