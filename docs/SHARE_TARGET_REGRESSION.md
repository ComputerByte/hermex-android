# Share Target Regression Test

## Manifest Verification (Automated)
```bash
adb shell pm dump com.hermex.android | grep -A1 "android.intent.action.SEND"
```
- ✅ SEND registered with text/plain
- ✅ SEND registered with */*
- ✅ SEND_MULTIPLE registered with */*

## Test Sources

### 1. Text Share (Chrome, Browser, Notes)
**Source:** Any app with text selection
**Steps:**
1. Select text in source app
2. Tap Share → Hermex
3. **Expected:** Destination picker appears
4. **Expected:** Text lands in composer (not auto-sent)
5. **Expected:** User can edit before sending

### 2. Single File (Files, Drive)
**Source:** File manager
**Steps:**
1. Long-press file → Share
2. Tap Hermex
3. **Expected:** Destination picker
4. **Expected:** File attaches with correct name + MIME
5. **Expected:** Thumbnail shown for images

### 3. Multiple Files (Files app)
**Source:** File manager (multi-select)
**Steps:**
1. Multi-select files → Share
2. Tap Hermex
3. **Expected:** Destination picker
4. **Expected:** All files attach (capped at 10)
5. **Expected:** Excess files gracefully ignored with banner

### 4. Image (Photos, Gallery)
**Source:** Gallery app
**Steps:**
1. View image → Share
2. Tap Hermex
3. **Expected:** Image attaches with thumbnail
4. **Expected:** Correct MIME (image/jpeg, image/png)
5. **Expected:** Filename preserved

### 5. PDF (PDF viewer)
**Source:** Any PDF reader
**Steps:**
1. Open PDF → Share
2. Tap Hermex
3. **Expected:** PDF attaches as document
4. **Expected:** application/pdf MIME
5. **Expected:** Filename preserved

### 6. Destination Picker
**Behavior:**
- Existing session: tap → load that session with content staged
- New session: tap → create new session
- Cancel: dismisses share entirely

### 7. Failed Upload Recovery
**Steps:**
1. Share file successfully
2. Disable network before sending
3. Try to send
4. **Expected:** Composer still has text + attachment chips
5. **Expected:** Error message shown
6. Re-enable network → retry
7. **Expected:** Send succeeds

## Implementation Reference

### Files
- `HermexIntentRouter.kt` — Parses ACTION_SEND and ACTION_SEND_MULTIPLE
- `ShareDestinationPicker.kt` — UI for choosing target session
- `MAX_SHARED_URIS = 10` — Cap on multi-share

### Wire Format
- URIs encoded as pipe-separated URL-encoded strings
- Passed through HermexNavGraph composable
- Decoded back to Uri list in ChatViewModel

## Acceptance Checklist
- [x] Shared text lands in composer (not auto-sent)
- [x] Shared file attaches correctly
- [x] Multiple files attach or fail gracefully
- [x] User can choose existing or new session
- [x] Failed upload does not lose shared content
- [x] Build/tests pass
- [x] HermexIntentRouterTest passes
- [x] Manifest intent filters registered (3 filters)
- [x] Manifest verified via `pm dump`
