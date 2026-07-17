package com.hermex.android.chat

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.cache.FakeOfflineCacheRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.userMessage
import com.hermex.android.core.network.SseEvent
import com.hermex.android.core.network.SseStreamSource
import com.hermex.android.core.network.ToolEventPayload
import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.ModelCatalogOption
import com.hermex.android.core.network.dto.SessionDetail
import com.hermex.android.core.network.dto.UploadResponse
import com.hermex.android.core.storage.ChatPreferencesStore
import com.hermex.android.core.storage.FakeServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSseClient(private val flowProvider: (HttpUrl) -> Flow<SseEvent>) : SseStreamSource {
    var lastUrl: HttpUrl? = null
    override fun stream(url: HttpUrl): Flow<SseEvent> {
        lastUrl = url
        return flowProvider(url)
    }
}

private class FakeChatPreferencesStore(
    private var expandThinkingByDefault: Boolean = false,
    private var expandToolCallsByDefault: Boolean = false,
    private var notificationsEnabled: Boolean = false,
) : ChatPreferencesStore {
    override suspend fun loadExpandThinkingByDefault(): Boolean = expandThinkingByDefault
    override suspend fun setExpandThinkingByDefault(value: Boolean) { expandThinkingByDefault = value }
    override suspend fun loadExpandToolCallsByDefault(): Boolean = expandToolCallsByDefault
    override suspend fun setExpandToolCallsByDefault(value: Boolean) { expandToolCallsByDefault = value }
    override suspend fun loadNotificationsEnabled(): Boolean = notificationsEnabled
    override suspend fun setNotificationsEnabled(value: Boolean) { notificationsEnabled = value }
    override suspend fun loadShowSubagentSessions(): Boolean = true
    override suspend fun setShowSubagentSessions(value: Boolean) {}
}

/** See [com.hermex.android.sessions.SessionListViewModelTest] for why this pattern (Turbine +
 * UnconfinedTestDispatcher, not StandardTestDispatcher + advanceUntilIdle) is required here:
 * Retrofit/OkHttp calls resume on a real background thread, not kotlinx-coroutines-test's
 * virtual clock. */
private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

/** [ChatViewModel.refreshCacheInBackground] writes to the cache from its own untracked
 * `viewModelScope.launch`, with no corresponding [ChatUiState] emission to synchronize on via
 * Turbine -- so unlike every other wait in this file, this has to poll for real (the completion
 * genuinely depends on a second real network round trip finishing on a background thread, which
 * `runTest`'s virtual clock has no visibility into).
 *
 * [until] must check for the *specific* expected end state, not just non-null: [loadSession]
 * itself opportunistically caches the detail from its own initial fetch, so a plain "is there
 * anything cached yet" check can trivially match that earlier write before the later
 * turn-completion refresh this function is actually meant to wait for ever lands. */
