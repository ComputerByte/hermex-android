# Git Workspace Write Actions — Defer Decision

## Decision: DEFERRED to 1.1

## Rationale
Per slice-14 guidance, the **defer** path is the recommended default for 1.0:
- Destructive actions (commit, discard, checkout) expand QA surface right before release
- Backend endpoint compatibility not verified
- Existing Git UI is read-only (status, diff, branches) — this is the minimum useful surface
- No existing dead commit/discard/checkout controls in current 1.0 codebase

## 1.0 Git Capabilities
- **Read-only:**
  - `GET /api/git/status` — file changes
  - `GET /api/git/diff` — diff viewer
  - `GET /api/git/branches` — branch list (read-only)
- **Not in 1.0 (deferred to 1.1):**
  - Commit
  - Discard file changes
  - Checkout branch

## UI Changes for 1.0
- Added "read-only" label in Git status card header
- Existing code comment: `// ── Git (read-only) ──` already present
- No broken/destructive controls exposed in current UI

## Endpoint Path for 1.1 (Reference)
When implemented in 1.1, the following endpoints will be needed:
- `POST /api/git/commit?session_id=X` — body: `{message, files}`
- `POST /api/git/discard?session_id=X` — body: `{files}`
- `POST /api/git/checkout?session_id=X` — body: `{branch}`

## Implementation Plan for 1.1
1. curl-test server endpoints to verify wire format
2. Add Retrofit `@POST` methods to `HermexApi.kt`
3. Create DTOs: `GitCommitRequest`, `GitDiscardRequest`, `GitCheckoutRequest`
4. Add ViewModel methods: `gitCommit()`, `gitDiscard()`, `gitCheckout()`
5. Add commit dialog (message input + file selection)
6. Wire discard confirmation (currently UI exists for file edits, not git)
7. Add branch checkout flow (tap → confirm → checkout)
8. Use `HermexErrorBanner` for errors
9. Add tests

## Risk Assessment
- **Defer risk:** Low — read-only git is the minimum useful surface
- **Include risk:** Medium/High — destructive actions + backend compatibility
- **Recommendation:** Keep deferred; revisit in 1.1

## Acceptance Checklist (1.0 Defer Path)
- [x] 1.0 Git UI is clearly read-only
- [x] No visible dead commit/discard/checkout controls
- [x] README/release notes will list Git status/diff/branches as read-only
- [x] Build/tests pass
- [x] Decision documented for 1.1 follow-up
