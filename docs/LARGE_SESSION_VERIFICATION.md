# Large Session Stability Verification

## Prerequisites
- Android device with Hermex installed
- Active Hermes server
- A session with 100+ messages (or ability to generate one)

## Verification Steps

### 1. Long Session Opens Reliably (200+ messages)
1. Navigate to a session with 200+ messages
2. **Expected:** Session opens without crash
3. **Expected:** All cached messages load (API limit is 50/server fetch, cache may have more)
4. **Expected:** Scroll is responsive

### 2. Scroll-to-Bottom Works
1. Open a long session
2. Scroll to the top
3. Tap scroll-to-bottom FAB or swipe down at bottom
4. **Expected:** Scrolls to the latest message
5. **Expected:** No jitter or visual glitches

### 3. Tool Cards Render in Historical Messages
1. Open a session that includes tool calls (e.g. /search or file operations)
2. Scroll through historical messages
3. **Expected:** Tool call cards render with correct status
4. **Expected:** No layout overflow or truncated cards

### 4. Reasoning Blocks Render in Historical Messages
1. Open a session with extended thinking/reasoning blocks
2. Scroll through
3. **Expected:** Reasoning blocks collapsible/expandable
4. **Expected:** Content preserved

### 5. Cache Fallback Banner
1. Enable airplane mode
2. Open a long session
3. **Expected:** Banner appears: "Showing cached messages"
4. Disable airplane mode
5. **Expected:** Banner clears on successful network fetch

## Performance Notes (1.0)
- API: `msg_limit=50` caps server response
- Cache: Room CachedMessageEntity stores all cached messages
- Query: Ordered by orderIndex
- For 1.0: 200+ message sessions supported via cache
- Full pagination/infinite scroll: deferred to 1.1

## Known Limitations (1.0)
- No infinite scroll for messages beyond API msg_limit
- Cache may show stale data when offline
- 500+ message sessions may have minor scroll lag (acceptable for 1.0)

## Acceptance Checklist
- [ ] Long sessions (200+ msgs) open reliably
- [ ] Scroll-to-bottom works
- [ ] Tool cards render correctly in history
- [ ] Reasoning blocks render correctly in history
- [ ] Cache fallback banner shown when offline
- [ ] No crashes with large sessions
- [ ] Full pagination deferred to 1.1 (not blocker for 1.0)
