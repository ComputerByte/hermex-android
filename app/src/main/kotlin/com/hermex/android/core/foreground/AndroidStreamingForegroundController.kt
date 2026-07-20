package com.hermex.android.core.foreground

import android.content.Context
import androidx.core.content.ContextCompat
import com.hermex.android.chat.StreamingForegroundController
import com.hermex.android.core.util.HermexLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of [StreamingForegroundController] that starts and stops
 * [ChatStreamForegroundService].
 *
 * [onStreamStarted] uses [ContextCompat.startForegroundService] so the service has the
 * required time window to call [android.app.Service.startForeground]. Must be called while
 * the app is still foregrounded (Android 14+ disallows background service starts). A failed
 * start (e.g. called after backgrounding) resets the internal guard so a retry can succeed.
 *
 * [onStreamStopped] is idempotent: concurrent or repeated terminal signals cannot double-stop
 * or leak the service. The [AtomicBoolean] guard also prevents [onStreamStarted] from sending
 * a second start intent if the service is already running.
 *
 * Also registers itself for [ChatStreamForegroundService.onSystemTimeout] while running, so a
 * system-initiated stop (the Android 15+ dataSync 24h foreground service timeout) resets
 * [isRunning] the same as an explicit [onStreamStopped] would -- otherwise it would stay stuck
 * true and silently block every future [onStreamStarted] call.
 */
class AndroidStreamingForegroundController(
    private val context: Context,
) : StreamingForegroundController {

    private val isRunning = AtomicBoolean(false)

    override fun onStreamStarted() {
        if (isRunning.compareAndSet(false, true)) {
            try {
                ContextCompat.startForegroundService(context, ChatStreamForegroundService.startIntent(context))
                // Hear about a system-initiated stop (Android 15+ dataSync 24h timeout) so
                // isRunning doesn't stay stuck true forever after the service is gone.
                ChatStreamForegroundService.onSystemTimeout = { isRunning.set(false) }
            } catch (e: IllegalStateException) {
                HermexLog.w("StreamService", "startForegroundService failed (likely backgrounded): ${e.message}")
                isRunning.set(false)
            }
        }
    }

    override fun onStreamStopped() {
        if (isRunning.compareAndSet(true, false)) {
            ChatStreamForegroundService.onSystemTimeout = null
            context.startService(ChatStreamForegroundService.stopIntent(context))
        }
    }
}
