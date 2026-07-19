package com.hermex.android.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
