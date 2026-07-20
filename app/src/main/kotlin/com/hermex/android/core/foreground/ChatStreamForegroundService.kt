package com.hermex.android.core.foreground

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hermex.android.R
import com.hermex.android.core.notifications.HermexNotificationChannels
import com.hermex.android.core.notifications.HermexNotifier

/**
 * Foreground service that keeps the app process alive during an active SSE stream,
 * preventing Android from freezing the connection when the user backgrounds.
 *
 * Started by [AndroidStreamingForegroundController.onStreamStarted] while the user is still
 * foregrounded — satisfying Android 14+ background-start restrictions. Stopped on every
 * terminal stream path via [AndroidStreamingForegroundController.onStreamStopped].
 *
 * Shows a non-sensitive ongoing notification on the existing Hermex status channel.
 * No conversation text, IDs, server names, credentials, or model names are exposed.
 * Uses [HermexNotifier.STREAM_SERVICE_NOTIFICATION_ID] (1100), which is strictly below the
 * completion notification IDs (1200, 1300+) so it cannot clobber them.
 */
internal class ChatStreamForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        HermexNotificationChannels.ensureCreated(this)
        val notification = NotificationCompat.Builder(this, HermexNotificationChannels.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_hermex)
            .setContentTitle("Response in progress")
            .setContentText("Open Hermex")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(HermexNotifier.STREAM_SERVICE_NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    /** Android 15+ (API 35) enforces a 24-hour timeout on [android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC]
     * foreground services. When the timeout fires without a corresponding stop, the system calls
     * this method. We stop cleanly rather than letting the system kill the process. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_STOP = "com.hermex.android.ACTION_STREAM_STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, ChatStreamForegroundService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, ChatStreamForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
