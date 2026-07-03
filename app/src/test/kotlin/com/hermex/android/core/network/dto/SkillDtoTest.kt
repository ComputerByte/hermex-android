package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillDtoTest {
    @Test
    fun `skills list decodes tolerantly with missing fields`() {
        val result = HermexJson.decodeFromString<SkillsResponse>(
            """{"skills":[{"name":"web-search"},{"name":"code-review","category":"dev","description":"Reviews code"}]}""",
        )
        assertEquals(2, result.skills?.size)
        assertEquals("web-search", result.skills?.get(0)?.name)
        assertNull(result.skills?.get(0)?.category)
        assertEquals("dev", result.skills?.get(1)?.category)
    }

    @Test
    fun `linked_files as a flat name-to-content map uses the keys`() {
        val result = HermexJson.decodeFromString<SkillDetailResponse>(
            """{"name":"x","content":"y","linked_files":{"helper.py":"...","notes.md":"..."}}""",
        )
        assertEquals(listOf("helper.py", "notes.md"), result.linkedFiles)
    }

    @Test
    fun `linked_files as a bare array of filenames is used directly`() {
        val result = HermexJson.decodeFromString<SkillDetailResponse>(
            """{"linked_files":["a.py","b.py"]}""",
        )
        assertEquals(listOf("a.py", "b.py"), result.linkedFiles)
    }

    @Test
    fun `linked_files nested under a category object collects names from both shapes`() {
        val result = HermexJson.decodeFromString<SkillDetailResponse>(
            """{"linked_files":{"scripts":["a.py","b.py"],"notes.md":"..."}}""",
        )
        assertEquals(listOf("a.py", "b.py", "notes.md"), result.linkedFiles)
    }

    @Test
    fun `linked_files absent decodes as null, not a crash`() {
        val result = HermexJson.decodeFromString<SkillDetailResponse>("""{"name":"x"}""")
        assertNull(result.linkedFiles)
    }

    @Test
    fun `linked_files as an empty object or array decodes as null rather than an empty list`() {
        assertNull(HermexJson.decodeFromString<SkillDetailResponse>("""{"linked_files":{}}""").linkedFiles)
        assertNull(HermexJson.decodeFromString<SkillDetailResponse>("""{"linked_files":[]}""").linkedFiles)
    }

    @Test
    fun `linked_files with duplicate names across shapes is deduplicated`() {
        val result = HermexJson.decodeFromString<SkillDetailResponse>(
            """{"linked_files":{"scripts":["a.py"],"a.py":"..."}}""",
        )
        assertEquals(listOf("a.py"), result.linkedFiles)
    }

    @Test
    fun `linked_files an unexpected shape like a plain number does not crash`() {
        val result = HermexJson.decodeFromString<SkillDetailResponse>("""{"linked_files":42}""")
        assertNull(result.linkedFiles)
    }
}
