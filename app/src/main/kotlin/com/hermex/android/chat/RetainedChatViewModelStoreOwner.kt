package com.hermex.android.chat

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Process-retained owner for per-session [ChatViewModel] instances. Navigation destinations use
 * this store instead of their own back-stack-entry stores, so replacing a chat screen cannot
 * cancel that chat's send/start/SSE coroutine. The owner is reset on authentication-scope changes
 * by [com.hermex.android.AppContainer], which prevents old server/session state from leaking into
 * a later login and bounds retention to the current authenticated app process.
 */
internal class RetainedChatViewModelStoreOwner : ViewModelStoreOwner {
    private var store = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = store

    /** Clears every retained chat and swaps in a fresh store for the next authenticated scope. */
    @MainThread
    fun reset() {
        val previous = store
        store = ViewModelStore()
        previous.clear()
    }
}
