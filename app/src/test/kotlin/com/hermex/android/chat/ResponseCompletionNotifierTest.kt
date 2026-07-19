package com.hermex.android.chat

import org.junit.Assert.assertFalse
import org.junit.Test

class ResponseCompletionNotifierTest {

    // ── Pure gating logic (no Android deps) ──

    @Test
    fun `completedNormally true and foreground returns false`() {
        assertFalse(ResponseCompletionGate.shouldNotify(completedNormally = true, isAppInForeground = true))
    }

    @Test
    fun `completedNormally true and background returns true`() {
        assert(ResponseCompletionGate.shouldNotify(completedNormally = true, isAppInForeground = false))
    }

    @Test
    fun `completedNormally false and background returns false`() {
        assertFalse(ResponseCompletionGate.shouldNotify(completedNormally = false, isAppInForeground = false))
    }

    @Test
    fun `completedNormally false and foreground returns false`() {
        assertFalse(ResponseCompletionGate.shouldNotify(completedNormally = false, isAppInForeground = true))
    }

    @Test
    fun `default no-op notifier does not crash`() {
        val noop: ResponseCompletionNotifier = ResponseCompletionNotifier { _, _ -> }
        noop.onResponseCompleted("s1", true)
        noop.onResponseCompleted("s1", false)
    }
}
