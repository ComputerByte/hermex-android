package com.hermex.android.core.network

import com.hermex.android.core.util.TtftTracer
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response

/**
 * Feeds OkHttp's connection-level timings (DNS, TCP connect, TLS handshake, header arrival) into
 * [TtftTracer] for the two requests a chat turn actually makes -- `/api/chat/start` and
 * `/api/chat/stream`. Every other request on the shared clients (session polling, uploads, model
 * catalog refreshes) resolves to [EventListener.NONE] so the trace stays readable and this never
 * adds overhead off the chat path.
 */
class TtftEventListenerFactory : EventListener.Factory {
    override fun create(call: Call): EventListener {
        val label = call.request().url.encodedPath.let { path ->
            when {
                path.endsWith("/api/chat/start") -> "chat/start"
                path.endsWith("/api/chat/stream") -> "sse"
                else -> null
            }
        } ?: return EventListener.NONE

        return object : EventListener() {
            override fun callStart(call: Call) =
                TtftTracer.mark("HTTP request starts ($label)")

            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
                TtftTracer.mark("TCP connect start ($label)")

            override fun secureConnectStart(call: Call) =
                TtftTracer.mark("TLS handshake start ($label)")

            override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) =
                TtftTracer.mark("TCP/TLS connection established ($label, protocol=$protocol)")

            override fun connectionAcquired(call: Call, connection: Connection) =
                TtftTracer.mark("Connection acquired ($label)")

            override fun requestHeadersEnd(call: Call, request: okhttp3.Request) =
                TtftTracer.mark("Request sent ($label)")

            override fun responseHeadersStart(call: Call) =
                TtftTracer.mark("Response headers start ($label)")

            override fun responseHeadersEnd(call: Call, response: Response) =
                TtftTracer.mark("Response headers received ($label)")

            override fun responseBodyStart(call: Call) =
                TtftTracer.mark("Response body start ($label)")
        }
    }
}
