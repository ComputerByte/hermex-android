package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDtoSerializationTest {
    @Test
    fun `directory listing decodes folders and files, tolerating missing optional fields`() {
        val response = HermexJson.decodeFromString<DirectoryListResponse>(
            """{
                "path": "src",
                "workspace": "/home/user/project",
                "entries": [
                    {"name": "chat", "path": "src/chat", "type": "dir", "is_directory": true},
                    {"name": "Main.kt", "path": "src/Main.kt", "type": "file", "size": 4096, "modified": 1719900000.0, "is_directory": false},
                    {"name": "no_metadata"}
                ]
            }""",
        )

        assertEquals("src", response.path)
        assertEquals("/home/user/project", response.workspace)
        assertNull(response.error)
        assertEquals(3, response.entries?.size)

        val folder = response.entries!![0]
        assertEquals("chat", folder.name)
        assertEquals("dir", folder.type)
        assertEquals(true, folder.isDirectory)

        val file = response.entries[1]
        assertEquals("Main.kt", file.name)
        assertEquals(4096L, file.size)
        assertEquals(1719900000.0, file.modified)
        assertEquals(false, file.isDirectory)

        val sparse = response.entries[2]
        assertEquals("no_metadata", sparse.name)
        assertNull(sparse.path)
        assertNull(sparse.type)
        assertNull(sparse.size)
        assertNull(sparse.isDirectory)
    }

    @Test
    fun `empty directory listing decodes to an empty list, not null or a crash`() {
        val response = HermexJson.decodeFromString<DirectoryListResponse>(
            """{"path": ".", "entries": []}""",
        )
        assertEquals(0, response.entries?.size)
    }

    @Test
    fun `directory listing surfaces a server-side error without crashing`() {
        val response = HermexJson.decodeFromString<DirectoryListResponse>(
            """{"error": "path not found"}""",
        )
        assertEquals("path not found", response.error)
        assertNull(response.entries)
    }

    @Test
    fun `directory listing ignores unknown fields`() {
        val response = HermexJson.decodeFromString<DirectoryListResponse>(
            """{"path": ".", "entries": [], "git_status": "clean", "totally_new_field": 42}""",
        )
        assertEquals(".", response.path)
    }

    @Test
    fun `text file decodes content, language, size, and lines`() {
        val response = HermexJson.decodeFromString<FileResponse>(
            """{
                "content": "fun main() {}\n",
                "path": "src/Main.kt",
                "name": "Main.kt",
                "language": "kotlin",
                "size": 15,
                "lines": 1
            }""",
        )
        assertEquals("fun main() {}\n", response.content)
        assertEquals("Main.kt", response.name)
        assertEquals("kotlin", response.language)
        assertEquals(15L, response.size)
        assertEquals(1, response.lines)
        assertNull(response.error)
        assertNull(response.truncated)
        assertNull(response.binary)
    }

    /**
     * ASSUMPTION (undocumented upstream): no verified example of what `/api/file` returns for a
     * binary file was found -- the iOS reference client never calls this endpoint for binary
     * paths at all (it gates by file extension client-side first). This models the most
     * conservative guess -- null content plus a [binary] flag and/or an [FileResponse.error] --
     * purely so decoding a response shaped this way doesn't crash. Confirm against a live server
     * before relying on either field.
     */
    @Test
    fun `binary or unsupported file decodes without content, using whichever signal the server sends`() {
        val flagged = HermexJson.decodeFromString<FileResponse>(
            """{"path": "assets/logo.png", "name": "logo.png", "binary": true, "content": null}""",
        )
        assertNull(flagged.content)
        assertEquals(true, flagged.binary)

        val errored = HermexJson.decodeFromString<FileResponse>(
            """{"path": "assets/logo.png", "error": "Cannot display binary file as text"}""",
        )
        assertNull(errored.content)
        assertEquals("Cannot display binary file as text", errored.error)
    }

    /**
     * ASSUMPTION (undocumented upstream): same caveat as the binary case -- no verified example of
     * a truncated/large-file response. Modeled so a `truncated: true` flag decodes safely
     * alongside partial [FileResponse.content] and a [FileResponse.size] larger than the returned
     * text, without asserting the server actually behaves this way.
     */
    @Test
    fun `large file decodes a truncated flag alongside partial content`() {
        val response = HermexJson.decodeFromString<FileResponse>(
            """{
                "content": "first 1000 chars only...",
                "path": "data/dump.json",
                "size": 50000000,
                "lines": 200000,
                "truncated": true
            }""",
        )
        assertEquals("first 1000 chars only...", response.content)
        assertEquals(50_000_000L, response.size)
        assertEquals(true, response.truncated)
        assertTrue((response.size ?: 0) > response.content!!.length)
    }

    @Test
    fun `file response surfaces a server-side error without crashing`() {
        val response = HermexJson.decodeFromString<FileResponse>(
            """{"error": "path not found"}""",
        )
        assertEquals("path not found", response.error)
        assertNull(response.content)
    }

    @Test
    fun `file response ignores unknown fields`() {
        val response = HermexJson.decodeFromString<FileResponse>(
            """{"content": "x", "encoding": "utf-8", "totally_new_field": 42}""",
        )
        assertEquals("x", response.content)
    }
}
