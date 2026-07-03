package com.hermex.android.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Builds the SSE URL for a chat stream. Not a Retrofit method -- SSE bypasses Retrofit's
 * response-body handling entirely, since [SseClient] needs the raw streaming `okhttp3.Response`.
 */
fun chatStreamUrl(serverBaseUrl: String, streamId: String): HttpUrl =
    serverBaseUrl.toHttpUrl().newBuilder()
        .encodedPath("/api/chat/stream")
        .addQueryParameter("stream_id", streamId)
        .build()
