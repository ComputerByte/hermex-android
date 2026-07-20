package com.hermex.android.chat

class FakeStreamingForegroundController : StreamingForegroundController {
    var startedCount = 0
    var stoppedCount = 0

    override fun onStreamStarted() { startedCount++ }
    override fun onStreamStopped() { stoppedCount++ }
}
