package com.hermex.android.chat

/**
 * Injected callback for local response-completion notifications.
 * Keeps [ChatViewModel] Android-free: the concrete implementation lives in
 * [com.hermex.android.core.notifications.HermexResponseCompletionNotifier] and
 * the DI layer (AppContainer) wires it in.
 *
 * [completedNormally] is true only for successful stream completion
 * (SseEvent.Done / StreamEnd). False for user-initiated Stop, server errors,
 * transport errors, and forced cancellation.
 */
fun interface ResponseCompletionNotifier {
    fun onResponseCompleted(sessionId: String, completedNormally: Boolean)
}

/**
 * Pure-Kotlin gating helper — testable without Android framework.
 */
object ResponseCompletionGate {
    fun shouldNotify(completedNormally: Boolean, isAppInForeground: Boolean): Boolean =
        completedNormally && !isAppInForeground
}
