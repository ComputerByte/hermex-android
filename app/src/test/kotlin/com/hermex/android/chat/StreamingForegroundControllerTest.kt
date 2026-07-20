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
    fun `fake records onStreamStarted calls with AtomicBoolean semantics`() {
        val fake = FakeStreamingForegroundController()
        assertEquals(0, fake.startedCount)
        fake.onStreamStarted()
        assertEquals(1, fake.startedCount)
        fake.onStreamStarted()
        // Second start is a no-op -- production AtomicBoolean prevents double-start.
        assertEquals(1, fake.startedCount)
    }

    @Test
    fun `fake records onStreamStopped calls with AtomicBoolean semantics`() {
        val fake = FakeStreamingForegroundController()
        assertEquals(0, fake.stoppedCount)
        // Stop on a fresh controller is a no-op -- nothing running.
        fake.onStreamStopped()
        assertEquals(0, fake.stoppedCount)
        fake.onStreamStarted()
        fake.onStreamStopped()
        assertEquals(1, fake.stoppedCount)
    }
}
