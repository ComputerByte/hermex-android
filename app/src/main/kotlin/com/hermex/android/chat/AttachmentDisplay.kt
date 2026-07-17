package com.hermex.android.chat

import com.hermex.android.core.network.dto.MessageAttachment
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val imageFileExtensions = setOf("avif", "bmp", "gif", "heic", "heif", "ico", "jpeg", "jpg", "png", "webp")

/** Filename accepted by hermes-webui's session-scoped `/api/file/raw` upload fallback. Historical
 * messages contain only this name; freshly-uploaded messages also carry an absolute path on the
 * server, which is deliberately never treated as a path on the Android device. */
fun MessageAttachment.displayFileName(): String? =
    (name ?: path)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.takeIf { it.isNotEmpty() }

/** Reloaded history loses MIME metadata server-side, so common image extensions provide the
 * minimum deterministic fallback needed to keep historical image thumbnails visible. */
fun MessageAttachment.isImageForDisplay(): Boolean {
    if (isImage == true || mime?.startsWith("image/", ignoreCase = true) == true) return true
    val extension = displayFileName()?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    return extension in imageFileExtensions
}

/** Builds a URL Coil can fetch with the app's authenticated OkHttp client. The attachment's
 * server filesystem path is never exposed as a device-local model; only the session id and safe
 * basename cross the HTTP boundary. */
fun attachmentRawUrl(
    serverBaseUrl: String?,
    sessionId: String?,
    attachment: MessageAttachment,
): String? {
    val baseUrl = serverBaseUrl?.toHttpUrlOrNull() ?: return null
    val sid = sessionId?.takeIf { it.isNotBlank() } ?: return null
    val filename = attachment.displayFileName() ?: return null
    return baseUrl.newBuilder()
        .encodedPath("/api/file/raw")
        .addQueryParameter("session_id", sid)
        .addQueryParameter("path", filename)
        .build()
        .toString()
}
