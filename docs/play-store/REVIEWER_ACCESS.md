# Reviewer Access Plan — Hermex Android

> **Status:** Plan, not yet executed. The owner must choose between Option A and
> Option B (or a hybrid) before Play submission. **This plan does not implement
> demo mode in this branch — see Option B for the scoping analysis only.**

The app requires a user-supplied Hermes server. A Google Play reviewer cannot
complete the sign-in flow without one, and the app's behavior depends on what the
server does. This document records two supported access patterns and the
trade-offs.

---

## Option A — Temporary reviewer server (recommended)

Provide Google reviewers with a temporary, dedicated, time-limited server that is
configured with non-sensitive demo data and that the developer can rotate or
destroy after the review is complete.

### What to set up

1. **A short-lived HTTPS endpoint.**
   - Use a dedicated subdomain on a domain the developer controls. For example,
     `review.hermex.example.com` or `play-review.hermex.example.com`.
   - Serve the Hermes WebUI server image (`hermes-webui:latest` or the current
     stable tag) with a fresh configuration.
   - **TLS:** use a real public certificate (Let's Encrypt is sufficient and free).
     Do not use self-signed certificates; the app's strict-HTTPS mode would block
     them without a per-server trust override.
   - **Auth credentials:** generate a fresh username and password for the review
     period. Do not reuse any other account's credentials.
   - **Demo data:** seed the server with 2–3 short, **non-sensitive** chat
     sessions. Use synthetic assistant responses that contain no real personal
     data, no real source code, no real API keys, and nothing that would be
     embarrassing if surfaced in a screenshot.

2. **A shared sign-in card.**
   - Send the reviewer (via the Play Console's "Notes to reviewer" field and via
     email if the Play Console supports it) the following:
     - The server URL: `https://<review-subdomain>`.
     - A username and password.
     - A one-line summary of what the demo data shows ("three pre-seeded chat
       sessions, one with a long-form completion to demonstrate streaming").
   - State clearly that the credentials are time-limited and that the developer
     will rotate them after review.

3. **Rotation and tear-down.**
   - Rotate the reviewer's password before and after the review window.
   - After the review is complete (review status is "Approved" or "Rejected"),
     shut down the review server and revoke the credentials.
   - Keep a written record of the rotation timestamps for compliance.

4. **What the reviewer can test.**
   - Sign in with the provided credentials.
   - Open the pre-seeded sessions and verify chat rendering, copy, code blocks,
     attachments, tool-call cards, reasoning blocks.
   - Send a new message and watch streaming.
   - Tap Stop, then Edit + Resend, then Regenerate (this is the v0.12.2-preview
     hotfix path).
   - Open the side drawer, switch servers, change settings.
   - Test the home-screen widget if it is in scope for this release.
   - Use the share-target intent.
   - Verify notifications work after granting `POST_NOTIFICATIONS` and do not
     crash after denying it.
   - Verify the adaptive launcher icon renders correctly on Android 8+ and that
     themed icons (Android 13+) pick up the monochrome layer.

### Pros

- Tests the real app against a real server. The most representative verification.
- The reviewer can exercise the app's networking, streaming, cancel/resume, and
  notification paths against real-world conditions.
- No production code is touched.

### Cons

- Requires a publicly reachable host with TLS for the duration of the review.
- The reviewer's actions (sign-in, chat messages) are observable on that
  server. The developer should write a clear notice in the reviewer notes that
  the server is dedicated and time-limited.
- If the review takes longer than expected, the server stays up longer.
- The reviewer may inadvertently send data into the demo server; that data
  lives until rotation. Do not put anything sensitive on the server.

### Security and reliability trade-offs

- **Confidentiality:** low risk if the demo data is non-sensitive. The reviewer
  sees the demo chats but not any real user data.
- **Integrity:** the reviewer can post any text and any attachment to the
  server. That's fine; the server is throwaway.
- **Availability:** the demo server may be flaky. Run a health check before the
  review window opens and have a backup plan (e.g. swap the DNS to a
  pre-warmed standby).
- **Maintenance:** ~30 minutes of one-time setup per review submission.

---

## Option B — Built-in demo mode (NOT implemented in this task)

A "demo mode" inside the app would let the reviewer sign in without a real
server, by routing requests to a built-in mock or to a stub. This is sometimes
used for apps that are entirely client-side.

### Scope (assessed, not implemented)

To provide a useful demo, the app would need to:

1. Detect a "demo mode" flag (e.g. an extra `HermexDemoMode` build flavor or a
   `BuildConfig` flag set in a `demo` product flavor).
2. Provide a fake `HermesApi` implementation that returns canned responses for
   `chatStart`, `chatStream`, `truncateSession`, `session`, etc.
3. Provide a fake `SseClient` that emits canned SSE events on a timer, including
   token, reasoning, tool-call, and done events.
4. Persist the demo sessions locally so the reviewer can see the session list
   populated.
5. Render all UI states (composer, stop button, error toasts, notifications) the
   same way the real server path would render them.

### Estimated effort

A representative demo mode is **not small**. The chat streaming and cancel/resume
lifecycle is the part of the app with the most subtle state, and reproducing it
faithfully enough for a reviewer to evaluate the UI requires at least 1–2 days
of focused work, plus tests, plus a build flavor split, plus ongoing maintenance
to keep the demo in sync with the real path.

### Risks

- **Drift.** A demo mode that does not match the real path is worse than no
  demo. The reviewer approves something the real app does not do, or misses a
  bug the demo path hides.
- **Maintenance cost.** Every change to `HermexApi`, `SseClient`, or
  `ChatViewModel` has to be mirrored in the demo's stubs. This is a tax on
  future development.
- **Code bloat.** Adding a parallel mock implementation to a non-trivial
  surface area is a real maintenance burden, not a small one.
- **Reviewer confusion.** A reviewer can only evaluate the demo, not the real
  product. The review may approve something that misrepresents how the app
  behaves against a real server.

### Recommendation

**Do not implement demo mode in this task.** It is not "small, isolated, and
approved separately" — it is a substantial parallel implementation. The
recommended path is Option A.

If the owner still wants a demo mode in the future, do it as its own branch and
its own task, with explicit acceptance criteria, and accept the ongoing
maintenance cost in writing.

---

## Choosing between the options

| Criterion | Option A (reviewer server) | Option B (demo mode) |
|---|---|---|
| Realism | High — reviewer tests against a real server | Low — reviewer tests against canned responses |
| Setup cost | ~30 min one-time | 1–2 days + ongoing maintenance |
| Maintenance cost | None after tear-down | High — every API change has to mirror the demo |
| Review reliability | High | Medium — easy to ship a demo that disagrees with the real app |
| Security risk | Low if demo data is non-sensitive | None (no real server) |
| User-data leakage | Possible only into the throwaway server | None (no real server) |
| Future compatibility | Same as the real app | Independent of the real app's evolution |
| Best for | This submission (v0.12.2-preview / first Play submission) | Hypothetical future offline showcase |

**Recommendation: Option A for the next Play submission.** Re-evaluate if the
Play review process changes or if the cost of standing up a TLS endpoint
becomes blocking.

## What the developer needs to do to enable Option A

1. Provision a short-lived subdomain and TLS certificate.
2. Run a Hermes server with non-sensitive demo data.
3. Generate a fresh reviewer account on that server.
4. Paste the URL and credentials into the Play Console "Notes to reviewer" field
   (or attach them in the Play Console's reviewer-asset area if available).
5. After the review resolves, rotate the credentials and shut down the
   reviewer server.

## What the developer needs to do to enable Option B (if approved later)

- Create a new branch with a `demo` product flavor.
- Implement the demo stubs as listed in "Scope".
- Add tests covering the demo's contract.
- Document the demo's divergence from the real path in
  `docs/play-store/REVIEWER_ACCESS.md` (update this file).
- Add a "Demo mode" entry in the in-app About screen so users know when they
  are running the demo.
