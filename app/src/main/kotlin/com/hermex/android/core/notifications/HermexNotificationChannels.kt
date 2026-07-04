package com.hermex.android.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object HermexNotificationChannels {
    const val STATUS_CHANNEL_ID = "hermex_status"
    private const val STATUS_CHANNEL_NAME = "Hermes status"
    private const val STATUS_CHANNEL_DESCRIPTION = "Hermes session and task status"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            STATUS_CHANNEL_ID,
            STATUS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = STATUS_CHANNEL_DESCRIPTION
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
