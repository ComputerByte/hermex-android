package com.hermex.android.chat

/** Test double that mirrors [AndroidStreamingForegroundController]'s AtomicBoolean semantics:
 *  the first [onStreamStopped] on a fresh controller is a no-op (no running service to stop),
 *  so [stoppedCount] only increments for an actual stop-after-start cycle. */
class FakeStreamingForegroundController : StreamingForegroundController {
    var startedCount = 0
    var stoppedCount = 0
    private var isRunning = false

    override fun onStreamStarted() {
        if (!isRunning) {
            isRunning = true
            startedCount++
        }
    }

    override fun onStreamStopped() {
        if (isRunning) {
            isRunning = false
            stoppedCount++
        }
    }
}
