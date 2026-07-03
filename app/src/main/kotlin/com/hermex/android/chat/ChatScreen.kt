package com.hermex.android.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onSwitchedSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.pendingProfileSwitch?.let { profileName ->
        val displayName = uiState.profileOptions.firstOrNull { it.normalizedName == profileName }?.displayName ?: profileName
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingProfileSwitch,
            title = { Text("Start a new session?") },
            text = { Text("Switching to \"$displayName\" starts a new session on that profile. This chat's history stays here.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingProfileSwitch(onSwitchedSession) }) {
                    Text("Start New Session")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingProfileSwitch) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            ChatComposer(
                text = uiState.composerText,
                onTextChanged = viewModel::onComposerTextChanged,
                onSend = viewModel::sendMessage,
                onStop = viewModel::cancelStream,
                isStreaming = uiState.isStreaming,
                isSending = uiState.isSending,
                profileOptions = uiState.profileOptions,
                selectedProfileName = uiState.selectedProfileName,
                isSwitchingProfile = uiState.isSwitchingProfile,
                onSelectProfile = viewModel::selectProfile,
                modelCatalogGroups = uiState.modelCatalogGroups,
                currentModel = uiState.currentModel,
                currentModelProvider = uiState.currentModelProvider,
                isLoadingModelCatalog = uiState.isLoadingModelCatalog,
                isUpdatingComposerConfiguration = uiState.isUpdatingComposerConfiguration,
                onOpenModelPicker = viewModel::refreshModelCatalogForPickerOpen,
                onSelectModel = viewModel::selectComposerModel,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val listState = rememberLazyListState()
                val totalItems = uiState.messages.size + uiState.activeToolCalls.size +
                    (if (uiState.streamingText.isNotEmpty()) 1 else 0) +
                    (if (uiState.streamingReasoning.isNotEmpty()) 1 else 0)

                // Plain (non-animated) scroll: streamingText changes on every token, so an
                // animated scroll here would re-trigger dozens of times a second and stutter.
                // A discrete new message/tool card is rare enough that the instant jump still
                // reads as smooth.
                LaunchedEffect(totalItems, uiState.streamingText) {
                    if (totalItems > 0) listState.scrollToItem(totalItems - 1)
                }

                // Tool cards don't live in `messages` -- each one is anchored to the message
                // index it will precede (see ToolCallUi.anchorMessageCount), so group them here
                // and interleave rather than always rendering every tool call after every
                // message regardless of which turn it actually happened in.
                val toolCallsByAnchor = uiState.activeToolCalls.groupBy { it.anchorMessageCount }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.messages.forEachIndexed { index, message ->
                        toolCallsByAnchor[index]?.forEach { toolCall ->
                            item(key = toolCall.stableId) { ToolCallCard(toolCall) }
                        }
                        item(key = message.stableId) { MessageBubble(message) }
                    }
                    if (uiState.streamingReasoning.isNotEmpty()) {
                        item(key = "streaming-reasoning") {
                            ReasoningBlock(uiState.streamingReasoning, initiallyExpanded = uiState.expandThinkingByDefault)
                        }
                    }
                    // The current, not-yet-finalized turn's tool calls: anchored at the index
                    // the eventual finalized reply will occupy, i.e. messages.size right now.
                    toolCallsByAnchor[uiState.messages.size]?.forEach { toolCall ->
                        item(key = toolCall.stableId) { ToolCallCard(toolCall) }
                    }
                    if (uiState.streamingText.isNotEmpty()) {
                        item(key = "streaming-text") { StreamingBubble(uiState.streamingText) }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
