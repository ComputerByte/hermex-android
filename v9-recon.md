# v0.9.1 Recon Report — Offline Cache / Data Management Hardening

**Date:** 2026-07-04
**Branch:** master (`703555b`)
**Status:** Clean (only release APKs untracked)
**Previous release:** v0.9.0-preview

---

## 1. Repo Status

| Metric | Value |
|---|---|
| Branch | `master` |
| HEAD | `703555b` (Bump version to 0.9.0-preview) |
| Ahead/behind origin | Up to date |
| Clean/dirty | Clean |
| Untracked | 7 release APKs + `v9-recon.md` |

---

## 2. Current Offline/Cache Architecture

### Storage technologies
| Technology | Purpose |
|---|---|
| **Room** (SQLite, `hermex_cache.db`) | Structured offline cache: session lists + chat message transcripts |
| **DataStore Preferences** (`hermex_server`) | Server configurations (list + active server) |
| **DataStore Preferences** (`hermex_notification_prefs`) | Notification toggle preference |
| **DataStore Preferences** (`hermex_appearance`) | Appearance/theme preferences |
| **DataStore Preferences** (`hermex_chat_prefs`) | Chat-specific preferences |
| **DataStore Preferences** (`hermex_custom_headers`) | Custom HTTP headers per server |
| **EncryptedSharedPreferences** (`hermex_cookies_encrypted`) | Encrypted cookie store per server |
| **In-memory only** | Profiles, projects, tasks, skills, memory, insights (re-fetched on every screen load) |
| **In-memory only** | Tool call cards, streaming state, composer state |

### Key classes
- `HermexDatabase` — Room DB (version 2, 2 tables: `cached_sessions`, `cached_messages`)
- `OfflineCacheRepository` — interface defining cache CRUD
- `RoomOfflineCacheRepository` — Room-backed implementation
- `NoOpOfflineCacheRepository` — in-memory no-op (used in tests)
- `FakeOfflineCacheRepository` — test double with in-memory maps
- `CachedSessionDao` — Room DAO with `getSessions`, `getSession`, `getMessages`, `replaceSessions`, `replaceSessionDetail`, `deleteSessionsForServer`, `deleteMessagesForServer`, `deleteMessagesForSession`
- `MIGRATION_1_2` — adds `cached_messages` table

### Cache write paths
1. **Session list fetched** → `SessionListViewModel.load()` → `offlineCacheRepository.saveSessions(serverId, sessions)` → `dao.replaceSessions()` (DELETE all + INSERT all)
2. **Session detail/messages fetched** → `ChatViewModel.loadSession()` → `offlineCacheRepository.cacheSessionDetail(serverId, sessionId, detail)` → `dao.replaceSessionDetail()` (INSERT session + DELETE messages + INSERT messages, in one transaction)
3. **Server removed** → `AuthRepository.forgetServer()` → `offlineCacheRepository.clearServer(serverId)` via `onServerForgotten` callback

### Cache read paths
1. **Session list, network fails** → `SessionListViewModel.load()` catches `ApiError` → reads `offlineCacheRepository.cachedSessions(serverId)` → shows banner "Unable to reach server -- showing cached sessions"
2. **Chat session, network fails** → `ChatViewModel.loadSession()` catches `ApiError` → reads `offlineCacheRepository.cachedSessionDetail(serverId, sessionId)` → shows banner "Unable to reach server -- showing cached conversation"

### What happens when network is unavailable
- Session list: shows cached session list with offline banner
- Chat: shows cached messages with offline banner
- All other features (profiles, projects, tasks, skills, memory, insights): **nothing cached** — user sees loading spinner or error state
- Workspace: no offline support, shows "Not signed in" or error

---

## 3. Cached Data Inventory

| Data | Where stored | Bounded? | Scope | Clear on logout? | Clear on server switch? | Clear on server remove? |
|---|---|---|---|---|---|---|
| Session list | Room `cached_sessions` | **Unbounded** | Per `serverId` | No | No | Yes |
| Chat messages | Room `cached_messages` | **Unbounded** | Per `(serverId, sessionId)` | No | No | Yes |
| Server configs | DataStore `hermex_server` | Bounded (N servers) | Global | No | No | N/A |
| Cookies (encrypted) | EncryptedSharedPrefs | Bounded (1 per server) | Per `serverId` | Yes (active) | Yes | Yes |
| Custom headers | DataStore `hermex_custom_headers` | Bounded (1 per server) | Per `serverId` | No | No | N/A (uses `forgetServer` cleanup) |
| Preferences | DataStore (4 files) | Bounded (small) | Global | No | No | No |
| Profiles/Projects/Tasks/Skills/Memory/Insights | In-memory only | N/A | Per fetch | Yes (state reset on logout) | Yes | Yes |

