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

    /** Single synchronous source of truth for Hermex's own in-app "Notifications" preference
     * (separate from the OS permission [canPostNotifications] checks). Set once by AppContainer
     * at startup to read its already-coroutine-synced, [Volatile]-cached copy of
     * [com.hermex.android.core.storage.ChatPreferencesStore.loadNotificationsEnabled] -- this
     * object never reads DataStore itself, so there is exactly one place the preference lives.
     * Every public entry point below funnels through [showStatus], so none of them can forget
     * this check the way only [com.hermex.android.core.notifications.HermexResponseCompletionNotifier]
     * used to. */
    var isNotificationsEnabled: () -> Boolean = { false }

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
            notificationId = taskNotificationId(jobId),
            title = "Task done",
            text = "Open Hermex",
            intent = deepLinkIntent(context, HermexNotificationRoutes.task(jobId)),
        )
    }

    /** Derives a stable per-task notification ID from [jobId] so two tasks completing near each
     * other get distinct notifications instead of clobbering each other under one constant ID --
     * while re-posting for the *same* [jobId] always resolves to the same ID, so
     * [android.app.NotificationManager.notify] updates that one notification in place rather than
     * creating a duplicate. Collisions between different job ids are possible (this is a plain
     * hash, not a guaranteed-unique id) but rare enough for practical notification use, matching
     * how [android.util.SparseArray]-style hash-based ids are used elsewhere in the platform. */
    internal fun taskNotificationId(jobId: String): Int = TASK_DONE_NOTIFICATION_ID + nonNegativeHash(jobId.hashCode())

    /** abs(Int.MIN_VALUE) overflows back to Int.MIN_VALUE itself rather than throwing --
     * special-cased so a hash of exactly Int.MIN_VALUE can't silently produce a surprising
     * negative id. Extracted as a pure function of the hash (rather than of a String) so the
     * exact edge case is directly testable without needing a real string that hashes to it. */
    internal fun nonNegativeHash(hash: Int): Int = if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)

    private fun showStatus(context: Context, notificationId: Int, title: String, text: String, intent: Intent) {
        HermexNotificationChannels.ensureCreated(context)
        if (!NotificationGate.shouldPost(isNotificationsEnabled(), canPostNotifications(context))) return

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

        // canPostNotifications() above already guards this for the common case, but lint's
        // static analysis doesn't trace a Boolean-returning helper as equivalent to an inline
        // checkSelfPermission() guard -- and permission can theoretically be revoked in the
        // narrow window between that check and this call. Catching SecurityException is the
        // second half of lint's own suggested remedy and costs nothing: on a rejected/revoked
        // permission this notification silently doesn't show, exactly like the early return
        // above already does.
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission was revoked after canPostNotifications() checked it. Same outcome as
            // that early return: no notification, no crash.
        }
    }

    private fun deepLinkIntent(context: Context, uri: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setClass(context, MainActivity::class.java)
        data = android.net.Uri.parse(uri)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}
