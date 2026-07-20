package com.hermex.android.chat

/**
 * Injected lifecycle hook for the chat stream foreground service.
 * Keeps [ChatViewModel] Android-free: the concrete implementation lives in
 * [com.hermex.android.core.foreground.AndroidStreamingForegroundController].
 *
 * Both methods are idempotent at the controller level -- [ChatViewModel] may reach
 * [onStreamStopped] from multiple terminal paths and must not track which already fired.
 */
interface StreamingForegroundController {
    fun onStreamStarted()
    fun onStreamStopped()

    companion object {
        val NoOp: StreamingForegroundController = object : StreamingForegroundController {
            override fun onStreamStarted() = Unit
            override fun onStreamStopped() = Unit
        }
    }
}