### Notable
- `CachedSessionEntity` has a `cachedAtEpochMillis` field specifically designed to support future pruning — no schema migration needed
- `CachedMessageEntity` also has `cachedAtEpochMillis`
- Custom headers store (DataStore) is unbounded per server but practically small (a few headers)

---

## 4. Current Retention/Pruning Behavior

| Activity | Pruning? |
|---|---|
| Network fetch success (sessions) | **Full replace** — old session list is deleted, new one is inserted. This implicitly removes sessions that no longer exist server-side. |
| Network fetch success (messages) | **Full replace** — old messages for that session are deleted, new ones inserted. |
| App startup | **No cleanup** — cache from last session is loaded as-is. |
| Login | **No cleanup** — existing cache persists. |
| Logout | **No cleanup** — cache remains (only cookies cleared for active server). |
| Server switch | **No cleanup** — cache for both servers persists. |
| Server removal | **Cleanup** — `clearServer()` deletes all cached sessions + messages + cookies + headers for that server. |
| Time-based | **No pruning** — no max-age or max-row cap exists. |
| Startup/server-switch | **No cleanup hook** — `AuthRepository` has no startup or switch cleanup. |

### The one existing bounded behavior
- `SessionListViewModel`'s `saveSessions()` is called with the **full** server-side session list. This acts as implicit retention: if a session no longer exists on the server, the next successful list fetch removes it from the cache. But if the server is unreachable, stale sessions remain indefinitely.

---

## 5. Risk Assessment

### Storage growth risks
| Risk | Severity | Notes |
|---|---|---|
| Unlimited session/message accumulation | **Low-medium** | Each session is ~1 KB + messages per session (~0.5 KB each). Even 10,000 messages across 100 sessions ≈ 10 MB. Room SQLite handles this well. Risk is very gradual. |
| Orphaned messages without parent session | **Low** | Messages are always written in a transaction with their parent session row. Only `clearSession()` (called from `ChatViewModel` on server-404) would leave a session row without messages (acceptable: session still appears in list with 0 messages). |
| Custom headers DataStore growth | **Very low** | Fixed small number of entries. |
| Server config DataStore growth | **Very low** | User-defined, typically 1-3 servers. |

### Stale data risks
| Risk | Severity | Notes |
|---|---|---|
| Stale session list on offline start | **Low** | Banner clearly indicates offline/cached state. User can refresh when online. |
| Stale message transcript on offline open | **Low** | Banner clearly indicates offline/cached state. |
| Session deleted server-side still cached | **Medium** | If session is deleted server-side while app is offline, user can tap it, get cached messages (offline banner), then on next online fetch get a 404 → `clearSession()` runs, viewer shows error. This path is handled. |

### Privacy/security risks
| Risk | Severity | Notes |
|---|---|---|
| Cached conversations survive logout | **Medium** | Logout clears cookies but leaves cached sessions/messages on disk. If another user logs in to the **same** server on the same device, they'd see the previous user's cached conversations (before a fresh online fetch replaces them). Currently this is a design choice, not a bug — the app doesn't target shared-device use. |
| Server removal leaks partial data | **Low** | `clearServer()` deletes all cache for that server. No leak path identified. |

### Logout/server-switch risks
| Risk | Severity | Notes |
|---|---|---|
| Logout doesn't clear cache | **Medium risk, low impact** | Intentional design. Cache persists across logout/login cycles. If the user is the same person (typical), this is a feature. If using shared device, cache would persist. |
| Server switch doesn't clear cache | **Low** | Both servers' caches co-exist independently. No mixing because all queries are scoped by `serverId`. |

---

## 6. Recommended v0.9.1 Implementation Slices

### Slice A: Cache pruning infrastructure + tests (safe, no user-facing change)
- Add `pruneServerCache(serverId, maxSessions, maxAgeDays)` to `OfflineCacheRepository`
- Room implementation: `DELETE FROM cached_sessions WHERE serverId = :serverId AND cachedAtEpochMillis < :cutoff` for age pruning, and `DELETE FROM cached_sessions WHERE serverId = :serverId AND sessionId NOT IN (top N session IDs)` for count pruning
- Then `DELETE FROM cached_messages WHERE serverId = :serverId AND sessionId NOT IN (remaining session IDs)` to clean orphaned messages
- **Preserve active session** — always exclude the currently-open session from pruning
- Only test coverage — no call sites wired yet

### Slice B: Startup pruning hook
- Call `pruneServerCache()` from `AppContainer.init` or `AuthRepository` on auth restore
- Conservative defaults: keep 50 sessions per server, 90-day max age
- Only runs once per app start, not on every list fetch

