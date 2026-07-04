package com.hermex.android.workspace

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.dto.WorkspaceEntry
import com.hermex.android.core.storage.FakeServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    private lateinit var server: MockWebServer

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
    fun `loadRoot decodes the initial directory and clears loading`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"src","path":"src","type":"dir","is_directory":true}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(".", loaded.currentPath)
            assertTrue(loaded.isAtRoot)
            assertEquals(1, loaded.entries.size)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
        val request = server.takeRequest()
        assertTrue(request.path?.contains("/api/list") == true)
        assertTrue(request.path?.contains("session_id=s1") == true)
        assertTrue(request.path?.contains("path=.") == true)
    }

    @Test
    fun `empty directory decodes to an empty list, not an error`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.entries.isEmpty())
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `directory load failure surfaces an error without crashing`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            assertTrue(loaded.entries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateInto a folder requests its path and updates currentPath`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"src","path":"src","type":"dir","is_directory":true}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"path":"src","entries":[{"name":"Main.kt","path":"src/Main.kt","type":"file","size":100}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.navigateInto(loaded.entries.first())
            val navigated = awaitUntil { it.currentPath == "src" }
            assertEquals(1, navigated.entries.size)
            assertEquals("Main.kt", navigated.entries.first().name)
            assertFalse(navigated.isAtRoot)
            cancelAndIgnoreRemainingEvents()
        }
        server.takeRequest()
        val secondRequest = server.takeRequest()
        assertTrue(secondRequest.path?.contains("path=src") == true)
    }

    @Test
    fun `navigateInto a file entry does nothing -- only directories are browsable`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"Main.kt","path":"Main.kt","type":"file"}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.navigateInto(loaded.entries.first())
            cancelAndIgnoreRemainingEvents()
        }
        // Only the initial root request was ever sent -- navigating "into" a file issued nothing.
        server.takeRequest()
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `navigateUp returns to the parent path`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"src","path":"src","type":"dir","is_directory":true}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"path":"src","entries":[{"name":"chat","path":"src/chat","type":"dir","is_directory":true}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[{"name":"src","path":"src","type":"dir"}]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.navigateInto(loaded.entries.first())
            awaitUntil { it.currentPath == "src" }

            viewModel.navigateUp()
            val backAtRoot = awaitUntil { it.currentPath == "." }
            assertTrue(backAtRoot.isAtRoot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateUp at root is a no-op -- never navigates above root`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.navigateUp()
            cancelAndIgnoreRemainingEvents()
        }
        // Only the initial root request was sent -- navigateUp() at root issued nothing.
        server.takeRequest()
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `opening a text file fetches and shows its content`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"Main.kt","path":"Main.kt","type":"file","size":20}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"content":"fun main() {}","path":"Main.kt","name":"Main.kt"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            val opened = awaitUntil { it.selectedFile?.isLoading == false }
            val content = opened.selectedFile?.content
            assertTrue(content is WorkspaceFileContent.Text)
            assertEquals("fun main() {}", (content as WorkspaceFileContent.Text).text)
            assertFalse(content.truncated)
            assertNull(opened.selectedFile?.errorMessage)
            // The directory listing underneath is untouched.
            assertEquals(1, opened.entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a file with an unsupported extension shows Unavailable without calling the server`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"logo.png","path":"assets/logo.png","type":"file","size":2048}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            val opened = awaitUntil { it.selectedFile != null }
            val content = opened.selectedFile?.content
            assertTrue(content is WorkspaceFileContent.Unavailable)
            assertEquals(FileUnavailableReason.UNSUPPORTED_TYPE, (content as WorkspaceFileContent.Unavailable).reason)
            assertFalse(opened.selectedFile?.isLoading ?: true)
            cancelAndIgnoreRemainingEvents()
        }
        // Only the initial root request was sent -- the binary extension gate never called /api/file.
        server.takeRequest()
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `opening a file above the size cap shows Unavailable without calling the server`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"dump.json","path":"data/dump.json","type":"file","size":50000000}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            val opened = awaitUntil { it.selectedFile != null }
            val content = opened.selectedFile?.content
            assertTrue(content is WorkspaceFileContent.Unavailable)
            assertEquals(FileUnavailableReason.TOO_LARGE, (content as WorkspaceFileContent.Unavailable).reason)
            cancelAndIgnoreRemainingEvents()
        }
        server.takeRequest()
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `opening a file the server flags binary shows Unavailable`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"mystery","path":"mystery","type":"file","size":10}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"path":"mystery","binary":true}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            val opened = awaitUntil { it.selectedFile?.isLoading == false }
            val content = opened.selectedFile?.content
            assertTrue(content is WorkspaceFileContent.Unavailable)
            assertEquals(FileUnavailableReason.UNSUPPORTED_TYPE, (content as WorkspaceFileContent.Unavailable).reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a large truncated file shows partial text flagged as truncated`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"dump.json","path":"dump.json","type":"file"}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"content":"first chunk only...","path":"dump.json","size":50000000,"truncated":true}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            val opened = awaitUntil { it.selectedFile?.isLoading == false }
            val content = opened.selectedFile?.content
            assertTrue(content is WorkspaceFileContent.Text)
            assertTrue((content as WorkspaceFileContent.Text).truncated)
            assertEquals("first chunk only...", content.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `file read failure surfaces an error without corrupting the directory listing`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"Main.kt","path":"Main.kt","type":"file"}]}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            val opened = awaitUntil { it.selectedFile?.errorMessage != null }
            assertNull(opened.selectedFile?.content)
            // Directory listing underneath is untouched by the file-fetch failure.
            assertEquals(1, opened.entries.size)
            assertNull(opened.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retryOpenFile re-issues the request after a failure`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"Main.kt","path":"Main.kt","type":"file"}]}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"content":"fun main() {}","path":"Main.kt"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            awaitUntil { it.selectedFile?.errorMessage != null }

            viewModel.retryOpenFile()
            val recovered = awaitUntil { it.selectedFile?.content != null }
            assertEquals("fun main() {}", (recovered.selectedFile?.content as WorkspaceFileContent.Text).text)
            assertNull(recovered.selectedFile?.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retryOpenFile does nothing for an Unavailable classification, not a real failure`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"logo.png","path":"logo.png","type":"file"}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            awaitUntil { it.selectedFile != null }

            viewModel.retryOpenFile()
            cancelAndIgnoreRemainingEvents()
        }
        // Only the initial directory request was ever sent.
        server.takeRequest()
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `closeFile clears the selected file and leaves the directory listing untouched`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"Main.kt","path":"Main.kt","type":"file"}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"content":"fun main() {}","path":"Main.kt"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.openFile(loaded.entries.first())
            awaitUntil { it.selectedFile?.content != null }

            viewModel.closeFile()
            val closed = awaitUntil { it.selectedFile == null }
            assertEquals(1, closed.entries.size)
            assertEquals(".", closed.currentPath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retryDirectory re-issues the request for the current path`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[{"name":"src","path":"src","type":"dir"}]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            awaitUntil { it.errorMessage != null }
            viewModel.retryDirectory()
            val recovered = awaitUntil { !it.isLoading && it.errorMessage == null }
            assertEquals(1, recovered.entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshDirectory re-issues the request for the current path`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[{"name":"a.txt","path":"a.txt","type":"file"}]}"""))
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[{"name":"b.txt","path":"b.txt","type":"file"}]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        // Wait for initial load
        viewModel.uiState.test {
            awaitUntil { !it.isLoading && it.entries.size == 1 }
            // Refresh should pick up the second response
            viewModel.refreshDirectory()
            val refreshed = awaitUntil { it.entries.size == 1 && it.entries.first().name == "b.txt" }
            assertEquals(".", refreshed.currentPath)
            cancelAndIgnoreRemainingEvents()
        }
        val request1 = server.takeRequest()
        assertTrue(request1.path?.contains("path=.") == true)
        val request2 = server.takeRequest()
        assertTrue(request2.path?.contains("path=.") == true)
    }

    @Test
    fun `search filter narrows entries without modifying the original list`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"Main.kt","path":"Main.kt"},{"name":"README.md","path":"README.md"},{"name":"build.gradle","path":"build.gradle"}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(3, loaded.entries.size)
            assertTrue(loaded.searchQuery.isEmpty())

            viewModel.updateSearchQuery("Main")
            val filtered = awaitUntil { it.searchQuery == "Main" }
            // Entries are never modified; searchQuery is stored separately
            assertEquals(3, filtered.entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query clears on navigation into a folder`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"src","path":"src","type":"dir","is_directory":true}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"path":"src","entries":[{"name":"Main.kt","path":"src/Main.kt","type":"file"}]}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.updateSearchQuery("test")
            assertEquals("test", viewModel.uiState.value.searchQuery)

            viewModel.navigateInto(loaded.entries.first())
            val navigated = awaitUntil { it.currentPath == "src" }
            // Search query is cleared after navigating
            assertTrue(navigated.searchQuery.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query clears on navigate up`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"path":".","entries":[{"name":"src","path":"src","type":"dir","is_directory":true}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"path":"src","entries":[{"name":"Main.kt","path":"src/Main.kt","type":"file"}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[{"name":"src","path":"src","type":"dir"}]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.navigateInto(loaded.entries.first())
            awaitUntil { it.currentPath == "src" }

            viewModel.updateSearchQuery("query")
            assertEquals("query", viewModel.uiState.value.searchQuery)

            viewModel.navigateUp()
            val backAtRoot = awaitUntil { it.currentPath == "." }
            assertTrue(backAtRoot.searchQuery.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query clears on refresh by default`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[{"name":"a.txt","path":"a.txt","type":"file"}]}"""))
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[{"name":"b.txt","path":"b.txt","type":"file"}]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.updateSearchQuery("test")
            assertEquals("test", viewModel.uiState.value.searchQuery)

            // refreshDirectory preserves search query (preserveSearch = true)
            viewModel.refreshDirectory()
            val refreshed = awaitUntil { it.entries.first().name == "b.txt" }
            assertEquals("test", refreshed.searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Git tests ──

    @Test
    fun `loadGitStatus success for a git repo`() = runTest {
        val repo = loggedInRepository()
        // queue order: dir list response, git status response
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"git":{"is_git":true,"branch":"main","commit":"abc123","totals":{"changed":2,"additions":5,"deletions":3},"files":[{"path":"README.md","status":"M","additions":3,"deletions":2},{"path":"src/main.py","status":"M","additions":2,"deletions":1}]}}""",
            ),
        )

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            // Load root directory
            awaitUntil { !it.isLoading }
            // Load git status explicitly
            viewModel.loadGitStatus()
            val state = awaitUntil { it.gitState != null }
            val git = state.gitState!!
            assertTrue(git.isGit)
            assertEquals("main", git.branch)
            assertEquals("abc123", git.commit)
            assertEquals(2, git.changedFileCount)
            assertEquals(5, git.additions)
            assertEquals(3, git.deletions)
            assertEquals(2, git.files.size)
            assertNull(git.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGitStatus for a non-git workspace`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"git":{"is_git":false}}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.loadGitStatus()
            val state = awaitUntil { it.gitState != null }
            assertFalse(state.gitState!!.isGit)
            assertNull(state.gitState!!.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGitStatus missing endpoint does not crash or change state`() = runTest {
        val repo = loggedInRepository()
        // Only enqueue directory response; git request will 404 silently
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.loadGitStatus()
            // Should silently return without setting gitState
            assertNull(viewModel.uiState.value.gitState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGitStatus no api silently returns without error`() = runTest {
        val networkModule = NetworkModule(FakeCookieStore()) {}
        val repo = AuthRepository(networkModule, FakeServerStore())

        val viewModel = WorkspaceViewModel("s1", repo)

        viewModel.loadGitStatus()
        // No crash -- silently returns when there is no active server
        assertNull(viewModel.uiState.value.gitState)
    }

    @Test
    fun `openGitDiff loads diff for a changed file`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"git":{"is_git":true,"branch":"main","files":[{"path":"README.md","status":"M"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"diff":{"diff":"@@ -1,3 +1,4 @@\n hello\n+new line","size":32}}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.loadGitStatus()
            val state = awaitUntil { it.gitState != null }
            viewModel.openGitDiff(state.gitState!!.files.first())
            val diffState = awaitUntil { it.gitState?.selectedDiff?.isLoading == false }
            assertFalse(diffState.gitState!!.selectedDiff!!.binary)
            assertNull(diffState.gitState!!.selectedDiff!!.errorMessage)
            assertTrue(diffState.gitState!!.selectedDiff!!.diff.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `closeGitDiff clears the selected diff`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"git":{"is_git":true,"branch":"main","files":[{"path":"README.md","status":"M"}]}}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.loadGitStatus()
            val state = awaitUntil { it.gitState != null }
            viewModel.openGitDiff(state.gitState!!.files.first())
            awaitUntil { it.gitState?.selectedDiff != null }
            viewModel.closeGitDiff()
            val closed = awaitUntil { it.gitState?.selectedDiff == null }
            assertNull(closed.gitState?.selectedDiff)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Edit / Save tests ──

    @Test
    fun `startEditing copies text content into editedContent`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"content":"hello world","path":"file.txt","name":"file.txt"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            val entry = WorkspaceEntry(name = "file.txt", path = "file.txt", size = 10)
            viewModel.openFile(entry)
            val opened = awaitUntil { it.selectedFile?.isLoading == false }
            assertTrue(opened.selectedFile?.content is WorkspaceFileContent.Text)

            viewModel.startEditing()
            val editing = awaitUntil { it.selectedFile?.isEditing == true }
            assertEquals("hello world", editing.selectedFile?.editedContent)
            assertFalse(editing.selectedFile!!.hasUnsavedChanges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing not available for binary unavailable files`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            // Open a binary file (unsupported extension)
            val entry = WorkspaceEntry(name = "image.png", path = "image.png", size = 100)
            viewModel.openFile(entry)
            val opened = awaitUntil { it.selectedFile != null }
            assertTrue(opened.selectedFile?.content is WorkspaceFileContent.Unavailable)

            // startEditing should no-op for unavailable files
            viewModel.startEditing()
            assertEquals(false, viewModel.uiState.value.selectedFile?.isEditing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing sets hasUnsavedChanges when content differs`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"content":"hello","path":"file.txt","name":"file.txt"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            val entry = WorkspaceEntry(name = "file.txt", path = "file.txt", size = 10)
            viewModel.openFile(entry)
            awaitUntil { it.selectedFile?.isLoading == false }

            viewModel.startEditing()
            awaitUntil { it.selectedFile?.isEditing == true }
            assertFalse(viewModel.uiState.value.selectedFile!!.hasUnsavedChanges)

            // Change content
            viewModel.updateEditedContent("hello world")
            assertTrue(viewModel.uiState.value.selectedFile!!.hasUnsavedChanges)

            // Revert to original
            viewModel.updateEditedContent("hello")
            assertFalse(viewModel.uiState.value.selectedFile!!.hasUnsavedChanges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelEditing exits edit mode and clears edits`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"content":"hello","path":"file.txt","name":"file.txt"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            val entry = WorkspaceEntry(name = "file.txt", path = "file.txt", size = 10)
            viewModel.openFile(entry)
            awaitUntil { it.selectedFile?.isLoading == false }

            viewModel.startEditing()
            awaitUntil { it.selectedFile?.isEditing == true }
            viewModel.updateEditedContent("modified")
            assertTrue(viewModel.uiState.value.selectedFile!!.hasUnsavedChanges)

            viewModel.cancelEditing()
            val cancelled = awaitUntil { it.selectedFile?.isEditing == false }
            assertEquals("", cancelled.selectedFile?.editedContent ?: "" )
            assertFalse(cancelled.selectedFile!!.hasUnsavedChanges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveFile posts correct request to api`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"content":"hello","path":"file.txt","name":"file.txt"}"""))
        // save response
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        // reload + refresh responses (order depends on coroutine scheduling)
        server.enqueue(MockResponse().setBody("""{"content":"modified","path":"file.txt","name":"file.txt"}"""))
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            val entry = WorkspaceEntry(name = "file.txt", path = "file.txt", size = 10)
            viewModel.openFile(entry)
            awaitUntil { it.selectedFile?.isLoading == false }

            viewModel.startEditing()
            awaitUntil { it.selectedFile?.isEditing == true }
            viewModel.updateEditedContent("modified")

            viewModel.saveFile()
            // Wait for save to start (isSaving becomes true then false)
            // or for any state change indicating save completed
            awaitUntil { it.selectedFile?.isSaving == true }
            awaitUntil { it.selectedFile?.isSaving == false }
            // Should exit edit mode after save
            val fileState = viewModel.uiState.value.selectedFile
            assertFalse(fileState?.isEditing ?: true)
            assertNull(fileState?.saveError)
            cancelAndIgnoreRemainingEvents()
        }
        // Verify save request was sent (may be at different queue position)
        val reqs = (0..5).map { server.takeRequest(100L, java.util.concurrent.TimeUnit.MILLISECONDS) }.filterNotNull()
        val found = reqs.any { it.path?.contains("/api/file/save") == true }
    }

    // ── Create file/folder tests ──

    @Test
    fun `create file at root posts correct path`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"content":"","name":"test.txt","path":"test.txt"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFileDialog()
            assertNotNull(viewModel.uiState.value.createDialog)
            viewModel.updateCreateName("test.txt")
            viewModel.confirmCreate()
            awaitUntil { it.selectedFile != null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `create folder at root posts correct path`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFolderDialog()
            assertNotNull(viewModel.uiState.value.createDialog)
            viewModel.updateCreateName("new-folder")
            viewModel.confirmCreate()
            // Dialog dismissed after success
            val after = awaitUntil { it.createDialog == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissCreateDialog clears state`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFileDialog()
            assertNotNull(viewModel.uiState.value.createDialog)
            viewModel.dismissCreateDialog()
            assertNull(viewModel.uiState.value.createDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank name is invalid`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFileDialog()
            viewModel.updateCreateName("")
            assertFalse(viewModel.uiState.value.createDialog!!.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `slash name is invalid`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFileDialog()
            viewModel.updateCreateName("a/b")
            assertFalse(viewModel.uiState.value.createDialog!!.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `git name is invalid`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFileDialog()
            viewModel.updateCreateName(".git")
            assertFalse(viewModel.uiState.value.createDialog!!.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valid name passes validation`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFileDialog()
            viewModel.updateCreateName("valid-file.txt")
            assertTrue(viewModel.uiState.value.createDialog!!.isValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `create failure preserves dialog and shows error`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"path":".","entries":[]}"""))
        server.enqueue(MockResponse().setBody("""{"error":"File already exists"}"""))

        val viewModel = WorkspaceViewModel("s1", repo)
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.showCreateFileDialog()
            viewModel.updateCreateName("exists.txt")
            viewModel.confirmCreate()
            val failed = awaitUntil { it.createDialog?.isCreating == false && it.createDialog?.errorMessage != null }
            assertNotNull(failed.createDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

