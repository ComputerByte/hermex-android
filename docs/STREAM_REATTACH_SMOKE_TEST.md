# Stream Reattach Device Smoke Test Procedure

## Prerequisites
- Android device with Hermex installed
- Active Hermes server (HTTPS or local HTTP)
- Active chat session in progress

## Test 1: Background/Foreground Reattach
1. Open a session and send a message that produces a long response
2. While streaming, press the Home button to background the app
3. Wait 5-10 seconds
4. Return to the app from recents
5. **Expected:** Stream resumes (no duplicate messages, no stuck state)
6. **Expected:** Final answer is received

## Test 2: Stop Button After Reattach
1. Start a long streaming response
2. Background app, return
3. Confirm reattach occurred
4. Tap Stop button during the reattached stream
5. **Expected:** Stream stops cleanly, partial response preserved
6. **Expected:** No duplicate messages, no infinite streaming indicator

## Test 3: Tool Calls After Reattach
1. Send a message that triggers a tool call
2. Background app mid-tool-execution
3. Return to app
4. **Expected:** Tool call card is shown with correct status (running/done)
5. **Expected:** Tool result is captured in final response

## Test 4: Final Scroll Position
1. Start a long streaming response
2. Scroll up to read earlier messages
3. **Expected:** New tokens append below without forcing scroll
4. When reattach happens, return to bottom
5. **Expected:** Scroll position respected, no jump

## Test 5: No Duplicate Messages
1. Complete a streamed response
2. Background app, return
3. **Expected:** No duplicate assistant messages
4. **Expected:** No stuck "Streaming..." state

## Test 6: Network Dropout Reattach
1. Start a long streaming response
2. Toggle airplane mode ON for 5 seconds
3. Toggle airplane mode OFF
4. **Expected:** Reattach attempted
5. **Expected:** Reconnect state visible, no crash

## Test 7: Server Restart Reattach (Optional)
1. Start a long streaming response
2. Restart the Hermes server
3. **Expected:** Reattach fails gracefully
4. **Expected:** User sees error or "stream ended" message

## Acceptance Checklist
- [ ] No duplicated assistant messages
- [ ] No stuck "streaming" state
- [ ] No lost final answer
- [ ] Stop/cancel works during reattached stream
- [ ] Tool calls render correctly after reattach
- [ ] Scroll position respected
- [ ] No crashes during reattach flow

## Stream URL Format (Verified)
- `GET /api/chat/stream?stream_id=X` (query param format)
- Base URL: server base URL
- Path: `/api/chat/stream` (replaces any base path)
