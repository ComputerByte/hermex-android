package com.hermex.android.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGateTest {

    @Test
    fun `enabled preference and granted permission permits a notification attempt`() {
        assertTrue(NotificationGate.shouldPost(notificationsEnabledInApp = true, hasSystemPermission = true))
    }

    @Test
    fun `disabled preference suppresses posting even with permission granted`() {
        assertFalse(NotificationGate.shouldPost(notificationsEnabledInApp = false, hasSystemPermission = true))
    }

    @Test
    fun `missing Android permission suppresses posting even when the preference is enabled`() {
        assertFalse(NotificationGate.shouldPost(notificationsEnabledInApp = true, hasSystemPermission = false))
    }

    @Test
    fun `both disabled suppresses posting`() {
        assertFalse(NotificationGate.shouldPost(notificationsEnabledInApp = false, hasSystemPermission = false))
    }
}
