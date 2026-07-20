package com.hermex.android.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermexNotifierTest {

    @Test
    fun `the same job id always derives the same notification id, so a repeat post updates it`() {
        val first = HermexNotifier.taskNotificationId("job-42")
        val second = HermexNotifier.taskNotificationId("job-42")
        assertEquals(first, second)
    }

    @Test
    fun `different job ids derive different notification ids`() {
        val a = HermexNotifier.taskNotificationId("job-a")
        val b = HermexNotifier.taskNotificationId("job-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `an empty job id does not crash and is stable`() {
        assertEquals(HermexNotifier.taskNotificationId(""), HermexNotifier.taskNotificationId(""))
    }

    @Test
    fun `a hash of Int MIN_VALUE does not crash and resolves to a deterministic non-negative value`() {
        // kotlin.math.abs(Int.MIN_VALUE) overflows back to Int.MIN_VALUE itself -- this is the
        // exact edge case taskNotificationId() must not blow up on.
        assertEquals(0, HermexNotifier.nonNegativeHash(Int.MIN_VALUE))
    }

    @Test
    fun `an ordinary negative hash is made non-negative`() {
        assertEquals(42, HermexNotifier.nonNegativeHash(-42))
    }

    // ── Notification ID safety: stream service must not clobber completion/session notifications ──

    @Test
    fun `stream service notification id is distinct from session attention id`() {
        assertNotEquals(HermexNotifier.STREAM_SERVICE_NOTIFICATION_ID, HermexNotifier.SESSION_ATTENTION_NOTIFICATION_ID)
    }

    @Test
    fun `stream service notification id is distinct from base task done id`() {
        assertNotEquals(HermexNotifier.STREAM_SERVICE_NOTIFICATION_ID, HermexNotifier.TASK_DONE_NOTIFICATION_ID)
    }

    @Test
    fun `stream service notification id is less than base task done id so hash expansion cannot reach it`() {
        // taskNotificationId() returns TASK_DONE_NOTIFICATION_ID + nonNegativeHash(...) >= TASK_DONE_NOTIFICATION_ID.
        // STREAM_SERVICE_NOTIFICATION_ID is strictly below TASK_DONE_NOTIFICATION_ID, so no job id
        // can produce a taskNotificationId equal to it.
        assertTrue(HermexNotifier.STREAM_SERVICE_NOTIFICATION_ID < HermexNotifier.TASK_DONE_NOTIFICATION_ID)
    }
}
