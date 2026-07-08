package com.hermex.android.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.ui.graphics.vector.ImageVector

/** Actions executed when a slash command is sent (distinct from the suggestion for display). */
sealed class CommandAction {
    /** Continue the last response -- sends prompt to server. */
    data object Continue : CommandAction() { val prompt = "Continue" }
    /** Summarize the conversation -- sends prompt to server. */
    data object Summarize : CommandAction() { val prompt = "Summarize this conversation" }
    /** Edit the last message -- placeholder (UI not implemented). */
    data object Edit : CommandAction()
    /** Search past sessions -- placeholder (UI not implemented). */
    data object Search : CommandAction()
}

data class CommandSuggestion(
    val command: String,
    val description: String,
    val icon: ImageVector,
    val action: CommandAction? = null, // null for commands without direct action
)

object CommandRegistry {
    val commands = listOf(
        CommandSuggestion("/edit", "Edit the last message", Icons.Filled.Edit, CommandAction.Edit),
        CommandSuggestion("/continue", "Continue the last response", Icons.Filled.PlayArrow, CommandAction.Continue),
        CommandSuggestion("/summarize", "Summarize the conversation", Icons.Filled.Summarize, CommandAction.Summarize),
        CommandSuggestion("/search", "Search past sessions", Icons.Filled.Search, CommandAction.Search),
    )

    fun filter(query: String): List<CommandSuggestion> {
        if (!query.startsWith("/")) return emptyList()
        val search = query.substring(1).lowercase()
        return commands.filter { it.command.contains(search, ignoreCase = true) }
    }

    /** Returns the matching CommandAction if the text is a known slash command, null otherwise. */
    fun matchCommand(text: String): CommandAction? {
        val trimmed = text.trim()
        return commands.find { trimmed.startsWith(it.command) }?.action
    }
}
