---
layout: default
title: Hermex Android Privacy Policy
---

# Hermex Android — Privacy Policy
>
> **Applies to:** the Hermex Android app distributed as `com.hermex.android`.
>
> **Developer:** Brent Duarte
>
> **Last updated:** 2026-07-17
>
> **Canonical URL:** https://computerbyte.github.io/hermex-android/privacy/

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
feature work:

- The user's chat messages and the assistant's responses streamed back.
- File and image attachments the user explicitly chooses to send, together with any
  metadata the server requires to handle them (typically the file name and MIME
  type).
- A server password the user enters during login. The password is sent to the chosen
  server for authentication but is not stored by the app. Any session cookie returned
  by the server may be stored for later requests.
- The user-supplied `Authorization` header (session cookie or bearer token) that
  authenticates requests to that server.
- Any custom HTTP headers the user has configured in the app's settings for that
  server.
- The active workspace, model, model provider, and profile identifiers the user has
  selected for the session (only if those features are enabled on the server).

Anything the server does with that data — including retention, logging, training
on conversations, or onward sharing — is governed by the **server's** privacy and
terms policies, not this one. Hermex is not a party to that relationship.

## 3. Data the app stores on the device

The app stores the following data locally in Android private app storage:

- **Server connection settings** — server URL, friendly name, and any per-server
  custom headers the user has configured. Server settings and custom headers use
  Android DataStore in the app's sandbox. Custom headers, including a bearer token
  entered as a header value, are not separately encrypted at the application layer.
- **Authentication cookies** — session cookies are encrypted using
  `EncryptedSharedPreferences` with a key protected by the Android Keystore. They are
  sent only to the server scope that created them.
- **Cached conversations and message history** — for offline browsing and
  fast-scroll, the app may cache conversation content in a local Room database,
  isolated by server.
- **User preferences** — theme, font size, and other UI options. Some of these may
  be stored in Android DataStore.

The app disables Android application backup. Selected attachment bytes are held in
memory while they are uploaded; Hermex does not create its own durable local copy of
the selected file. The receiving server may retain an uploaded attachment under its
own policy.

### Retention and deletion

- The local conversation cache is pruned on startup to the 50 most recent sessions
  or 90 days, whichever removes more old sessions.
- **Sign out** clears the selected server's authentication cookie. It intentionally
  leaves the server entry, custom headers, and offline conversation cache available
  for later use.
- **Forget server** removes that server's configuration, authentication cookies,
  custom headers, and cached conversations from the device.
- Clearing Hermex's storage in Android Settings or uninstalling Hermex removes all
  app-local data.
- Data retained by a user-configured server must be deleted through that server or
  its operator. Hermex and its developer cannot delete data held by an independently
  operated server.
- Speech-recognition data, if processed by the device's speech-recognition provider,
  is subject to that provider's retention and deletion practices.

## 4. Permissions

Hermex may request or declare the following Android permissions to provide core
functionality:

### Internet (`INTERNET`)

Used to communicate with AI servers that you configure.

Hermex supports connections to both HTTPS servers and, where enabled by the
application, HTTP servers. Because HTTP connections are not encrypted in transit,
users should only connect to trusted servers when using HTTP.

### Notifications (`POST_NOTIFICATIONS`)

Used to display notifications generated by the application.

### Microphone (`RECORD_AUDIO`)

Used only when you choose to use voice input or speech recognition features. Speech
recognition is provided by your device's configured recognition service, which may
process audio on-device or transmit it to its own servers according to its own
privacy practices.

### Foreground Service (`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`)

Declared so Hermex can perform long-running tasks, such as maintaining active
synchronization or streaming operations while the app is in use. These are normal
Android permissions and are not runtime permissions that require user approval.

The app uses Android's system document or photo picker for attachments and therefore
does not request broad storage or media-library permission. It does not request
location, contacts, SMS, call logs, camera, or device-identifier permissions.

## 5. Network Security

Hermex communicates only with AI servers that you explicitly configure.

The application supports secure HTTPS connections and may also permit HTTP
connections, depending on your server configuration. HTTPS encrypts data in transit.
HTTP does not provide transport encryption and should only be used with servers and
networks that you trust.

All conversation data, attachments, and requests are transmitted only to the server
that you choose to connect to. Hermex does not route your requests through
developer-operated servers.

## 6. Custom HTTP headers

Advanced users can configure custom HTTP headers per server (for example, to send
a reverse-proxy token). Any value the user puts in a custom-header field is sent
verbatim to that server on every request. Header names and values are trimmed and
invalid blank rows are discarded, but Hermex does not determine whether a header
value is safe or appropriate for the selected server. Custom headers are stored in
Android private app storage but are not separately encrypted at the application
layer (see Section 3).

## 7. Microphone / voice input

If the user taps voice input and grants microphone permission, Hermex invokes
Android's installed speech-recognition service using the standard `SpeechRecognizer`
API. Depending on the device and the selected recognition service, speech may be
processed on the device or transmitted to that service's provider. That processing
is governed by the provider's privacy policy and device settings.

Hermex receives the resulting text and places it in the message composer. The text
is not sent to the user-configured Hermes server until the user sends the message.
Hermex does not create a persistent audio recording, but it cannot control whether
the installed speech-recognition service retains data.

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

- It does not transmit user data to a backend operated by the Hermex Android
  developer.
- It does not sell user data.
- It does not create a user account with Hermex.
- It does not provide account-creation, account-deletion, or account-recovery
  services directly. The user manages accounts (if any) on the server they
  configure.
- It does not perform background tracking, location tracking, or device-identifier
  collection.
- It does not run background analytics, advertising, or tracking services.

## 11. Children

The app is a general-purpose tool for connecting to a user-configured server. It
does not target children as an audience.

## 12. International transfers

Because the app does not operate its own backend, international data transfers may
occur when the user connects to a server outside their jurisdiction or when the
device's speech-recognition provider processes optional voice input elsewhere.
Those transfers are governed by the configured server operator's or recognition
provider's policies.

## 13. Changes to this policy

If the app's data handling changes materially (for example, a new optional
telemetry feature), this policy and the in-app privacy-policy link will be updated.
The Play Store listing will reflect the new wording.

## 14. Contact

For privacy questions about the Hermex Android app, contact:

> **Developer:** Brent Duarte
>
> **Email:** [brentduarte28@gmail.com](mailto:brentduarte28@gmail.com)
>
> **Subject line prefix:** `[Hermex Android privacy]`

For privacy questions about the underlying Hermes server, contact the operator
of the server the user is connecting to.

---

This policy is intended to remain publicly available at its canonical HTTPS URL.
It should be reviewed whenever Hermex's data handling changes.
