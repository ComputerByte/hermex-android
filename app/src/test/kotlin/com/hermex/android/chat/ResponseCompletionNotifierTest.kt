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

    // ── Notification preference gate (simulates HermexResponseCompletionNotifier) ──

    @Test
    fun `gate skips when preference off`() {
        val gate = PreferenceAwareGate(notificationsEnabled = false, isAppInForeground = false, completedNormally = true)
        assertFalse(gate)
    }

    @Test
    fun `gate posts when preference on background completed normally`() {
        val gate = PreferenceAwareGate(notificationsEnabled = true, isAppInForeground = false, completedNormally = true)
        assert(gate)
    }

    @Test
    fun `gate skips when preference on but foreground`() {
        val gate = PreferenceAwareGate(notificationsEnabled = true, isAppInForeground = true, completedNormally = true)
        assertFalse(gate)
    }

    @Test
    fun `gate skips when preference on background but not completed normally`() {
        val gate = PreferenceAwareGate(notificationsEnabled = true, isAppInForeground = false, completedNormally = false)
        assertFalse(gate)
    }
}

private fun PreferenceAwareGate(
    notificationsEnabled: Boolean,
    isAppInForeground: Boolean,
    completedNormally: Boolean,
): Boolean {
    if (!ResponseCompletionGate.shouldNotify(completedNormally, isAppInForeground)) return false
    if (!notificationsEnabled) return false
    return true
}
