package com.hermex.android.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hermex.android.MainActivity
import com.hermex.android.R

object HermexNotifier {
    const val SESSION_ATTENTION_NOTIFICATION_ID = 1200
    const val TASK_DONE_NOTIFICATION_ID = 1300

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showSessionAttention(context: Context, sessionId: String) {
        showStatus(
            context = context,
            notificationId = SESSION_ATTENTION_NOTIFICATION_ID,
            title = "Session needs attention",
            text = "Open Hermex",
            intent = deepLinkIntent(context, HermexNotificationRoutes.session(sessionId)),
        )
    }

    fun showTaskDone(context: Context, jobId: String) {
        showStatus(
            context = context,
            notificationId = TASK_DONE_NOTIFICATION_ID,
            title = "Task done",
            text = "Open Hermex",
            intent = deepLinkIntent(context, HermexNotificationRoutes.task(jobId)),
        )
    }

    private fun showStatus(context: Context, notificationId: Int, title: String, text: String, intent: Intent) {
        HermexNotificationChannels.ensureCreated(context)
        if (!canPostNotifications(context)) return

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, HermexNotificationChannels.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_hermex)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun deepLinkIntent(context: Context, uri: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setClass(context, MainActivity::class.java)
        data = android.net.Uri.parse(uri)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}
