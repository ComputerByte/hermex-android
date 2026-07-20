package com.hermex.android.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingForegroundControllerTest {

    // ── Interface contract: NoOp default ──

    @Test
    fun `NoOp does not crash on onStreamStarted`() {
        StreamingForegroundController.NoOp.onStreamStarted()
    }

    @Test
    fun `NoOp does not crash on onStreamStopped`() {
        StreamingForegroundController.NoOp.onStreamStopped()
    }

    // ── Fake records calls for ViewModel integration tests ──

    @Test
    fun `fake records onStreamStarted calls`() {
        val fake = FakeStreamingForegroundController()
        assertEquals(0, fake.startedCount)
        fake.onStreamStarted()
        assertEquals(1, fake.startedCount)
        fake.onStreamStarted()
        assertEquals(2, fake.startedCount)
    }

    @Test
    fun `fake records onStreamStopped calls`() {
        val fake = FakeStreamingForegroundController()
        assertEquals(0, fake.stoppedCount)
        fake.onStreamStopped()
        assertEquals(1, fake.stoppedCount)
    }
}
