package com.hermex.android.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.ui.graphics.vector.ImageVector

data class CommandSuggestion(
    val command: String,
    val description: String,
    val icon: ImageVector,
)

object CommandRegistry {
    val commands = listOf(
        CommandSuggestion("/edit", "Edit the last message", Icons.Filled.Edit),
        CommandSuggestion("/continue", "Continue the last response", Icons.Filled.PlayArrow),
        CommandSuggestion("/summarize", "Summarize the conversation", Icons.Filled.Summarize),
        CommandSuggestion("/search", "Search past sessions", Icons.Filled.Search),
    )

    fun filter(query: String): List<CommandSuggestion> {
        if (!query.startsWith("/")) return emptyList()
        val search = query.substring(1).lowercase()
        return commands.filter { it.command.contains(search, ignoreCase = true) }
    }
}
