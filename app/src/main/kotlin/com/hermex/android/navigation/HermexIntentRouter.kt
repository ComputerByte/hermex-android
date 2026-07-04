package com.hermex.android.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import java.net.URLDecoder

sealed interface HermexIntentDestination {
    data object Sessions : HermexIntentDestination
    data object Tasks : HermexIntentDestination
    data class Session(val sessionId: String) : HermexIntentDestination
    data class Task(val jobId: String) : HermexIntentDestination
    data class ShareContent(val text: String? = null, val uri: Uri? = null) : HermexIntentDestination
}

fun Intent.hermexDestination(): HermexIntentDestination? = when (action) {
    Intent.ACTION_SEND -> {
        val text = getStringExtra(Intent.EXTRA_TEXT)
        val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        shareContentDestination(text, streamUri)
    }
    Intent.ACTION_VIEW -> hermexDeepLinkDestination(dataString)
    else -> null
}

internal fun shareContentDestination(text: String?, streamUri: Uri?): HermexIntentDestination? {
    val trimmedText = text?.trim()?.takeIf { it.isNotEmpty() }
    return if (trimmedText != null || streamUri != null) {
        HermexIntentDestination.ShareContent(text = trimmedText, uri = streamUri)
    } else {
        null
    }
}

internal fun hermexDeepLinkDestination(uriString: String?): HermexIntentDestination {
    val normalized = uriString?.trim()
    if (normalized == null) return HermexIntentDestination.Sessions

    val isHermex = normalized.startsWith("hermex://", ignoreCase = true)
    val isHermesAgent = normalized.startsWith("hermes-agent://", ignoreCase = true)
    if (!isHermex && !isHermesAgent) return HermexIntentDestination.Sessions

    val remainder = normalized.substringAfter("://")
    val route = remainder.substringBefore('/').substringBefore('?').lowercase()
    val firstPathSegment = remainder
        .substringAfter('/', missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .decodeUrlSegmentOrNull()

    return when (route) {
        "sessions" -> HermexIntentDestination.Sessions
        "session" -> extractSessionOrFallback(remainder, firstPathSegment)
        "tasks" -> HermexIntentDestination.Tasks
        "task" -> firstPathSegment?.takeIf { it.isNotBlank() }?.let(HermexIntentDestination::Task)
            ?: HermexIntentDestination.Tasks
        else -> HermexIntentDestination.Sessions
    }
}

/**
 * Prefers path-based session ID first (e.g. `hermex://session/abc`), then falls
 * back to iOS-style query parameters `?id=...` or `?session_id=...`.
 */
private fun extractSessionOrFallback(remainder: String, firstPathSegment: String?): HermexIntentDestination {
    firstPathSegment?.takeIf { it.isNotBlank() }?.let { return HermexIntentDestination.Session(it) }

    val queryString = remainder.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return HermexIntentDestination.Sessions
    val params = queryString.split('&')
    for (param in params) {
        val parts = param.split('=', limit = 2)
        if (parts.size == 2) {
            val key = parts[0].trim()
            if (key == "id" || key == "session_id") {
                val value = runCatching { URLDecoder.decode(parts[1].trim(), "UTF-8") }.getOrNull()
                if (!value.isNullOrBlank()) {
                    return HermexIntentDestination.Session(value)
                }
            }
        }
    }
    return HermexIntentDestination.Sessions
}

private fun String.decodeUrlSegmentOrNull(): String? = runCatching { URLDecoder.decode(this, "UTF-8") }.getOrNull()
