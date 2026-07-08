package com.hermex.android.chat

import org.junit.Assert.*
import org.junit.Test

class CommandRegistryTest {
    @Test
    fun matchCommand_returnsContinue() {
        assertTrue(CommandRegistry.matchCommand("/continue") is CommandAction.Continue)
        assertTrue(CommandRegistry.matchCommand("/continue ") is CommandAction.Continue)
    }

    @Test
    fun matchCommand_returnsSummarize() {
        assertTrue(CommandRegistry.matchCommand("/summarize") is CommandAction.Summarize)
        assertTrue(CommandRegistry.matchCommand("/summarize ") is CommandAction.Summarize)
    }

    @Test
    fun matchCommand_returnsEdit() {
        assertTrue(CommandRegistry.matchCommand("/edit") is CommandAction.Edit)
    }

    @Test
    fun matchCommand_returnsSearch() {
        assertTrue(CommandRegistry.matchCommand("/search") is CommandAction.Search)
    }

    @Test
    fun matchCommand_returnsNullForUnknown() {
        assertNull(CommandRegistry.matchCommand("/unknown"))
        assertNull(CommandRegistry.matchCommand("hello"))
        assertNull(CommandRegistry.matchCommand(""))
    }

    @Test
    fun filter_returnsMatchingCommands() {
        assertTrue(CommandRegistry.filter("/e").any { it.command == "/edit" })
        assertTrue(CommandRegistry.filter("/c").any { it.command == "/continue" })
        assertTrue(CommandRegistry.filter("/s").any { it.command == "/summarize" })
        assertTrue(CommandRegistry.filter("/s").any { it.command == "/search" })
    }

    @Test
    fun filter_returnsEmptyForNonSlash() {
        assertTrue(CommandRegistry.filter("edit").isEmpty())
    }
}