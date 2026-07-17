package com.hermex.android.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.hermex.android.chat.RetainedChatViewModelStoreOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelRetentionTest {
    @Test
    fun `same server and session resolve to the same retained key`() {
        assertEquals(
            retainedChatViewModelKey("server-a", "session-1"),
            retainedChatViewModelKey("server-a", "session-1"),
        )
    }

    @Test
    fun `same session id on different servers cannot share a view model`() {
        assertNotEquals(
            retainedChatViewModelKey("server-a", "shared-session"),
            retainedChatViewModelKey("server-b", "shared-session"),
        )
    }

    @Test
    fun `length prefixes avoid delimiter collisions`() {
        assertNotEquals(
            retainedChatViewModelKey("server:a", "b"),
            retainedChatViewModelKey("server", "a:b"),
        )
    }

    @Test
    fun `clearing a replaced destination does not clear the app-retained chat`() {
        val retainedChatOwner = RetainedChatViewModelStoreOwner()
        val replacedDestinationOwner = TestOwner()
        val factory = TrackingViewModelFactory()
        val key = retainedChatViewModelKey("server-a", "session-1")
        val retained = ViewModelProvider(retainedChatOwner, factory)[key, TrackingViewModel::class.java]

        // Navigation replaces/pops this store. The chat is deliberately not owned by it.
        ViewModelProvider(replacedDestinationOwner, factory)["screen", TrackingViewModel::class.java]
        replacedDestinationOwner.viewModelStore.clear()

        val afterSwitch = ViewModelProvider(retainedChatOwner, factory)[key, TrackingViewModel::class.java]
        assertSame(retained, afterSwitch)
        assertFalse(retained.wasCleared)

        // Authentication-scope reset releases the stream and ensures a later login gets a new
        // instance even when the server/session key happens to be identical.
        retainedChatOwner.reset()
        assertTrue(retained.wasCleared)
        val afterAuthenticationReset =
            ViewModelProvider(retainedChatOwner, factory)[key, TrackingViewModel::class.java]
        assertNotSame(retained, afterAuthenticationReset)
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class TrackingViewModel : ViewModel() {
        var wasCleared: Boolean = false
            private set

        override fun onCleared() {
            wasCleared = true
        }
    }

    private class TrackingViewModelFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TrackingViewModel() as T
    }
}
