package com.hermex.android.core.network

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * End-to-end tests of [SseClient]'s actual OkHttp streaming + line-parsing logic against
 * [MockWebServer] serving literal SSE-formatted bytes -- the novel, previously-untested part of
 * this class (vs. [SseEventParserTest], which only tests the pure event-name/JSON mapping).
 */
class SseClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SseClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        client = SseClient(okHttpClient)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a simple token stream is parsed into the expected events in order`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                "event: token\ndata: {\"text\":\"Hello\"}\n\n" +
                    "event: token\ndata: {\"text\":\" world\"}\n\n" +
                    "event: stream_end\ndata: {}\n\n",
            ),
        )

        val events = client.stream(server.url("/api/chat/stream?stream_id=abc")).toList()

        assertEquals(
            listOf(SseEvent.Token("Hello"), SseEvent.Token(" world"), SseEvent.StreamEnd),
            events,
        )
    }

    @Test
    fun `heartbeat comment lines interleaved between events produce zero events of their own`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                ": heartbeat\n\n" +
                    "event: token\ndata: {\"text\":\"a\"}\n\n" +
                    ": heartbeat\n\n" +
                    ": another comment\n\n" +
                    "event: token\ndata: {\"text\":\"b\"}\n\n" +
                    ": heartbeat\n\n" +
                    "event: stream_end\ndata: {}\n\n",
            ),
        )

        val events = client.stream(server.url("/api/chat/stream?stream_id=abc")).toList()

        assertEquals(
            listOf(SseEvent.Token("a"), SseEvent.Token("b"), SseEvent.StreamEnd),
            events,
        )
    }

    @Test
    fun `a tool call started then completed produces matching stableIds`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                "event: tool\ndata: {\"name\":\"read_file\",\"tid\":\"t1\"}\n\n" +
                    "event: tool_complete\ndata: {\"tid\":\"t1\",\"duration\":0.5}\n\n" +
                    "event: done\ndata: {}\n\n",
            ),
        )

        val events = client.stream(server.url("/api/chat/stream?stream_id=abc")).toList()

        assertEquals(3, events.size)
        assertTrue(events[0] is SseEvent.ToolStarted)
        assertTrue(events[1] is SseEvent.ToolCompleted)
        assertEquals("t1", (events[0] as SseEvent.ToolStarted).payload.stableId)
        assertEquals("t1", (events[1] as SseEvent.ToolCompleted).payload.stableId)
        assertEquals(SseEvent.Done(null, null), events[2])
    }

    @Test
    fun `a malformed data line does not stop the stream -- subsequent valid events still parse`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                "event: token\ndata: not valid json\n\n" +
                    "event: token\ndata: {\"text\":\"still works\"}\n\n" +
                    "event: stream_end\ndata: {}\n\n",
            ),
        )

        val events = client.stream(server.url("/api/chat/stream?stream_id=abc")).toList()

        assertEquals(
            listOf(SseEvent.Unknown, SseEvent.Token("still works"), SseEvent.StreamEnd),
            events,
        )
    }

    @Test
    fun `a non-2xx response yields a single TransportError and no other events`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

        val events = client.stream(server.url("/api/chat/stream?stream_id=abc")).toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is SseEvent.TransportError)
        assertTrue((events[0] as SseEvent.TransportError).message.contains("500"))
    }

    @Test
    fun `an unreachable server yields a TransportError instead of throwing`() = runTest {
        val deadUrl = server.url("/api/chat/stream?stream_id=abc")
        server.shutdown()

        val events = client.stream(deadUrl).toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is SseEvent.TransportError)
    }

    @Test
    fun `cancel and error events with empty or minimal payloads are terminal and do not crash`() = runTest {
        server.enqueue(MockResponse().setBody("event: cancel\ndata: {}\n\n"))
        val cancelEvents = client.stream(server.url("/api/chat/stream?stream_id=abc")).toList()
        assertEquals(listOf(SseEvent.Cancelled), cancelEvents)

        server.enqueue(MockResponse().setBody("event: error\ndata: {\"error\":\"boom\"}\n\n"))
        val errorEvents = client.stream(server.url("/api/chat/stream?stream_id=def")).toList()
        assertEquals(listOf(SseEvent.Error("boom")), errorEvents)
    }

    @Test
    fun `an unrecognized event name does not stop the stream`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                "event: interim_assistant\ndata: {\"text\":\"draft\"}\n\n" +
                    "event: token\ndata: {\"text\":\"final\"}\n\n" +
                    "event: stream_end\ndata: {}\n\n",
            ),
        )

        val events = client.stream(server.url("/api/chat/stream?stream_id=abc")).toList()

        assertEquals(
            listOf(SseEvent.Unknown, SseEvent.Token("final"), SseEvent.StreamEnd),
            events,
        )
    }
}
