package com.hermex.android.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.core.storage.CustomHttpHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomHeadersViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the saved headers at init`() = runTest {
        val store = FakeCustomHeadersStore(listOf(CustomHttpHeader(name = "X-Test", value = "abc")))
        val viewModel = CustomHeadersViewModel(store)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(1, loaded.headers.size)
            assertEquals("X-Test", loaded.headers.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addRow appends a blank row`() = runTest {
        val viewModel = CustomHeadersViewModel(FakeCustomHeadersStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.addRow()

        assertEquals(1, viewModel.uiState.value.headers.size)
        assertEquals("", viewModel.uiState.value.headers.first().name)
    }

    @Test
    fun `removeRow removes only the targeted row`() = runTest {
        val store = FakeCustomHeadersStore(
            listOf(CustomHttpHeader(name = "A", value = "1"), CustomHttpHeader(name = "B", value = "2")),
        )
        val viewModel = CustomHeadersViewModel(store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.removeRow(0)

        assertEquals(listOf("B"), viewModel.uiState.value.headers.map { it.name })
    }

    @Test
    fun `updateName and updateValue edit only the targeted row`() = runTest {
        val store = FakeCustomHeadersStore(
            listOf(CustomHttpHeader(name = "A", value = "1"), CustomHttpHeader(name = "B", value = "2")),
        )
        val viewModel = CustomHeadersViewModel(store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.updateName(1, "B-renamed")
        viewModel.updateValue(1, "2-updated")

        assertEquals("A", viewModel.uiState.value.headers[0].name)
        assertEquals("1", viewModel.uiState.value.headers[0].value)
        assertEquals("B-renamed", viewModel.uiState.value.headers[1].name)
        assertEquals("2-updated", viewModel.uiState.value.headers[1].value)
    }

    @Test
    fun `save persists the current draft rows and invokes the callback`() = runTest {
        val store = FakeCustomHeadersStore()
        val viewModel = CustomHeadersViewModel(store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.addRow()
        viewModel.updateName(0, "X-New")
        viewModel.updateValue(0, "new-value")

        // FakeCustomHeadersStore.save() has no real suspension point, so under
        // UnconfinedTestDispatcher the whole save() coroutine runs to completion in one go --
        // there's no reliably observable moment where isSaving is true, so just assert on the
        // settled end state rather than chasing an intermediate flag a fake can't produce.
        var saved = false
        viewModel.save { saved = true }

        assertTrue(saved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(listOf("X-New"), store.snapshot().map { it.name })
    }

    @Test
    fun `a blank-name row is dropped from the store on save, matching sanitizedForStorage`() = runTest {
        val store = FakeCustomHeadersStore()
        val viewModel = CustomHeadersViewModel(store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.addRow() // stays blank -- never named

        viewModel.save {}

        assertTrue(store.snapshot().isEmpty())
    }
}