### Slice C: Server-switch / login cleanup review
- Ensure `AuthRepository.forgetServer` already calls `clearServer` (it does — verified)
- Ensure `AuthRepository.logout()` does not need to clear cache (document decision)
- Add comment clarifying intentional cache retention across logout

### Slice D: Custom headers encryption (optional, reusable pattern from v0.7.8)
- Migrate `DataStoreCustomHeadersStore` to `EncryptedSharedPreferences`
- Pattern is identical to cookie migration in v0.7.8
- **Deferred unless specifically requested** — headers are user-configured metadata, not live credentials

---

## 7. Proposed Pruning Policy

```
Active/current session:  Never deleted (passed explicitly as sessionToPreserve)
Sessions per server:     Keep 50 most recent (by lastMessageAt)
Message retention:       Keep all messages for retained sessions
Max cache age:           90 days (by cachedAtEpochMillis)
Orphaned messages:       Deleted whenever sessions above them are pruned
Execution timing:        On app startup only (not on every network fetch)
```

Rationale:
- 50 sessions per server is generous — most users will have far fewer
- 90-day age is conservative — old enough to cover most offline scenarios
- Running at startup only avoids repeated DB work during rapid session list refreshes
- `cachedAtEpochMillis` is already on both entity schemas — no migration needed

---

## 8. Test Plan

### Existing test infrastructure
- `FakeOfflineCacheRepository` — in-memory test double, ready for extension
- `SessionListViewModelTest` — covers offline session loading with `FakeOfflineCacheRepository`
- `ChatViewModelTest` — covers offline message loading with `FakeOfflineCacheRepository`

### New tests to add
| Test | What it covers |
|---|---|
| `pruneCache preserves active session` | Pruning with explicit `sessionToPreserve` does not delete the preserved session |
| `pruneCache keeps latest N sessions` | With 60 sessions and max=50, only 50 remain |
| `pruneCache deletes orphaned messages` | After session pruning, messages for deleted sessions are gone |
| `pruneCache respects maxAge` | Sessions older than `maxAgeDays` are removed |
| `pruneCache no-ops when under limit` | With 10 sessions and max=50, all 10 remain |
| `pruneCache handles empty server` | No crash when server has no cached sessions |
| `offline session list still works after prune` | `cachedSessions` returns correct subset |
| `server switch does not mix pruned caches` | Pruning one server does not affect another |
| `logout leaves cache intact` | Verifies documented behavior — no regression |

### Test infrastructure changes needed
- Add `pruneServerCache` to `OfflineCacheRepository` interface (and `NoOpOfflineCacheRepository`)
- Add `pruneServerCache` to `FakeOfflineCacheRepository`
- No Robolectric or instrumented test needed — Room query logic is tested through the fake

---

## 9. Manual Verification Plan

1. **Online baseline** — Open app, verify session list loads, open a session, verify messages load
2. **Offline session list** — Go to airplane mode, pull to refresh, verify cached session list appears with offline banner
3. **Offline chat open** — Tap a cached session offline, verify cached messages appear with offline banner
4. **After pruning** — Add test data with `cachedAtEpochMillis` set to old timestamps, restart app, verify only recent sessions remain
5. **Active session preserved after prune** — Have a session open, simulate pruning, verify that session's messages are still cached
6. **Cross-server isolation** — Configure 2 servers, verify each sees only its own cached data
7. **Logout/login cycle** — Log out, verify cookies cleared but cache intact. Log back in, verify cached sessions still visible (until next online fetch replaces them)
8. **Server removal** — Remove a server, verify its cache is deleted and does not affect remaining servers
9. **No crash on empty cache** — Fresh install, open app, verify graceful empty states

---

## 10. Recommendation

**Proceed with v0.9.1 as planned.** The offline cache architecture is well-designed and the risk of unbounded growth is currently low (typical usage would take years to become problematic). However, there is no pruning at all, and the `cachedAtEpochMillis` field was specifically added to enable it without a migration.

### Safest first slice
**Slice A: Cache pruning infrastructure + tests** — pure data layer, no user-facing UI changes, fully testable through existing `FakeOfflineCacheRepository`.

### What to defer
- **Custom headers encryption** — low-risk, low-value for current use cases. Revisit if security audit demands it.
- **Logout clears cache** — explicitly not recommended. The cache is a convenience feature, not a security boundary.
- **Offline support for profiles/projects/tasks/skills/memory/insights** — would require significant new caching infrastructure. Not justified for MVP data volumes.
- **User-facing cache management UI** — premature; add only if users report issues.
