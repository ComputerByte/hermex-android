package com.hermex.android.navigation

import android.content.Intent
import java.net.URLDecoder

sealed interface HermexIntentDestination {
    data object Sessions : HermexIntentDestination
    data object Tasks : HermexIntentDestination
    data class Session(val sessionId: String) : HermexIntentDestination
    data class Task(val jobId: String) : HermexIntentDestination
    data class ShareText(val text: String) : HermexIntentDestination
}

fun Intent.hermexDestination(): HermexIntentDestination? = when (action) {
    Intent.ACTION_SEND -> shareTextDestination(getStringExtra(Intent.EXTRA_TEXT))
    Intent.ACTION_VIEW -> hermexDeepLinkDestination(dataString)
    else -> null
}

internal fun shareTextDestination(text: String?): HermexIntentDestination? {
    val sharedText = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return HermexIntentDestination.ShareText(sharedText)
}

internal fun hermexDeepLinkDestination(uriString: String?): HermexIntentDestination {
    val uri = uriString?.takeIf { it.startsWith("hermex://", ignoreCase = true) } ?: return HermexIntentDestination.Sessions
    val remainder = uri.substringAfter("://")
    val route = remainder.substringBefore('/').substringBefore('?').lowercase()
    val firstPathSegment = remainder
        .substringAfter('/', missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .decodeUrlSegmentOrNull()

    return when (route) {
        "sessions" -> HermexIntentDestination.Sessions
        "session" -> firstPathSegment?.takeIf { it.isNotBlank() }?.let(HermexIntentDestination::Session)
            ?: HermexIntentDestination.Sessions
        "tasks" -> HermexIntentDestination.Tasks
        "task" -> firstPathSegment?.takeIf { it.isNotBlank() }?.let(HermexIntentDestination::Task)
            ?: HermexIntentDestination.Tasks
        else -> HermexIntentDestination.Sessions
    }
}

private fun String.decodeUrlSegmentOrNull(): String? = runCatching { URLDecoder.decode(this, "UTF-8") }.getOrNull()
