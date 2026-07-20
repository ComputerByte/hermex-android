package com.hermex.android.core.foreground

import android.content.Context
import androidx.core.content.ContextCompat
import com.hermex.android.chat.StreamingForegroundController
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of [StreamingForegroundController] that starts and stops
 * [ChatStreamForegroundService].
 *
 * [onStreamStarted] uses [ContextCompat.startForegroundService] so the service has the
 * required time window to call [android.app.Service.startForeground]. Must be called while
 * the app is still foregrounded (Android 14+ disallows background service starts).
 *
 * [onStreamStopped] is idempotent: concurrent or repeated terminal signals cannot double-stop
 * or leak the service. The [AtomicBoolean] guard also prevents [onStreamStarted] from sending
 * a second start intent if the service is already running.
 */
class AndroidStreamingForegroundController(
    private val context: Context,
) : StreamingForegroundController {

    private val isRunning = AtomicBoolean(false)

    override fun onStreamStarted() {
        if (isRunning.compareAndSet(false, true)) {
            ContextCompat.startForegroundService(context, ChatStreamForegroundService.startIntent(context))
        }
    }

    override fun onStreamStopped() {
        if (isRunning.compareAndSet(true, false)) {
            context.startService(ChatStreamForegroundService.stopIntent(context))
        }
    }
}
