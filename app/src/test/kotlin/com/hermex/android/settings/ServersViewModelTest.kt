package com.hermex.android.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.auth.AuthState
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.storage.FakeServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repositoryFor(serverStore: FakeServerStore): AuthRepository {
        lateinit var repo: AuthRepository
        val networkModule = NetworkModule(FakeCookieStore()) { repo.handleUnauthorized() }
        repo = AuthRepository(networkModule, serverStore)
        return repo
    }

    @Test
    fun `loads the configured servers and the active server id`() = runTest {
        val store = FakeServerStore("https://a.example.com/")
        val activeId = store.activeServerSnapshot()!!.id
        val viewModel = ServersViewModel(repositoryFor(store), store)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(1, loaded.servers.size)
            assertEquals(activeId, loaded.activeServerId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addServer via the editor persists a new server`() = runTest {
        val store = FakeServerStore()
        val viewModel = ServersViewModel(repositoryFor(store), store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.startAdding()
        viewModel.updateEditorName("Mac Mini")
        viewModel.updateEditorUrl("hermex.local")
        viewModel.saveEditor()

        assertEquals(1, store.state.value.servers.size)
        val added = store.state.value.servers.single()
        assertEquals("Mac Mini", added.name)
        assertEquals("https://hermex.local/", added.baseUrl)
        assertEquals(added.id, store.state.value.activeServerId) // first server auto-activates
    }

    @Test
    fun `an invalid url in the editor surfaces an error and does not persist`() = runTest {
        val store = FakeServerStore()
        val viewModel = ServersViewModel(repositoryFor(store), store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.startAdding()
        viewModel.updateEditorUrl("   ")
        viewModel.saveEditor()

        assertTrue(viewModel.uiState.value.editorError != null)
        assertTrue(store.state.value.servers.isEmpty())
    }

    @Test
    fun `adding a duplicate normalized url is rejected`() = runTest {
        val store = FakeServerStore("https://a.example.com/")
        val viewModel = ServersViewModel(repositoryFor(store), store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.startAdding()
        viewModel.updateEditorUrl("a.example.com")
        viewModel.saveEditor()

        assertEquals("This server is already configured.", viewModel.uiState.value.editorError)
        assertEquals(1, store.state.value.servers.size)
    }

    @Test
    fun `editing a server keeps its id stable`() = runTest {
        val store = FakeServerStore("https://a.example.com/")
        val original = store.activeServerSnapshot()!!
        val viewModel = ServersViewModel(repositoryFor(store), store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.startEditing(original)
        viewModel.updateEditorName("Renamed")
        viewModel.saveEditor()

        val edited = store.state.value.servers.single()
        assertEquals(original.id, edited.id)
        assertEquals("Renamed", edited.name)
    }

    @Test
    fun `switchTo makes another server active`() = runTest {
        val store = FakeServerStore()
        val first = store.addServer("A", "https://a.example.com/")
        val second = store.addServer("B", "https://b.example.com/")
        val repo = repositoryFor(store)
        val viewModel = ServersViewModel(repo, store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.switchTo(second.id)

        assertEquals(second.id, store.state.value.activeServerId)
        assertEquals(second.id, (repo.state.value as AuthState.LoggedIn).serverId)
        assertEquals(first.id, store.state.value.servers.first { it.id != second.id }.id) // A still configured
    }

    @Test
    fun `confirmRemoval forgets the server and clears the pending-removal dialog state`() = runTest {
        val store = FakeServerStore("https://a.example.com/")
        val target = store.activeServerSnapshot()!!
        val viewModel = ServersViewModel(repositoryFor(store), store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.requestRemoval(target)
        assertEquals(target, viewModel.uiState.value.serverPendingRemoval)

        viewModel.confirmRemoval()

        assertNull(viewModel.uiState.value.serverPendingRemoval)
        assertTrue(store.state.value.servers.isEmpty())
    }

    @Test
    fun `cancelRemoval dismisses the dialog without removing anything`() = runTest {
        val store = FakeServerStore("https://a.example.com/")
        val target = store.activeServerSnapshot()!!
        val viewModel = ServersViewModel(repositoryFor(store), store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.requestRemoval(target)
        viewModel.cancelRemoval()

        assertNull(viewModel.uiState.value.serverPendingRemoval)
        assertEquals(1, store.state.value.servers.size)
    }
}
