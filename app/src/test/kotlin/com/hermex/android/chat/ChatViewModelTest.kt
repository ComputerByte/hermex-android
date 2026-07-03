package com.hermex.android.chat

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.SseEvent
import com.hermex.android.core.network.SseStreamSource
import com.hermex.android.core.network.ToolEventPayload
import com.hermex.android.core.storage.ChatPreferencesStore
import com.hermex.android.core.storage.ServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeServerStore(initial: String?) : ServerStore {
    var stored: String? = initial
    override suspend fun save(serverUrl: String) { stored = serverUrl }
    override suspend fun load(): String? = stored
    override suspend fun clear() { stored = null }
}

private class FakeSseClient(private val flowProvider: (HttpUrl) -> Flow<SseEvent>) : SseStreamSource {
    var lastUrl: HttpUrl? = null
    override fun stream(url: HttpUrl): Flow<SseEvent> {
        lastUrl = url
        return flowProvider(url)
    }
}

private class FakeChatPreferencesStore(private var expandThinkingByDefault: Boolean = false) : ChatPreferencesStore {
    override suspend fun loadExpandThinkingByDefault(): Boolean = expandThinkingByDefault
    override suspend fun setExpandThinkingByDefault(value: Boolean) { expandThinkingByDefault = value }
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

    @Test
    fun `tool calls from different turns anchor to their own turn's message index, not the last one`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"session":{"session_id":"s1","messages":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"profiles":[]}""")) // GET /api/profiles

        // Two chat/start calls (one per turn), each opening its own scripted SSE flow via a
        // request-counting FakeSseClient below.
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-1","session_id":"s1"}"""))
        server.enqueue(MockResponse().setBody("""{"stream_id":"stream-2","session_id":"s1"}"""))

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
}
