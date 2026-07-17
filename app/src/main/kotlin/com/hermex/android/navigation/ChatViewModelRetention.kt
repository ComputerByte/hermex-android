package com.hermex.android.navigation

/**
 * A retained chat ViewModel must never be shared between two configured servers, even if both
 * servers happen to use the same session id. Length-prefixing makes the key unambiguous without
 * hashing away the values that make a debug dump useful.
 */
internal fun retainedChatViewModelKey(serverId: String?, sessionId: String): String {
    val scopedServerId = serverId.orEmpty()
    return "chat:${scopedServerId.length}:$scopedServerId:${sessionId.length}:$sessionId"
}
