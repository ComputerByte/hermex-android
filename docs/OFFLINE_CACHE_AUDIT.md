# Offline Cache Audit

## Cache Architecture
- **Database:** Room (`HermexDatabase`)
- **Entities:** `CachedSessionEntity`, `CachedMessageEntity`
- **Migration:** MIGRATION_1_2 (adds `cached_messages` table)
- **Pruning:** 50 sessions/90 days per server (`CachePruning.kt`)
- **Startup pruning:** Runs on app start (`StartupCachePruning.kt`)
- **Server isolation:** All cache operations scoped by `serverId`

## Audit Checklist

### 1. Migration Behavior
- [x] MIGRATION_1_2 exists in `Migrations.kt`
- [x] Schema version 2 includes `cached_messages` table
- [x] Foreign key cascade: deleting session deletes messages
- **Verified:** Existing v1 databases upgrade cleanly

### 2. Server Isolation (No Cross-Server Leaks)
- [x] All DAO methods take `serverId` parameter
- [x] `clearServer(serverId)` scoped to one server
- [x] `pruneServerCache(serverId, ...)` scoped to one server
- [x] Tests in `OfflineCacheServerIsolationTest.kt` verify isolation

### 3. Logout Behavior
- [x] `AuthRepository.signOut()` clears cookies via `networkModule.cookieJar.clear()`
- [x] `signOut()` demotes to `AuthState.LoggedOut` (server stays configured)
- [x] Cache is **preserved** on logout (so re-login shows offline history)
- [x] `forgetServer()` (not `signOut()`) calls `offlineCacheRepository.clearServer()`
- **Design:** Logout ≠ Forget Server. Logout keeps cached data for offline access.

### 4. Stale Cache Banner
- [x] `ChatUiState.isShowingCachedData` flag
- [x] `ChatUiState.cacheStatusMessage` user-facing message
- [x] Banner shown when data came from cache
- [x] Banner clears on successful network fetch

### 5. Pruning Safety
- [x] `pruneServerCache` has `sessionIdToPreserve` parameter
- [x] Most-recently-active session is never pruned
- [x] Pruning is scoped per-server
- [x] Tests in `CachePruningTest.kt` cover edge cases

### 6. Server Switch Isolation
- [x] Active server ID tracked in `AuthRepository`
- [x] Cache lookups always use `serverId` key
- [x] Switching servers does not show other server's data
- [x] Tests verify isolation

## Test Coverage
- `OfflineCacheRepositoryPruningTest.kt` - Pruning logic
- `CachePruningTest.kt` - Pruning helpers
- `CachedSessionEntityTest.kt` - Entity conversions
- `CachedMessageEntityTest.kt` - Message entity conversions
- `StartupCachePruningTest.kt` - Startup pruning
- `OfflineCacheServerIsolationTest.kt` - NEW: Server isolation

## Device Verification Procedure

### Test 1: Offline Session List
1. Open sessions list with network on
2. Disable network
3. Restart app
4. **Expected:** Cached sessions still visible
5. **Expected:** Banner indicates "Offline" or "Cached"

### Test 2: Server Switch
1. Configure server A, browse sessions
2. Configure server B (same or different URL)
3. Switch to server B
4. **Expected:** Server A's sessions NOT visible
5. Switch back to server A
6. **Expected:** Server A's sessions still visible (cached)

### Test 3: Logout (vs Forget)
1. Configure server, browse
2. Sign out
3. **Expected:** Sign-in screen shown
4. **Expected:** Cache preserved (visible if you sign back in offline)
5. Re-login: cached data still accessible

### Test 4: Forget Server
1. Configure server, browse
2. Forget server (remove from server list)
3. **Expected:** Server entry removed
4. **Expected:** Cache for that server also cleared (no orphan data)

### Test 5: Migration
1. Install older version of app (with schema v1)
2. Add some sessions
3. Update to current version
4. **Expected:** Sessions preserved
5. **Expected:** Messages table also migrated

## Acceptance Checklist
- [x] Offline session list after restart
- [x] Offline chat history with clear banner
- [x] Server switch does not leak data
- [x] Logout does not leave accessible private data in UI
- [x] Build/tests pass
- [x] QA checklist includes passed offline/server-switch/logout cache scenarios before RC
