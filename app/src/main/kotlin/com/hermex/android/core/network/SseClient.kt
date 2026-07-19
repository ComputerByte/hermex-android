package com.hermex.android.core.network

import com.hermex.android.core.util.HermexLog
import com.hermex.android.core.util.TtftTracer
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface SseStreamSource {
    fun stream(url: HttpUrl): Flow<SseEvent>
}

/**
 * Hand-rolled SSE consumer over OkHttp's streaming response body -- no SSE library. The wire
 * format here is simple enough (`event: <name>\ndata: <json>\n\n`, `:`-prefixed heartbeat
 * comments, no multi-line data folding) that a small parser is clearer and more testable than a
 * dependency, and it keeps the exact heartbeat/error behavior fully visible.
 *
 * Deliberately does NOT implement reconnect-with-replay on a dropped connection (iOS has one via
 * an `after_seq` mechanism) -- a drop surfaces as [SseEvent.TransportError] and the caller
 * finalizes with whatever partial content exists. Documented follow-up, not built in this MVP.
 *
 * Must be given an [OkHttpClient] with `readTimeout = 0` (see [NetworkModule.sseClient]) -- the
 * server holds the connection open far longer than any bounded read timeout would allow.
 *
 * [readTimeoutMs] gates each individual line read (see [readLoopTimeoutMs] below) and is also the
 * single source [timeoutMessage] derives its user-facing "no data received for Ns" text from --
 * the two can never drift apart the way a hardcoded 120s timeout and a hardcoded "30s" message
 * once did. Overridable (default [DEFAULT_READ_TIMEOUT_MS]) so tests can exercise the timeout path
 * with a short real wait instead of the real 120s.
 */
class SseClient(
    private val okHttpClient: OkHttpClient,
    private val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
) : SseStreamSource {
    companion object {
        const val DEFAULT_READ_TIMEOUT_MS = 120_000L

        /** Pure text derivation from [timeoutMs] -- kept free of any Android/coroutine dependency
         * so the message itself is directly unit-testable without waiting out a real timeout. */
        fun timeoutMessage(timeoutMs: Long): String = "Stream timeout — no data received for ${timeoutMs / 1000}s"
    }

    override fun stream(url: HttpUrl): Flow<SseEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache, no-transform")
            .header("Accept-Encoding", "identity")
            .build()

        val call = okHttpClient.newCall(request)
        HermexLog.d("Sse", "connecting: ${url.encodedPath}?${url.encodedQuery}")

        val response = try {
            call.execute()
        } catch (e: IOException) {
            HermexLog.w("Sse", "connect failed", e)
            trySend(SseEvent.TransportError(e.message ?: "Network error"))
            close()
            awaitClose { call.cancel() }
            return@callbackFlow
        }

        if (!response.isSuccessful) {
            HermexLog.w("Sse", "connect rejected: HTTP ${response.code}")
            trySend(SseEvent.TransportError("HTTP ${response.code}"))
            response.close()
            close()
            awaitClose { call.cancel() }
            return@callbackFlow
        }
        HermexLog.d("Sse", "connected: HTTP ${response.code}")

        val source = response.body?.source()
        if (source == null) {
            trySend(SseEvent.TransportError("Empty response body"))
            response.close()
            close()
            awaitClose { call.cancel() }
            return@callbackFlow
        }

        try {
            var eventName = "message"
            val dataLines = StringBuilder()
            while (isActive) {
                val line = try {
                    withTimeout(readTimeoutMs) {  // long responses can have multi-minute gaps
                        source.readUtf8Line()
                    }
                } catch (e: IOException) {
                    // If the coroutine is no longer active, the stream was cancelled intentionally
                    // (user navigated away, model/profile switch, etc.) and the IOException is
                    // an expected artefact of canceling the underlying OkHttp call — do not
                    // surface a scary TransportError to the user.
                    if (!isActive) break
                    HermexLog.w("Sse", "read error", e)
                    trySend(SseEvent.TransportError(e.message ?: "Stream read error"))
                    null
                } catch (e: TimeoutCancellationException) {
                    val message = timeoutMessage(readTimeoutMs)
                    HermexLog.w("Sse", message)
                    trySend(SseEvent.TransportError(message))
                    null
                } ?: break

                when {
                    line.isEmpty() -> {
                        if (dataLines.isNotEmpty()) {
                            TtftTracer.markOnce("First SSE event received")
                            val event = SseEventParser.parse(eventName, dataLines.toString())
                            // Logged per-event (not the raw text) so logcat timestamps show
                            // arrival timing -- e.g. confirming tokens arrive incrementally
                            // rather than in one late burst, or that a heartbeat kept a quiet
                            // stream alive without a real event for 120+ seconds.
                            HermexLog.d("Sse", "event: $eventName -> ${event::class.simpleName}")
                            trySend(event)
                        }
                        eventName = "message"
                        dataLines.clear()
                    }
                    line.startsWith(":") -> HermexLog.d("Sse", "heartbeat")
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (dataLines.isNotEmpty()) dataLines.append('\n')
                        dataLines.append(line.removePrefix("data:").trim())
                    }
                    // "id:"/"retry:" lines: not needed for this MVP (no reconnect/replay).
                }
            }
            HermexLog.d("Sse", "read loop ended (server EOF or cancelled)")
        } finally {
            response.close()
        }

        close()
        awaitClose {
            HermexLog.d("Sse", "collector cancelled -- cancelling call")
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