private fun waitUntilCached(
    cache: FakeOfflineCacheRepository,
    serverId: String,
    sessionId: String,
    until: (SessionDetail) -> Boolean = { true },
): SessionDetail {
    val deadlineMillis = System.currentTimeMillis() + 2_000
    while (System.currentTimeMillis() < deadlineMillis) {
        val result = runBlocking { cache.cachedSessionDetail(serverId, sessionId) }
        if (result != null && until(result)) return result
        Thread.sleep(10)
    }
    error("cache was never populated with the expected content within the timeout")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private suspend fun loggedInRepository(): AuthRepository {
        lateinit var repo: AuthRepository
        val networkModule = NetworkModule(FakeCookieStore()) { repo.handleUnauthorized() }
        repo = AuthRepository(networkModule, FakeServerStore(server.url("/").toString()))
        repo.restoreSavedServer()
        return repo
    }

    @Test
    fun `loadSession populates messages on success`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[{"role":"user","content":"hi"}]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}""")) // GET /api/profiles

        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(1, loaded.messages.size)
            assertEquals("hi", loaded.messages.first().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stageDraftIfComposerEmpty stages shared text without sending`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            viewModel.stageDraftIfComposerEmpty(" Review before sending ")
            val staged = awaitUntil { it.composerText == "Review before sending" }

            assertEquals("Review before sending", staged.composerText)
            assertEquals(false, staged.isSending)
            assertEquals(false, staged.isStreaming)
            assertTrue(staged.messages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stageDraftIfComposerEmpty does not overwrite existing composer text`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            viewModel.onComposerTextChanged("local draft")
            awaitUntil { it.composerText == "local draft" }
            viewModel.stageDraftIfComposerEmpty("shared draft")

            assertEquals("local draft", viewModel.uiState.value.composerText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a full turn -- tokens, tool start+complete, done -- finalizes the message and clears streaming state`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}""")) // GET /api/profiles
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val scriptedEvents = listOf(
            SseEvent.Token("Hello "),
            SseEvent.Token("world"),
            SseEvent.ToolStarted(ToolEventPayload(null, "read_file", "reading", null, null, null, "t1")),
            SseEvent.ToolCompleted(ToolEventPayload(null, "read_file", "done reading", null, 0.5, false, "t1")),
            SseEvent.Done(null, null),
        )
        val fakeSseClient = FakeSseClient { scriptedEvents.asFlow() }
        val viewModel = ChatViewModel("s1", authRepository, fakeSseClient, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            viewModel.onComposerTextChanged("Read the file please")
            viewModel.sendMessage()

            // isStreaming defaults to false, so we must confirm the turn actually started before
            // waiting for it to end -- otherwise "!it.isStreaming" matches the pre-turn state too.
            awaitUntil { it.isStreaming }
            val finalState = awaitUntil { !it.isStreaming }

            assertEquals("", finalState.streamingText)
            assertEquals("", finalState.streamingReasoning)
            assertEquals(false, finalState.isStreaming)

            // messages: [user "Read the file please", assistant "Hello world"]
            assertEquals(2, finalState.messages.size)
            assertEquals("user", finalState.messages[0].role)
            assertEquals("assistant", finalState.messages[1].role)
            assertEquals("Hello world", finalState.messages[1].content)

            assertEquals(1, finalState.activeToolCalls.size)
            val toolCall = finalState.activeToolCalls.first()
            assertEquals("t1", toolCall.stableId)
            assertEquals(true, toolCall.isComplete)
            assertEquals(false, toolCall.isError)
            assertEquals(0.5, toolCall.durationSeconds)
            assertEquals("done reading", toolCall.preview) // updated by the completion event
            // The tool call started while only the optimistic user message (index 0) existed,
            // so it's anchored to render right before the assistant reply that lands at index 1.
            assertEquals(1, toolCall.anchorMessageCount)

            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fakeSseClient.lastUrl.toString().contains("stream_id=stream-1"))
    }

    // ── Response-completion notification tests ──

    private class SpyResponseCompletionNotifier : ResponseCompletionNotifier {
        var lastSessionId: String? = null
        var lastCompletedNormally: Boolean? = null

        override fun onResponseCompleted(sessionId: String, completedNormally: Boolean) {
            lastSessionId = sessionId
            lastCompletedNormally = completedNormally
        }
    }

    @Test
    fun `SseEvent Done triggers notifier with completedNormally true`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val notifier = SpyResponseCompletionNotifier()
        val viewModel = ChatViewModel(
            "s1", authRepository,
            FakeSseClient { listOf(SseEvent.Token("hello"), SseEvent.Done(null, null)).asFlow() },
            FakeChatPreferencesStore(),
            responseCompletionNotifier = notifier,
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("test")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            // Wait for finalization after Done
            val finalState = awaitUntil { !it.isStreaming }
            assertNotNull(finalState.messages)
            assertEquals("s1", notifier.lastSessionId)
            assertEquals(true, notifier.lastCompletedNormally)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SseEvent Error does not trigger normal notification`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val notifier = SpyResponseCompletionNotifier()
        val viewModel = ChatViewModel(
            "s1", authRepository,
            FakeSseClient { listOf(SseEvent.Error("server error")).asFlow() },
            FakeChatPreferencesStore(),
            responseCompletionNotifier = notifier,
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("test")
            viewModel.sendMessage()
            awaitUntil { !it.isStreaming }

            assertNull(notifier.lastSessionId)
            assertNull(notifier.lastCompletedNormally)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SseEvent StreamEnd triggers notifier with completedNormally true`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val notifier = SpyResponseCompletionNotifier()
        val viewModel = ChatViewModel(
            "s1", authRepository,
            FakeSseClient { listOf(SseEvent.Token("done"), SseEvent.StreamEnd).asFlow() },
            FakeChatPreferencesStore(),
            responseCompletionNotifier = notifier,
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("test")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            awaitUntil { !it.isStreaming }
            assertEquals(true, notifier.lastCompletedNormally)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelStream does not trigger notification`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}""")) // cancel response

        val notifier = SpyResponseCompletionNotifier()
        val viewModel = ChatViewModel(
            "s1", authRepository,
            FakeSseClient { flow { awaitCancellation() } }, // never completes on its own
            FakeChatPreferencesStore(),
            responseCompletionNotifier = notifier,
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("test")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }

            viewModel.cancelStream()
            awaitUntil { !it.isStreaming }

            assertNull(notifier.lastSessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default no-op notifier does not break existing completion flow`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        // No responseCompletionNotifier argument — uses the no-op default.
        val viewModel = ChatViewModel(
            "s1", authRepository,
            FakeSseClient { listOf(SseEvent.Token("hello"), SseEvent.Done(null, null)).asFlow() },
            FakeChatPreferencesStore(),
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("works")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            val finalState = awaitUntil { !it.isStreaming }
            assertEquals(2, finalState.messages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tool calls from different turns anchor to their own turn's message index, not the last one`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}""")) // GET /api/profiles

        // Two chat/start calls (one per turn), each opening its own scripted SSE flow via a
        // request-counting FakeSseClient below. Each turn's completion also fires an untracked
        // background cache-refresh GET /api/session/{id} (see ChatViewModel.refreshCacheInBackground),
        // so one extra response has to be queued between the two turns for it to consume.
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}""")) // background cache refresh after turn 1
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-2","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}""")) // background cache refresh after turn 2

        val turnOneEvents = listOf(
            SseEvent.ToolStarted(ToolEventPayload(null, "tool_a", null, null, null, null, "call-a")),
            SseEvent.ToolCompleted(ToolEventPayload(null, "tool_a", null, null, 0.1, false, "call-a")),
            SseEvent.Token("first reply"),
            SseEvent.Done(null, null),
        )
        val turnTwoEvents = listOf(
            SseEvent.ToolStarted(ToolEventPayload(null, "tool_b", null, null, null, null, "call-b")),
            SseEvent.ToolCompleted(ToolEventPayload(null, "tool_b", null, null, 0.2, false, "call-b")),
            SseEvent.Token("second reply"),
            SseEvent.Done(null, null),
        )
        var callCount = 0
        val fakeSseClient = FakeSseClient {
            callCount += 1
            if (callCount == 1) turnOneEvents.asFlow() else turnTwoEvents.asFlow()
        }
        val viewModel = ChatViewModel("s1", authRepository, fakeSseClient, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            viewModel.onComposerTextChanged("first message")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            awaitUntil { !it.isStreaming }

            viewModel.onComposerTextChanged("second message")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            val finalState = awaitUntil { !it.isStreaming }

            // messages: [user1, assistant1, user2, assistant2]
            assertEquals(4, finalState.messages.size)
            assertEquals(2, finalState.activeToolCalls.size)

            val toolA = finalState.activeToolCalls.first { it.stableId == "call-a" }
            val toolB = finalState.activeToolCalls.first { it.stableId == "call-b" }
            // tool_a started when only [user1] existed (index 0) -> anchors before assistant1 (index 1).
            assertEquals(1, toolA.anchorMessageCount)
            // tool_b started when [user1, assistant1, user2] existed (index 2) -> anchors before assistant2 (index 3).
            assertEquals(3, toolB.anchorMessageCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an error mid-stream finalizes with whatever partial content exists and sets errorMessage`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}""")) // GET /api/profiles
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val scriptedEvents = listOf(SseEvent.Token("partial reply"), SseEvent.Error("connection reset"))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { scriptedEvents.asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hello")
            viewModel.sendMessage()

            awaitUntil { it.isStreaming }
            val finalState = awaitUntil { !it.isStreaming }

            assertEquals("connection reset", finalState.errorMessage)
            assertEquals(false, finalState.isStreaming)
            // the partial reply is still preserved as a finalized message, not discarded
            assertEquals(2, finalState.messages.size)
            assertEquals("partial reply", finalState.messages[1].content)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unrecognized events never touch chat state`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}""")) // GET /api/profiles
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val scriptedEvents = listOf(SseEvent.Unknown, SseEvent.Token("x"), SseEvent.Unknown, SseEvent.Done(null, null))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { scriptedEvents.asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()

            awaitUntil { it.isStreaming }
            val finalState = awaitUntil { !it.isStreaming }

            assertEquals("x", finalState.messages.last().content)
            assertNull(finalState.errorMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelStream finalizes immediately without waiting for the stream to close on its own`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}""")) // GET /api/profiles
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}""")) // /api/chat/cancel

        // A flow that emits one token then hangs forever -- simulates a still-open stream, so
        // finalization can only happen via an explicit cancelStream() call, not natural completion.
        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("still typing"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "still typing" }

            viewModel.cancelStream()
            val afterCancel = awaitUntil { !it.isStreaming }

            assertEquals("", afterCancel.streamingText)
            assertEquals(2, afterCancel.messages.size)
            assertEquals("still typing", afterCancel.messages[1].content)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelStream success clears streaming and allows sending the next message`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"cancelled":true}""")) // /api/chat/cancel
        // cancelStream() -> finalizeStream() always fires an untracked background cache refresh
        // (see ChatViewModel.refreshCacheInBackground); queue its response too so it doesn't
        // steal the "second send" response enqueued further down.
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("still typing"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "still typing" }

            viewModel.cancelStream()
            val afterCancel = awaitUntil { !it.isStreaming }
            assertNull(afterCancel.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
        repeat(5) { server.takeRequest() } // session, profiles, chat/start, chat/cancel, background cache refresh

        // A second send must actually be allowed (isSending/isStreaming both settled false).
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-2","session_id":"s1"}"""))
        viewModel.uiState.test {
            viewModel.onComposerTextChanged("another message")
            viewModel.sendMessage()
            val afterSecondSend = awaitUntil { it.isStreaming }
            assertTrue(afterSecondSend.messages.any { it.content == "another message" })
            cancelAndIgnoreRemainingEvents()
        }
        val secondChatStart = server.takeRequest()
        assertTrue(secondChatStart.path?.contains("/api/chat/start") == true)
    }

    @Test
    fun `cancelStream failure surfaces an error but still finalizes locally without corrupting messages`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setResponseCode(500)) // /api/chat/cancel fails

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("still typing"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "still typing" }

            viewModel.cancelStream()
            // The local finalize (isStreaming -> false) happens synchronously; the server-side
            // failure surfaces slightly later as errorMessage, once the failed call returns.
            val afterCancel = awaitUntil { !it.isStreaming }
            assertEquals("", afterCancel.streamingText)
            assertEquals(2, afterCancel.messages.size) // user message + the finalized partial reply
            assertEquals("still typing", afterCancel.messages[1].content)

            val afterFailure = awaitUntil { it.errorMessage != null }
            assertTrue(afterFailure.errorMessage != null)
            // The failure must not have reopened the stream or touched the already-finalized messages.
            assertEquals(false, afterFailure.isStreaming)
            assertEquals(2, afterFailure.messages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelStream does not restart the stream or send a second chat-start`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}""")) // /api/chat/cancel
        // cancelStream() -> finalizeStream() always fires an untracked background cache refresh --
        // queue its response too so it doesn't hang around unconsumed.
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("still typing"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "still typing" }

            viewModel.cancelStream()
            awaitUntil { !it.isStreaming }
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        server.takeRequest() // chat/start
        val cancelRequest = server.takeRequest()
        assertTrue(cancelRequest.path?.contains("/api/chat/cancel") == true)
        server.takeRequest() // background cache refresh
        // Nothing else was ever sent -- specifically no second /api/chat/start.
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `cancelStream while showing cached data asks to reconnect instead of calling the server`() = runTest {
        authRepository = loggedInRepository()
        val serverId = authRepository.activeServerId()!!
        val cache = FakeOfflineCacheRepository()
        cache.cacheSessionDetail(
            serverId,
            "s1",
            SessionDetail(sessionId = "s1", messages = listOf(ChatMessage(role = "user", content = "cached msg"))),
        )
        server.enqueue(MockResponse().setResponseCode(500)) // initial load fails -> falls back to cache
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(),
            cache,
        )

        viewModel.uiState.test {
            val settled = awaitUntil { !it.isLoading }
            assertTrue(settled.isShowingCachedData)
            cancelAndIgnoreRemainingEvents()
        }
        server.takeRequest() // session (failed)
        server.takeRequest() // profiles

        viewModel.uiState.test {
            viewModel.cancelStream()
            val afterAttempt = awaitUntil { it.errorMessage != null }
            assertEquals("Reconnect to control this run.", afterAttempt.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS)) // no chat/cancel fired
    }

    @Test
    fun `switching the active server mid-stream closes the local stream instead of leaving it pointed at the old server`() = runTest {
        val serverStore = FakeServerStore(server.url("/").toString())
        val secondConfig = serverStore.addServer("Second", server.url("/").toString())
        val networkModule = NetworkModule(FakeCookieStore()) { authRepository.handleUnauthorized() }
        authRepository = AuthRepository(networkModule, serverStore)
        authRepository.restoreSavedServer()

        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        // What refreshCacheInBackground() fetches once the switch finalizes the stream -- the
        // second "server" here is the same MockWebServer instance (only serverId-scoping is under
        // test, matching the established convention in the multi-server cache-isolation tests
        // above), so this response is genuinely consumed rather than hitting a real host.
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("still typing"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }

            authRepository.switchActiveServer(secondConfig.id)

            val afterSwitch = awaitUntil { !it.isStreaming }
            assertTrue(afterSwitch.errorMessage?.contains("active server changed") == true)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val chatStartRequest = server.takeRequest()
        assertTrue(chatStartRequest.path?.contains("/api/chat/start") == true)
        // No /api/chat/cancel was ever sent -- the stale server's stream was closed locally only.
        val nextRequest = server.takeRequest()
        assertTrue(
            "the only remaining request should be the background cache refresh, not a chat/cancel",
            nextRequest.path?.contains("/api/chat/cancel") != true,
        )
    }

    @Test
    fun `repeated identical token text is appended, never deduplicated`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        // "la" arrives three times in a row -- a naive dedup pass would collapse these.
        val scriptedEvents = listOf(SseEvent.Token("la"), SseEvent.Token("la"), SseEvent.Token("la"), SseEvent.Done(null, null))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { scriptedEvents.asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("sing it")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            val finalState = awaitUntil { !it.isStreaming }
            assertEquals("lalala", finalState.messages.last().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectProfile switches the active profile immediately when the session has no messages`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(
            MockResponse().setBody("""{"active":"default","profiles":[{"name":"default"},{"name":"work"}]}"""),
        )
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"active":"work","profiles":[{"name":"default"},{"name":"work"}]}"""))

        viewModel.uiState.test {
            viewModel.selectProfile("work")
            // isSwitchingProfile defaults to false, so confirm the switch actually started before
            // waiting for it to finish -- otherwise "!it.isSwitchingProfile" matches the
            // pre-switch state too.
            awaitUntil { it.isSwitchingProfile }
            val afterSwitch = awaitUntil { !it.isSwitchingProfile }
            assertEquals("work", afterSwitch.selectedProfileName)
            assertNull(afterSwitch.pendingProfileSwitch)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val switchRequest = server.takeRequest()
        assertTrue(switchRequest.path?.contains("/api/profile/switch") == true)
    }

    @Test
    fun `selectProfile with existing messages asks for confirmation instead of switching immediately`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[{"role":"user","content":"hi"}]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"active":"default","profiles":[{"name":"default"},{"name":"work"}]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            // loadProfiles() fires on whichever real thread the session response callback lands
            // on, with no happens-before relationship to this test thread -- waiting for
            // profileOptions to actually populate (rather than just "isLoading flipped") is what
            // guarantees the profiles request has already landed before we drain it below.
            awaitUntil { it.profileOptions.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
        server.takeRequest() // session
        server.takeRequest() // profiles

        viewModel.uiState.test {
            viewModel.selectProfile("work")
            val afterSelect = awaitUntil { it.pendingProfileSwitch != null }
            assertEquals("work", afterSelect.pendingProfileSwitch)
            assertEquals("default", afterSelect.selectedProfileName) // not switched yet
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS)) // no network call fired
    }

    @Test
    fun `dismissPendingProfileSwitch clears the pending switch without calling the API`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[{"role":"user","content":"hi"}]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[{"name":"default"},{"name":"work"}]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            awaitUntil { it.profileOptions.isNotEmpty() } // see comment in the selectProfile test above
            cancelAndIgnoreRemainingEvents()
        }
        server.takeRequest() // session
        server.takeRequest() // profiles

        viewModel.selectProfile("work")

        viewModel.uiState.test {
            viewModel.dismissPendingProfileSwitch()
            val afterDismiss = awaitUntil { it.pendingProfileSwitch == null }
            assertNull(afterDismiss.pendingProfileSwitch)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `confirmPendingProfileSwitch switches the profile, creates a new session, and hands off the new session id`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[{"role":"user","content":"hi"}]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[{"name":"default"},{"name":"work"}]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        viewModel.selectProfile("work")

        server.enqueue(MockResponse().setBody("""{"active":"work","profiles":[{"name":"default"},{"name":"work"}]}"""))
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s2"}}"""))
        var newSessionId: String? = null

        viewModel.uiState.test {
            viewModel.confirmPendingProfileSwitch { newSessionId = it }
            awaitUntil { it.isSwitchingProfile }
            val afterSwitch = awaitUntil { !it.isSwitchingProfile }
            assertNull(afterSwitch.pendingProfileSwitch)
            assertEquals("work", afterSwitch.selectedProfileName)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("s2", newSessionId)

        server.takeRequest() // session
        server.takeRequest() // profiles
        val switchRequest = server.takeRequest()
        assertTrue(switchRequest.path?.contains("/api/profile/switch") == true)
        val newSessionRequest = server.takeRequest()
        assertTrue(newSessionRequest.path?.contains("/api/session/new") == true)
        assertTrue(newSessionRequest.body.readUtf8().contains("\"profile\":\"work\""))
    }

    @Test
    fun `loads expandThinkingByDefault from the preferences store at init`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(expandThinkingByDefault = true),
        )

        viewModel.uiState.test {
            val loaded = awaitUntil { it.expandThinkingByDefault }
            assertTrue(loaded.expandThinkingByDefault)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads expandToolCallsByDefault from the preferences store at init`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(expandToolCallsByDefault = true),
        )

        viewModel.uiState.test {
            val loaded = awaitUntil { it.expandToolCallsByDefault }
            assertTrue(loaded.expandToolCallsByDefault)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -- Model switching inside chats --

    @Test
    fun `loadSession stores the session's current workspace, model, and modelProvider`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[],"workspace":"/home/user","model":"gpt-5.5","model_provider":"openai"}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals("/home/user", loaded.currentWorkspace)
            assertEquals("gpt-5.5", loaded.currentModel)
            assertEquals("openai", loaded.currentModelProvider)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshModelCatalogForPickerOpen loads the catalog and overlays live models`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"groups":[{"provider_id":"openai","models":[{"id":"gpt-5.5"}]}]}"""))
        server.enqueue(MockResponse().setBody("""{"provider":"openai","models":[{"id":"gpt-5.5"},{"id":"gpt-5.5-mini"}]}"""))

        viewModel.uiState.test {
            viewModel.refreshModelCatalogForPickerOpen()
            awaitUntil { it.isLoadingModelCatalog }
            val loaded = awaitUntil { !it.isLoadingModelCatalog }
            assertEquals(1, loaded.modelCatalogGroups.size)
            assertEquals(2, loaded.modelCatalogGroups.first().models.size) // live overlay landed
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectComposerModel updates the session in place via session update, not a new session`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[],"workspace":"/ws","model":"a","model_provider":"openai"}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","model":"b","model_provider":"anthropic","workspace":"/ws"}}""",
            ),
        )

        viewModel.uiState.test {
            viewModel.selectComposerModel(ModelCatalogOption(id = "b", displayName = "b", providerId = "anthropic"))
            awaitUntil { it.isUpdatingComposerConfiguration }
            val afterUpdate = awaitUntil { !it.isUpdatingComposerConfiguration }
            assertEquals("b", afterUpdate.currentModel)
            assertEquals("anthropic", afterUpdate.currentModelProvider)
            assertTrue(afterUpdate.pendingExplicitModelPick)
            assertNull(afterUpdate.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val updateRequest = server.takeRequest()
        assertTrue(updateRequest.path?.contains("/api/session/update") == true)
        assertTrue(updateRequest.body.readUtf8().contains("\"model\":\"b\""))
        assertTrue(!updateRequest.path.orEmpty().contains("/api/session/new"))
    }

    @Test
    fun `selectComposerModel is a no-op when the option already matches the current selection`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[],"model":"a","model_provider":"openai"}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        server.takeRequest()
        server.takeRequest()

        viewModel.selectComposerModel(ModelCatalogOption(id = "a", displayName = "a", providerId = "openai"))

        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `selectComposerModel is blocked while a stream is active`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[],"model":"a"}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("still typing"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            cancelAndIgnoreRemainingEvents()
        }
        repeat(3) { server.takeRequest() } // session, profiles, chat/start

        viewModel.uiState.test {
            viewModel.selectComposerModel(ModelCatalogOption(id = "b", displayName = "b", providerId = "openai"))
            val afterAttempt = awaitUntil { it.errorMessage != null }
            assertEquals("Wait for the current response to finish before changing models.", afterAttempt.errorMessage)
            assertEquals("a", afterAttempt.currentModel) // unchanged
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS)) // no session/update fired
    }

    @Test
    fun `selectComposerModel failure keeps the previous model and surfaces an error`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody("""{"session":{"session_id":"s1","messages":[],"model":"a","model_provider":"openai"}}"""),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setResponseCode(500))

        viewModel.uiState.test {
            viewModel.selectComposerModel(ModelCatalogOption(id = "b", displayName = "b", providerId = "anthropic"))
            awaitUntil { it.isUpdatingComposerConfiguration }
            val afterUpdate = awaitUntil { !it.isUpdatingComposerConfiguration }
            assertEquals("a", afterUpdate.currentModel)
            assertEquals("openai", afterUpdate.currentModelProvider)
            assertTrue(afterUpdate.errorMessage != null)
            assertTrue(!afterUpdate.pendingExplicitModelPick)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessage includes the session's current workspace, model, modelProvider, and profile`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[],"workspace":"/ws","model":"gpt-5.5","model_provider":"openai"}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"active":"work","profiles":[{"name":"work"}]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        viewModel.uiState.test {
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val chatStartRequest = server.takeRequest().body.readUtf8()
        assertTrue(chatStartRequest.contains("\"workspace\":\"/ws\""))
        assertTrue(chatStartRequest.contains("\"model\":\"gpt-5.5\""))
        assertTrue(chatStartRequest.contains("\"model_provider\":\"openai\""))
        assertTrue(chatStartRequest.contains("\"profile\":\"work\""))
    }

    @Test
    fun `explicitModelPick is sent once after a manual model selection, then cleared`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[],"workspace":"/ws","model":"a","model_provider":"openai"}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(
            MockResponse().setBody("""{"session":{"session_id":"s1","model":"b","model_provider":"anthropic","workspace":"/ws"}}"""),
        )
        viewModel.uiState.test {
            viewModel.selectComposerModel(ModelCatalogOption(id = "b", displayName = "b", providerId = "anthropic"))
            awaitUntil { it.pendingExplicitModelPick }
            cancelAndIgnoreRemainingEvents()
        }
        server.takeRequest() // session
        server.takeRequest() // profiles
        server.takeRequest() // session/update

        // First send after the pick: explicit_model_pick should be true, and clear afterwards.
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        viewModel.uiState.test {
            viewModel.onComposerTextChanged("first")
            viewModel.sendMessage()
            val afterSend = awaitUntil { it.isStreaming }
            assertTrue(!afterSend.pendingExplicitModelPick)
            cancelAndIgnoreRemainingEvents()
        }
        val firstChatStart = server.takeRequest().body.readUtf8()
        assertTrue(firstChatStart.contains("\"explicit_model_pick\":true"))
        server.enqueue(MockResponse().setBody("""{"ok":true}""")) // GET /api/chat/cancel
        viewModel.cancelStream()
        server.takeRequest() // chat/cancel

        // cancelStream() also finalizes the (empty) stream locally, which fires an untracked
        // background cache refresh (see ChatViewModel.refreshCacheInBackground) -- drain that
        // request with its own response now, before it can steal the "second send" response below.
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.takeRequest() // background cache refresh

        // Second send: no pick pending anymore, so the field is omitted entirely.
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-2","session_id":"s1"}"""))
        viewModel.uiState.test {
            viewModel.onComposerTextChanged("second")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            cancelAndIgnoreRemainingEvents()
        }
        val secondChatStart = server.takeRequest().body.readUtf8()
        assertTrue(!secondChatStart.contains("explicit_model_pick"))
    }

    // -- Offline chat cache (Phase 2) --

    @Test
    fun `loadSession shows cached messages immediately, then replaces them with fresh data and refreshes the cache`() = runTest {
        authRepository = loggedInRepository()
        val serverId = authRepository.activeServerId()!!
        val cache = FakeOfflineCacheRepository()
        cache.cacheSessionDetail(
            serverId,
            "s1",
            SessionDetail(sessionId = "s1", messages = listOf(ChatMessage(role = "user", content = "cached msg"))),
        )
        server.enqueue(
            MockResponse().setBody("""{"session":{"session_id":"s1","messages":[{"role":"user","content":"fresh msg"}]}}"""),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(),
            cache,
        )

        viewModel.uiState.test {
            val cachedFirst = awaitUntil { it.isShowingCachedData }
            assertEquals("cached msg", cachedFirst.messages.single().content)
            assertTrue(cachedFirst.cacheStatusMessage != null)

            val fresh = awaitUntil { !it.isLoading }
            assertEquals("fresh msg", fresh.messages.single().content)
            assertTrue(!fresh.isShowingCachedData)
            assertNull(fresh.cacheStatusMessage)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("fresh msg", cache.cachedSessionDetail(serverId, "s1")?.messages?.single()?.content)
    }

    @Test
    fun `loadSession falls back to the cached conversation when the network call fails`() = runTest {
        authRepository = loggedInRepository()
        val serverId = authRepository.activeServerId()!!
        val cache = FakeOfflineCacheRepository()
        cache.cacheSessionDetail(
            serverId,
            "s1",
            SessionDetail(sessionId = "s1", messages = listOf(ChatMessage(role = "user", content = "cached msg"))),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(),
            cache,
        )

        viewModel.uiState.test {
            val settled = awaitUntil { !it.isLoading }
            assertEquals("cached msg", settled.messages.single().content)
            assertTrue(settled.isShowingCachedData)
            assertNull("a cache fallback is not an error state", settled.errorMessage)
            assertTrue(settled.cacheStatusMessage != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSession with no cache and a network failure shows the normal error state, not fake data`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(),
            FakeOfflineCacheRepository(),
        )

        viewModel.uiState.test {
            val settled = awaitUntil { !it.isLoading }
            assertTrue(settled.messages.isEmpty())
            assertTrue(!settled.isShowingCachedData)
            assertNull(settled.cacheStatusMessage)
            assertTrue(settled.errorMessage != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying loadSession after a cache fallback clears the banner once the network recovers`() = runTest {
        authRepository = loggedInRepository()
        val serverId = authRepository.activeServerId()!!
        val cache = FakeOfflineCacheRepository()
        cache.cacheSessionDetail(
            serverId,
            "s1",
            SessionDetail(sessionId = "s1", messages = listOf(ChatMessage(role = "user", content = "cached msg"))),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(),
            cache,
        )
        viewModel.uiState.test {
            val settled = awaitUntil { !it.isLoading }
            assertTrue(settled.isShowingCachedData)
            cancelAndIgnoreRemainingEvents()
        }

        server.enqueue(
            MockResponse().setBody("""{"session":{"session_id":"s1","messages":[{"role":"user","content":"recovered msg"}]}}"""),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        viewModel.uiState.test {
            viewModel.loadSession()
            awaitUntil { it.isLoading }
            val recovered = awaitUntil { !it.isLoading }
            assertEquals("recovered msg", recovered.messages.single().content)
            assertTrue(!recovered.isShowingCachedData)
            assertNull(recovered.cacheStatusMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each server only ever sees its own cached chat messages`() = runTest {
        val cache = FakeOfflineCacheRepository()

        // Two distinct "servers" (distinct ids from FakeServerStore) both happen to point at the
        // same mock server here -- only the cache's serverId-scoping is under test, not real host
        // isolation (mirrors SessionListViewModelTest's equivalent multi-server test).
        val storeA = FakeServerStore(server.url("/").toString())
        val idA = storeA.activeServerSnapshot()!!.id
        cache.cacheSessionDetail(
            idA,
            "s1",
            SessionDetail(sessionId = "s1", messages = listOf(ChatMessage(role = "user", content = "Server A message"))),
        )

        val storeB = FakeServerStore(server.url("/").toString())
        val idB = storeB.activeServerSnapshot()!!.id
        cache.cacheSessionDetail(
            idB,
            "s1",
            SessionDetail(sessionId = "s1", messages = listOf(ChatMessage(role = "user", content = "Server B message"))),
        )

        lateinit var repoB: AuthRepository
        val networkModuleB = NetworkModule(FakeCookieStore()) { repoB.handleUnauthorized() }
        repoB = AuthRepository(networkModuleB, storeB)
        repoB.restoreSavedServer()
        server.enqueue(MockResponse().setResponseCode(500)) // force a cache fallback for server B
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))

        val viewModel = ChatViewModel(
            "s1",
            repoB,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(),
            cache,
        )

        viewModel.uiState.test {
            val settled = awaitUntil { !it.isLoading }
            assertEquals("Server B message", settled.messages.single().content)
            assertTrue(settled.messages.none { it.content == "Server A message" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sending while offline is blocked with a clear error and never queued as a fake sent message`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.shutdown() // simulate loss of connectivity: the next request fails with an IOException

        viewModel.uiState.test {
            viewModel.onComposerTextChanged("hello")
            viewModel.sendMessage()
            val afterAttempt = awaitUntil { it.errorMessage != null }
            assertEquals("You're offline. Reconnect to send messages.", afterAttempt.errorMessage)
            assertTrue(afterAttempt.messages.isEmpty()) // no optimistic/fake-sent message was added
            assertTrue(!afterAttempt.isSending)
            assertTrue(!afterAttempt.isStreaming)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a completed turn refreshes the cache from a fresh network fetch, not the locally-finalized state`() = runTest {
        authRepository = loggedInRepository()
        val serverId = authRepository.activeServerId()!!
        val cache = FakeOfflineCacheRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        // What refreshCacheInBackground() fetches after the turn finishes -- deliberately
        // different content than the streamed reply below, so the assertion can tell the two
        // apart: only this network response should ever land in the cache.
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[{"role":"user","content":"hi"},{"role":"assistant","content":"server-confirmed reply"}]}}""",
            ),
        )

        val scriptedEvents = listOf(SseEvent.Token("streamed reply"), SseEvent.Done(null, null))
        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { scriptedEvents.asFlow() },
            FakeChatPreferencesStore(),
            cache,
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            awaitUntil { !it.isStreaming }
            cancelAndIgnoreRemainingEvents()
        }

        // The background cache refresh runs in its own untracked viewModelScope.launch after
        // finalizeStream, completing only once the second network round trip lands -- wait for it
        // rather than asserting immediately. (loadSession's own initial fetch also opportunistically
        // caches its 0-message snapshot first, so the wait must target the 2-message end state
        // specifically, not just "something is cached".)
        val cached = waitUntilCached(cache, serverId, "s1") { it.messages.orEmpty().size == 2 }
        assertEquals("server-confirmed reply", cached.messages.orEmpty().last().content)
    }

    @Test
    fun `a stream error still only caches the network's confirmed state, never the partial streamed text`() = runTest {
        authRepository = loggedInRepository()
        val serverId = authRepository.activeServerId()!!
        val cache = FakeOfflineCacheRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        // The turn errors out mid-stream with only a partial reply locally, but the background
        // refresh's network fetch reports the session still has no assistant reply at all --
        // proving the cache reflects the network's view, not the partial local one.
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[{"role":"user","content":"hi"}]}}"""))

        val scriptedEvents = listOf(SseEvent.Token("partial reply"), SseEvent.Error("connection reset"))
        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { scriptedEvents.asFlow() },
            FakeChatPreferencesStore(),
            cache,
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            val finalState = awaitUntil { !it.isStreaming }
            assertEquals("partial reply", finalState.messages.last().content) // preserved on screen
            cancelAndIgnoreRemainingEvents()
        }

        val cached = waitUntilCached(cache, serverId, "s1") { it.messages.orEmpty().size == 1 }
        assertTrue(cached.messages.orEmpty().none { it.content == "partial reply" })
    }

    @Test
    fun `pending attachments start empty`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.pendingAttachments.isEmpty())
            assertEquals(false, loaded.isUploadingAttachment)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addUploadedAttachment appends to the pending list`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.addUploadedAttachment(
            UploadResponse(filename = "photo.png", path = "/state/attachments/s1/photo.png", size = 4096, mime = "image/png", isImage = true),
        )

        val pending = viewModel.uiState.value.pendingAttachments
        assertEquals(1, pending.size)
        assertEquals("photo.png", pending.first().name)
        assertEquals("/state/attachments/s1/photo.png", pending.first().path)
        assertEquals("image/png", pending.first().mime)
        assertEquals(4096L, pending.first().size)
        assertEquals(true, pending.first().isImage)
    }

    @Test
    fun `addUploadedAttachment with an error response surfaces the error instead of staging anything`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.addUploadedAttachment(UploadResponse(error = "File too large (max 20MB)"))

        val state = viewModel.uiState.value
        assertTrue(state.pendingAttachments.isEmpty())
        assertEquals("File too large (max 20MB)", state.errorMessage)
    }

    @Test
    fun `removePendingAttachment removes only the matching entry`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.addUploadedAttachment(UploadResponse(filename = "one.png", path = "/x/one.png"))
        viewModel.addUploadedAttachment(UploadResponse(filename = "two.png", path = "/x/two.png"))
        val (first, second) = viewModel.uiState.value.pendingAttachments

        viewModel.removePendingAttachment(first.id)

        val remaining = viewModel.uiState.value.pendingAttachments
        assertEquals(1, remaining.size)
        assertEquals(second.id, remaining.first().id)
    }

    @Test
    fun `removePendingAttachment with an unknown id is a no-op`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.addUploadedAttachment(UploadResponse(filename = "one.png", path = "/x/one.png"))

        viewModel.removePendingAttachment("no-such-id")

        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
    }

    @Test
    fun `send without attachments omits the attachments field and appends no marker`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hello")
            viewModel.sendMessage()
            val immediate = viewModel.uiState.value
            assertTrue(immediate.isSending)
            assertEquals("", immediate.composerText)
            assertEquals("hello", immediate.messages.last().content)
            val afterSend = awaitUntil { it.isStreaming }
            assertEquals("hello", afterSend.messages.last().content)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val chatStartBody = server.takeRequest().body.readUtf8()
        assertTrue(chatStartBody.contains("\"message\":\"hello\""))
        assertTrue(!chatStartBody.contains("attachments"))
    }

    @Test
    fun `send with attachments appends the marker and includes structured attachments`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.addUploadedAttachment(UploadResponse(filename = "one.png", path = "/x/one.png", mime = "image/png", isImage = true))
        viewModel.addUploadedAttachment(UploadResponse(filename = "two.pdf", path = "/x/two.pdf", mime = "application/pdf", isImage = false))

        viewModel.uiState.test {
            viewModel.onComposerTextChanged("check these out")
            viewModel.sendMessage()
            val immediate = viewModel.uiState.value
            assertTrue(immediate.isSending)
            assertEquals("", immediate.composerText)
            assertTrue(immediate.pendingAttachments.isEmpty())
            assertEquals(2, immediate.messages.last().attachments?.size)
            assertEquals("one.png", immediate.messages.last().attachments?.first()?.name)
            assertEquals("image/png", immediate.messages.last().attachments?.first()?.mime)
            val afterSend = awaitUntil { it.isStreaming }
            assertEquals(
                "check these out\n\n[Attached files: /x/one.png, /x/two.pdf]",
                afterSend.messages.last().content,
            )
            assertEquals(listOf("one.png", "two.pdf"), afterSend.messages.last().attachments?.map { it.name })
            assertTrue(afterSend.pendingAttachments.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val chatStartBody = server.takeRequest().body.readUtf8()
        assertTrue(chatStartBody.contains("\\n\\n[Attached files: /x/one.png, /x/two.pdf]"))
        assertTrue(chatStartBody.contains("\"attachments\":[{"))
        assertTrue(chatStartBody.contains("\"path\":\"/x/one.png\""))
        assertTrue(chatStartBody.contains("\"is_image\":true"))
        assertTrue(chatStartBody.contains("\"path\":\"/x/two.pdf\""))
        assertTrue(chatStartBody.contains("\"is_image\":false"))
    }

    @Test
    fun `send caps outgoing attachments at 20, matching the server's own cap`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        repeat(25) { index ->
            viewModel.addUploadedAttachment(UploadResponse(filename = "file-$index.txt", path = "/x/file-$index.txt"))
        }
        assertEquals(25, viewModel.uiState.value.pendingAttachments.size)

        viewModel.uiState.test {
            viewModel.onComposerTextChanged("many files")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming }
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val chatStartBody = server.takeRequest().body.readUtf8()
        assertTrue(chatStartBody.contains("file-19.txt")) // the 20th (index 19) is the last one included
        assertTrue(!chatStartBody.contains("file-20.txt")) // the 21st is dropped, both from the marker and the array
    }

    @Test
    fun `a failed send preserves pending attachments and composer text`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setResponseCode(500)) // /api/chat/start fails
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.addUploadedAttachment(UploadResponse(filename = "one.png", path = "/x/one.png"))

        viewModel.uiState.test {
            viewModel.onComposerTextChanged("check this out")
            viewModel.sendMessage()
            val immediate = viewModel.uiState.value
            assertTrue(immediate.isSending)
            assertEquals("", immediate.composerText)
            assertTrue(immediate.pendingAttachments.isEmpty())
            assertEquals(1, immediate.messages.size)
            val afterFailure = awaitUntil { it.errorMessage != null }
            assertEquals(1, afterFailure.pendingAttachments.size)
            assertEquals("check this out", afterFailure.composerText)
            assertEquals(false, afterFailure.isSending)
            assertTrue(afterFailure.messages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `performAttachmentUpload success adds a pending attachment with the returned metadata`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"filename":"notes.txt","path":"/state/attachments/s1/notes.txt","size":11,"mime":"text/plain","is_image":false}""",
            ),
        )
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "notes.txt", bytes = "hello world".toByteArray(), mime = "text/plain"))

        val pending = viewModel.uiState.value.pendingAttachments
        assertEquals(1, pending.size)
        assertEquals("notes.txt", pending.first().name)
        assertEquals("/state/attachments/s1/notes.txt", pending.first().path)
        assertEquals(11L, pending.first().size)
        assertEquals(false, viewModel.uiState.value.isUploadingAttachment)
    }

    @Test
    fun `performAttachmentUpload sends session_id and the file field via multipart`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"filename":"notes.txt","path":"/state/attachments/s1/notes.txt","size":11,"mime":"text/plain","is_image":false}""",
            ),
        )
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "notes.txt", bytes = "hello world".toByteArray(), mime = "text/plain"))

        server.takeRequest() // session
        server.takeRequest() // profiles
        val uploadRequest = server.takeRequest()
        assertEquals("/api/upload", uploadRequest.path)
        val body = uploadRequest.body.readUtf8()
        assertTrue(body.contains("name=\"session_id\""))
        assertTrue(body.contains("s1"))
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"notes.txt\""))
    }

    @Test
    fun `performAttachmentUpload with a server error response surfaces the error and adds nothing`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"error":"File too large (max 20MB)"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "huge.bin", bytes = ByteArray(10), mime = null))

        val state = viewModel.uiState.value
        assertTrue(state.pendingAttachments.isEmpty())
        assertEquals("File too large (max 20MB)", state.errorMessage)
        assertEquals(false, state.isUploadingAttachment)
    }

    @Test
    fun `a network upload failure preserves already-pending attachments`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"filename":"one.txt","path":"/x/one.txt"}""")) // first upload succeeds
        server.enqueue(MockResponse().setResponseCode(500)) // second upload fails
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "one.txt", bytes = byteArrayOf(1), mime = "text/plain"))
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)

        viewModel.performAttachmentUpload(AttachmentFile(name = "two.txt", bytes = byteArrayOf(2), mime = "text/plain"))

        val state = viewModel.uiState.value
        assertEquals(1, state.pendingAttachments.size)
        assertEquals("one.txt", state.pendingAttachments.first().name)
        assertTrue(state.errorMessage != null)
    }

    @Test
    fun `removePendingAttachment works after a real performAttachmentUpload call`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"filename":"one.txt","path":"/x/one.txt"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "one.txt", bytes = byteArrayOf(1), mime = "text/plain"))
        val uploaded = viewModel.uiState.value.pendingAttachments.single()

        viewModel.removePendingAttachment(uploaded.id)

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
    }

    @Test
    fun `isUploadingAttachment is true while the upload is in flight, false once it settles`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"filename":"one.txt","path":"/x/one.txt"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            launch { viewModel.performAttachmentUpload(AttachmentFile(name = "one.txt", bytes = byteArrayOf(1), mime = "text/plain")) }

            val uploading = awaitUntil { it.isUploadingAttachment }
            assertTrue(uploading.isUploadingAttachment)
            val settled = awaitUntil { !it.isUploadingAttachment }
            assertEquals(1, settled.pendingAttachments.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sending after a real performAttachmentUpload appends the marker and includes structured attachments`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"filename":"one.png","path":"/x/one.png","mime":"image/png","is_image":true}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "one.png", bytes = byteArrayOf(1, 2, 3), mime = "image/png"))
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)

        viewModel.uiState.test {
            viewModel.onComposerTextChanged("look at this")
            viewModel.sendMessage()
            val afterSend = awaitUntil { it.isStreaming }
            assertEquals("look at this\n\n[Attached files: /x/one.png]", afterSend.messages.last().content)
            assertTrue(afterSend.pendingAttachments.isEmpty()) // successful send clears pending attachments
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        server.takeRequest() // upload
        val chatStartBody = server.takeRequest().body.readUtf8()
        assertTrue(chatStartBody.contains("\\n\\n[Attached files: /x/one.png]"))
        assertTrue(chatStartBody.contains("\"attachments\":[{"))
        assertTrue(chatStartBody.contains("\"path\":\"/x/one.png\""))
    }

    @Test
    fun `a failed send preserves an attachment added via performAttachmentUpload`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"filename":"one.txt","path":"/x/one.txt"}"""))
        server.enqueue(MockResponse().setResponseCode(500)) // /api/chat/start fails
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "one.txt", bytes = byteArrayOf(1), mime = "text/plain"))

        viewModel.uiState.test {
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            val afterFailure = awaitUntil { it.errorMessage != null }
            assertEquals(1, afterFailure.pendingAttachments.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uploadAttachmentsSequentially with empty list is no-op`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.uploadAttachmentsSequentially(emptyList())
            // No state change expected because empty list is a no-op
            assertEquals(0, viewModel.uiState.value.pendingAttachments.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple performAttachmentUpload calls produce pending attachments sequentially`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"filename":"a.txt","path":"/a.txt"}"""))
        server.enqueue(MockResponse().setBody("""{"filename":"b.txt","path":"/b.txt"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        // First upload
        viewModel.performAttachmentUpload(AttachmentFile(name = "a.txt", bytes = byteArrayOf(1), mime = "text/plain"))
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
        assertEquals("a.txt", viewModel.uiState.value.pendingAttachments[0].name)

        // Second upload
        viewModel.performAttachmentUpload(AttachmentFile(name = "b.txt", bytes = byteArrayOf(2), mime = "text/plain"))
        assertEquals(2, viewModel.uiState.value.pendingAttachments.size)
        assertEquals("b.txt", viewModel.uiState.value.pendingAttachments[1].name)
    }

    @Test
    fun `one failed performAttachmentUpload does not corrupt state for subsequent uploads`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        // First upload fails (500)
        server.enqueue(MockResponse().setResponseCode(500))
        // Second upload succeeds
        server.enqueue(MockResponse().setBody("""{"filename":"good.txt","path":"/good.txt"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        // Failed upload
        viewModel.performAttachmentUpload(AttachmentFile(name = "fail.txt", bytes = byteArrayOf(1), mime = "text/plain"))
        // Error state should be set but pending attachments remain empty (failure, not added)
        assertNull(viewModel.uiState.value.pendingAttachments.firstOrNull()?.name)

        // Successful upload after failure
        viewModel.performAttachmentUpload(AttachmentFile(name = "good.txt", bytes = byteArrayOf(2), mime = "text/plain"))
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
        assertEquals("good.txt", viewModel.uiState.value.pendingAttachments[0].name)
    }

    @Test
    fun `no auto-send after performAttachmentUpload`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"filename":"f.txt","path":"/f.txt"}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.performAttachmentUpload(AttachmentFile(name = "f.txt", bytes = byteArrayOf(1), mime = "text/plain"))
        // Attachment is pending, not sent
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
        assertFalse(viewModel.uiState.value.isSending)
        assertFalse(viewModel.uiState.value.isStreaming)
    }

    // ── editMessage / regenerate ──

    @Test
    fun `editMessage truncates the session at that message and prefills the composer with its original text`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[
                    {"role":"user","content":"first"},
                    {"role":"assistant","content":"reply one"},
                    {"role":"user","content":"second"},
                    {"role":"assistant","content":"reply two"}
                ]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1"}}""")) // truncate response

        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(4, loaded.messages.size)

            // Editing the *second* user turn (index 2) must drop it and everything after it --
            // not just the tail end -- so Send re-adds it (with whatever new text) in its place
            // instead of appending a duplicate after "reply two".
            viewModel.editMessage(2)
            val edited = awaitUntil { it.composerText == "second" }
            assertEquals(2, edited.messages.size)
            assertEquals("first", edited.messages[0].content)

            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val truncateRequest = server.takeRequest()
        assertTrue(truncateRequest.path?.contains("/api/session/truncate") == true)
        val body = truncateRequest.body.readUtf8()
        assertTrue(body.contains("\"keep_count\":2"))
    }

    @Test
    fun `editMessage on a message that no longer exists is a no-op`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { emptyList<SseEvent>().asFlow() }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.editMessage(0)
        assertEquals("", viewModel.uiState.value.composerText)
        server.takeRequest() // session
        server.takeRequest() // profiles
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `regenerate truncates the last assistant message and resends the prior user turn`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"session":{"session_id":"s1","messages":[
                    {"role":"user","content":"question"},
                    {"role":"assistant","content":"bad answer"}
                ]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1"}}""")) // truncate response
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}""")) // chat/start

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { emptyList<SseEvent>().asFlow() },
            FakeChatPreferencesStore(),
        )

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.messages.size)

            viewModel.regenerate()
            awaitUntil { it.isSending || it.isStreaming }

            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // session
        server.takeRequest() // profiles
        val truncateRequest = server.takeRequest()
        assertTrue(truncateRequest.path?.contains("/api/session/truncate") == true)
        assertTrue(truncateRequest.body.readUtf8().contains("\"keep_count\":1"))
        val chatStartRequest = server.takeRequest()
        assertTrue(chatStartRequest.path?.contains("/api/chat/start") == true)
        assertTrue(chatStartRequest.body.readUtf8().contains("question"))
    }

    // ---- Issue #10 regression: stream-lifecycle ordering and 409 recovery ----

    @Test
    fun `send then stop then edit then resend does not race the cancel and hit 409`() = runTest {
        // Reproduces Issue #10: after tapping Stop mid-stream, the immediate Edit + Resend
        // (truncate + chat/start) was racing the in-flight /api/chat/cancel POST, causing the
        // server to return 409 "session already has an active stream" and the resend to be
        // lost. After the fix, editMessage must wait for the cancel to complete before
        // truncating.
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        // chat/cancel will be served with a real (non-failure) response, but we deliberately
        // delay it so the test can prove that editMessage() awaits it before truncating.
        server.enqueue(
            MockResponse().setBodyDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody("""{"ok":true,"cancelled":true}"""),
        )
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1"}}""")) // truncate
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-2","session_id":"s1"}""")) // chat/start
        // cancelStream() -> finalizeStream() fires a background cache refresh (GET /api/session).
        // Queue a few extras so the takeRequest() drain below doesn't hang.
        repeat(3) {
            server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        }

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("still typing"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("first send")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "still typing" }

            // User taps Stop, then immediately taps Edit on the user message at index 0.
            viewModel.cancelStream()
            viewModel.editMessage(0)

            // Wait for the full edit lifecycle: composer pre-filled, no longer streaming, no
            // longer stopping (means the cancel POST has landed and editMessage has moved on to
            // the truncate + resend path). This guarantees all 6+ requests below are observable
            // on the MockWebServer when we drain it after the test block.
            val afterEdit = awaitUntil { it.composerText == "first send" && !it.isStreaming && !it.isStopping }
            assertEquals(false, afterEdit.isStreaming)

            cancelAndIgnoreRemainingEvents()
        }

        // Verify the critical ordering: the cancel POST must be sent before the truncate.
        // (editMessage pre-fills the composer but does not auto-send; the actual resend
        // happens on a user Send tap. So we only assert cancel-then-truncate ordering here --
        // the resend lifecycle is covered by the other tests in this file.)
        var sawCancel = false
        var sawTruncateAfterCancel = false
        // Bound the scan: takeRequest() blocks by default when the queue empties, so pass
        // timeout=0 to make it return null immediately if no request is available.
        repeat(server.requestCount + 1) {
            val req = server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) ?: return@repeat
            val path = req.path ?: return@repeat
            when {
                path.contains("/api/chat/cancel") -> { sawCancel = true }
                path.contains("/api/session/truncate") && sawCancel -> { sawTruncateAfterCancel = true }
            }
        }
        assertTrue("expected /api/chat/cancel to be sent", sawCancel)
        assertTrue("expected /api/session/truncate AFTER the cancel (Issue #10)", sawTruncateAfterCancel)
    }

    @Test
    fun `send then stop then regenerate awaits cancel before truncating`() = runTest {
        // Companion to the edit test: regenerate() also calls truncateSession, so it must
        // cancel + await the in-flight stream first.
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[{"role":"user","content":"q"},{"role":"assistant","content":"a"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(
            MockResponse().setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody("""{"ok":true,"cancelled":true}"""),
        )
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1"}}""")) // truncate
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-2","session_id":"s1"}""")) // chat/start
        repeat(3) {
            server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1"}}"""))
        }

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("partial"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("q")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "partial" }

            viewModel.cancelStream()
            viewModel.regenerate()

            // Wait for the full regenerate lifecycle: composer pre-filled with "q", no longer
            // streaming, and isStopping has cleared (cancel POST landed) so the truncate +
            // chat/start below are observable on the server.
            awaitUntil { it.composerText == "q" && !it.isStreaming && !it.isStopping }
            cancelAndIgnoreRemainingEvents()
        }

        // Same scan-based ordering check as the edit test.
        var sawCancel = false
        var sawTruncateAfterCancel = false
        repeat(server.requestCount + 1) {
            val req = server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) ?: return@repeat
            val path = req.path ?: return@repeat
            when {
                path.contains("/api/chat/cancel") -> { sawCancel = true }
                path.contains("/api/session/truncate") && sawCancel -> { sawTruncateAfterCancel = true }
            }
        }
        assertTrue("expected /api/chat/cancel to be sent", sawCancel)
        assertTrue("expected /api/session/truncate AFTER the cancel (Issue #10)", sawTruncateAfterCancel)
    }

    @Test
    fun `stop while tokens are actively streaming fires cancel and finalizes locally`() = runTest {
        // Sanity check: the basic "stop during streaming" path still works after the lifecycle
        // refactor -- cancel POST is sent, stream local state is cleared, no IOException
        // surfaces as a user-facing error.
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"cancelled":true}""")) // cancel

        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("a"))
            emit(SseEvent.Token("b"))
            emit(SseEvent.Token("c"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "abc" }

            viewModel.cancelStream()
            val afterCancel = awaitUntil { !it.isStreaming }
            assertEquals("", afterCancel.streamingText)
            // No "software connection abort" should have surfaced -- the previous Issue #10 PR
            // made the SseClient silently drop IOExceptions on cancelled coroutines.
            assertNull(afterCancel.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chat start 409 with active_stream_id waits for release and retries once`() = runTest {
        // Even if the cancel-await isn't enough (e.g. the server hasn't yet processed the
        // cancel), chat/start returning 409 with "session already has an active stream" should
        // trigger a brief wait-and-retry that succeeds. Two consecutive 409s would surface a
        // user-facing message.
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        // First chat/start: 409 with active_stream_id
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"error":"session already has an active stream","active_stream_id":"stale-1"}"""),
        )
        // First poll of /api/session for awaitServerStreamRelease: still has the stale stream
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","active_stream_id":"stale-1"}}"""))
        // Second poll: cleared
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1"}}"""))
        // Second chat/start: success
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))

        val viewModel = ChatViewModel(
            "s1",
            authRepository,
            FakeSseClient { flow { emit(SseEvent.Done(null, null)); awaitCancellation() } },
            FakeChatPreferencesStore(),
        )

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("after conflict")
            viewModel.sendMessage()
            awaitUntil { it.isStreaming || it.errorMessage != null }
            assertNull(viewModel.uiState.value.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chat start 409 userMessage produces a clear stop hint`() = runTest {
        // Unit-test-level guard for the user-facing copy. The full retry/recover timing path
        // is covered by the lifecycle tests above and by the ApiErrorTest translations; this
        // just pins the wire-up so a future refactor of the 409 -> StreamConflict mapping
        // can't silently regress the user message.
        val conflict = ApiError.StreamConflict("stuck-1")
        val http409 = ApiError.Http(409, "session already has an active stream")
        assertTrue("got: ${conflict.userMessage()}", conflict.userMessage().contains("Previous stream is still stopping"))
        assertTrue("got: ${http409.userMessage()}", http409.userMessage().contains("Previous stream is still stopping"))
    }

    @Test
    fun `no user-facing IOException surfaces when a stream is cancelled locally mid-read`() = runTest {
        // Defends the Issue #9 (stream-cancellation) fix: cancelling the SSE reader while the
        // server hasn't finished writing the response must not surface a "Software caused
        // connection abort" error to the user. Combined with this PR's lifecycle ordering, the
        // end state after a stop-then-send cycle is identical to a clean stop with no error.
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"cancelled":true}""")) // cancel

        // The SseClient itself does the OkHttp read; we can't easily inject a delayed body that
        // throws on cancel here without a real server, but we can at least assert that the
        // local finalize path (the one that runs in ChatViewModel.finalizeStream on cancel)
        // leaves errorMessage null. The SseClient-side guarantee is covered by the
        // SseClientTest cancellation tests.
        val stillOpenFlow: Flow<SseEvent> = flow {
            emit(SseEvent.Token("hi"))
            awaitCancellation()
        }
        val viewModel = ChatViewModel("s1", authRepository, FakeSseClient { stillOpenFlow }, FakeChatPreferencesStore())

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onComposerTextChanged("hi")
            viewModel.sendMessage()
            awaitUntil { it.streamingText == "hi" }

            viewModel.cancelStream()
            val afterCancel = awaitUntil { !it.isStreaming }
            assertNull(afterCancel.errorMessage)
            assertEquals(2, afterCancel.messages.size)
            assertEquals("hi", afterCancel.messages[1].content)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
