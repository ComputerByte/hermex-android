package com.hermex.android.chat

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.cache.FakeOfflineCacheRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.SseEvent
import com.hermex.android.core.network.SseStreamSource
import com.hermex.android.core.network.ToolEventPayload
import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.ModelCatalogOption
import com.hermex.android.core.network.dto.SessionDetail
import com.hermex.android.core.storage.ChatPreferencesStore
import com.hermex.android.core.storage.FakeServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
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
}
