# Notifications + Widget QA

## Notifications

### Channel Registration (Verified)
```bash
adb shell dumpsys notification --noredact | grep hermex
```
- ✅ `NotificationChannel{mId='hermex_status', mName=Hermes status}` registered
- ✅ Channel created on app start via `HermexNotificationChannels.kt`

### Permission (Android 13+)
- ✅ `POST_NOTIFICATIONS` declared in AndroidManifest
- ✅ Runtime permission requested in SettingsScreen
- ✅ Settings toggle respects user choice

### Test 1: Notification Channel Visibility
1. Open app
2. Check Android system Settings → Apps → Hermex → Notifications
3. **Expected:** "Hermes status" channel visible

### Test 2: Background Response Notification
1. Start a chat response
2. Background the app (press Home)
3. Wait for response to complete
4. **Expected:** Notification appears with "Session needs attention" or similar
5. Tap notification
6. **Expected:** App opens to correct session (deep link)

### Test 3: Task Done Notification
1. Trigger a task completion scenario (server-side)
2. **Expected:** "Task done" notification shown
3. Tap → deep link to task

### Test 4: Deep Link from Notification
1. Trigger a notification
2. Tap it
3. **Expected:** Routes to relevant session/task/run
4. **Expected:** No crash if target is missing

## Widget

### Provider Registration (Verified)
```bash
adb shell dumpsys appwidget | grep com.hermex
```
- ✅ `HermexWidgetProvider` registered
- ✅ Initial layout: `hermex_widget`

### Test 5: Widget Adds to Home Screen
1. Long-press home screen → Widgets
2. Find Hermex widget
3. Drag to home screen
4. **Expected:** Widget appears without crash
5. **Expected:** Shows launch button + new-chat button

### Test 6: Widget Tap Behavior
1. Add widget to home screen
2. Tap root area
3. **Expected:** App launches to sessions list
4. Tap "new chat" button
5. **Expected:** App launches with new-chat intent

### Test 7: Widget Stability
1. Add widget
2. Reboot device
3. **Expected:** Widget still visible, not crashed
4. **Expected:** Tap still works

## Acceptance Checklist
- [x] Notification channel created on app start
- [x] POST_NOTIFICATIONS permission handled (Android 13+)
- [x] Widget does not crash launcher
- [x] Notification provider registered
- [x] Widget provider registered
- [x] No dead notification settings
- [x] Build/tests pass

## Implementation Reference

### Files
- `HermexNotificationChannels.kt` — Channel definitions
- `HermexNotifier.kt` — showSessionAttention(), showTaskDone()
- `HermexResponseCompletionNotifier.kt` — Posts when response completes
- `HermexWidgetProvider.kt` — AppWidgetProvider
- `res/xml/hermex_widget_provider.xml` — Widget metadata
- `res/layout/hermex_widget.xml` — Widget layout
- `res/drawable/hermex_widget_preview.xml` — Widget preview

### Deep Links
- `hermex://sessions` — Open sessions list
- `hermex://new-chat` — Start new chat
- `hermex://session/{id}` — Open specific session

## Deferred to 1.1
- Background response completion notification reliability on all OEMs
- Widget configuration activity
- Widget resize handling
- Per-session notification filtering
