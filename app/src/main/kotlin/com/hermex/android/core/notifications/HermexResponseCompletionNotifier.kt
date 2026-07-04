package com.hermex.android.core.notifications

import android.content.Context
import com.hermex.android.chat.ResponseCompletionGate
import com.hermex.android.chat.ResponseCompletionNotifier

/**
 * Concrete Android [ResponseCompletionNotifier] that posts a local notification
 * when a chat response completes normally while the app is backgrounded.
 *
 * [isAppInForeground] is resolved at notification time (not injected at construction)
 * so it reflects the most recent foreground/background state even if the notifier
 * instance lives longer than a single activity lifecycle.
 */
class HermexResponseCompletionNotifier(
    private val context: Context,
    private val isAppInForeground: () -> Boolean,
    private val isNotificationsEnabled: () -> Boolean = { true },
) : ResponseCompletionNotifier {

    override fun onResponseCompleted(sessionId: String, completedNormally: Boolean) {
        if (!ResponseCompletionGate.shouldNotify(completedNormally, isAppInForeground())) return
        if (!isNotificationsEnabled()) return
        HermexNotifier.showSessionAttention(context, sessionId)
    }
}
