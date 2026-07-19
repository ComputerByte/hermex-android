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
 *
 * Whether Hermex's own "Notifications" preference is enabled is *not* checked here --
 * [HermexNotifier] enforces that itself for every entry point it exposes, so this class only
 * needs to decide the foreground/completed-normally question that's specific to chat responses.
 */
class HermexResponseCompletionNotifier(
    private val context: Context,
    private val isAppInForeground: () -> Boolean,
) : ResponseCompletionNotifier {

    override fun onResponseCompleted(sessionId: String, completedNormally: Boolean) {
        if (!ResponseCompletionGate.shouldNotify(completedNormally, isAppInForeground())) return
        HermexNotifier.showSessionAttention(context, sessionId)
    }
}
