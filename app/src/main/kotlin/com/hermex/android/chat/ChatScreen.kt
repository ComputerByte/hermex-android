package com.hermex.android.chat

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.network.dto.ProjectSummary
import com.hermex.android.core.notifications.HermexNotificationRoutes
import com.hermex.android.core.util.HermexLog
import com.hermex.android.core.util.TtftTracer
import com.hermex.android.navigation.LocalHermexDrawerOpener
import com.hermex.android.sessions.DeleteSessionDialog
import com.hermex.android.sessions.MoveToProjectDialog
import com.hermex.android.sessions.RenameSessionDialog
import com.hermex.android.ui.theme.HermexErrorBanner
import com.hermex.android.ui.theme.HermexRadii
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onSwitchedSession: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
    initialComposerDraft: String? = null,
    pendingFileUploadUris: List<String>? = null,
    // True only in the wide-layout right pane, where the persistent left pane already shows which
    // session is open -- the top bar's own "Chat" label adds nothing there, so it's dropped rather
    // than shown redundantly. Back/Files/Refresh stay exactly as they are either way.
    isPaneMode: Boolean = false,
    sessionId: String? = null,
    serverBaseUrl: String? = null,
    sessionTitle: String? = null,
    sessionProjectId: String? = null,
    projects: List<ProjectSummary> = emptyList(),
    onRenameSession: ((String) -> Unit)? = null,
    onDeleteSession: (() -> Unit)? = null,
    onMoveSession: ((String?) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openDrawer = LocalHermexDrawerOpener.current
    val context = LocalContext.current
    var showSessionMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialComposerDraft) {
        initialComposerDraft?.let(viewModel::stageDraftIfComposerEmpty)
    }

    // Upload pending shared files sequentially once the view model is ready
    LaunchedEffect(pendingFileUploadUris) {
        val uriStrings = pendingFileUploadUris ?: return@LaunchedEffect
        val uris = uriStrings.mapNotNull { android.net.Uri.parse(it) }
            .filter { it != android.net.Uri.EMPTY }
        if (uris.isNotEmpty()) {
            viewModel.uploadAttachmentsSequentially(uris)
        }
    }

    uiState.pendingProfileSwitch?.let { profileName ->
        val displayName = uiState.profileOptions.firstOrNull { it.normalizedName == profileName }?.displayName ?: profileName
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingProfileSwitch,
            shape = RoundedCornerShape(HermexRadii.Dialog),
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

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (!isPaneMode) {
                        Text(
                            "Chat",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { openDrawer() }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenWorkspace) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Files")
                    }
                    IconButton(onClick = viewModel::loadSession) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { showSessionMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showSessionMenu, onDismissRequest = { showSessionMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { showSessionMenu = false; showRenameDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Project") },
                                onClick = { showSessionMenu = false; showMoveDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    showSessionMenu = false
                                    sessionId?.let { chatShareSession(context, it, sessionTitle ?: "Session") }
                                },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { showSessionMenu = false; showDeleteDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            uiState.showRetryHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            }
            ChatComposer(
                composerState = ChatComposerState.from(uiState),
                profileSelectorState = ChatComposerProfileSelectorState.from(uiState),
                modelSelectorState = ChatComposerModelSelectorState.from(uiState),
                attachmentState = ChatComposerAttachmentState.from(uiState),
                actions = ChatComposerActions(
                    onTextChanged = viewModel::onComposerTextChanged,
                    // TtftTracer.start() lives inside ChatViewModel.sendMessage() itself (not
                    // here) so regenerate/retryLastMessage, which call sendMessage() directly,
                    // re-arm the same trace instead of reusing stale timing state.
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::cancelStream,
                    onSelectProfile = viewModel::selectProfile,
                    onOpenModelPicker = viewModel::refreshModelCatalogForPickerOpen,
                    onSelectModel = viewModel::selectComposerModel,
                    onAttachFile = viewModel::uploadAttachment,
                    onRemoveAttachment = viewModel::removePendingAttachment,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            uiState.cacheStatusMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(HermexRadii.Accessory),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val listState = rememberLazyListState()
                    val totalItems = uiState.messages.size + uiState.activeToolCalls.size +
                        (if (uiState.streamingText.isNotEmpty()) 1 else 0) +
                        (if (uiState.streamingReasoning.isNotEmpty()) 1 else 0) +
                        (if (uiState.isStreaming) 1 else 0)

                    // Whether the transcript should stay pinned to its true bottom as content
                    // grows. Streaming appends dozens of times a second, so this can't be
                    // re-derived from scratch on every append (that's what caused the original
                    // bug: unconditionally snapping back to the bottom fought any manual scroll
                    // the user made to read up, and -- since scrollToItem only aligns an item's
                    // *top* edge with the viewport -- also permanently hid the tail of a
                    // streaming bubble taller than one screen). Instead it's tracked explicitly:
                    // true on load/new turn, re-evaluated only when a scroll gesture actually
                    // finishes.
                    var stickToBottom by remember { mutableStateOf(true) }
                    val coroutineScope = rememberCoroutineScope()

                    // Re-evaluate stickiness only after a *real* user drag settles -- not after
                    // every scroll gesture. The scrollToItem/scrollBy calls below toggle
                    // `isScrollInProgress` exactly like a user drag does, so watching that alone
                    // can't tell "the user just scrolled away" from "we just finished our own
                    // auto-scroll". Misreading the latter as the former was the root cause of a
                    // reopened session sometimes sticking near the top instead of the newest
                    // message: our own multi-step scrollToItem-then-scrollBy sequence could get
                    // read as a finished user scroll partway through, flipping stickToBottom off
                    // and aborting the rest of the jump to bottom.
                    LaunchedEffect(listState) {
                        var userIsScrolling = false
                        launch {
                            listState.interactionSource.interactions.collect { interaction ->
                                if (interaction is DragInteraction.Start) userIsScrolling = true
                            }
                        }
                        launch {
                            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                                if (!scrolling && userIsScrolling) {
                                    userIsScrolling = false
                                    stickToBottom = !listState.canScrollForward
                                }
                            }
                        }
                    }

                    // A freshly started turn re-pins only if the user is already near the bottom --
                    // if they'd scrolled away reading history, don't yank them back.
                    LaunchedEffect(uiState.isStreaming) {
                        if (uiState.isStreaming && !listState.canScrollForward) {
                            stickToBottom = true
                        }
                    }

                    LaunchedEffect(stickToBottom) {
                        HermexLog.d("ChatScroll", "stickToBottom=$stickToBottom")
                    }

                    // Debounced scroll: during active streaming, throttle to ~100ms intervals
                    // so we don't scroll on every single token (the main cause of jittery video).
                    // On discrete content changes (new message, new tool call, stickToBottom toggle)
                    // scroll immediately without delay.
                    LaunchedEffect(totalItems, stickToBottom) {
                        if (totalItems > 0 && stickToBottom) {
                            listState.scrollToItem(totalItems - 1)
                            // A single scrollBy right after scrollToItem can undershoot the true
                            // end: on a reopened session with a long markdown message as the last
                            // item (code blocks, images), its first-pass measured height is smaller
                            // than what it settles to a frame or two later, so `canScrollForward`
                            // read immediately afterward under-reports how much further there is to
                            // go -- observed landing mid-message instead of on its last line. Yield
                            // a frame before each check/nudge so layout catches up; capped so this
                            // can't spin forever (streaming has its own debounced effect below and
                            // isn't expected to hit this loop).
                            var attempts = 0
                            while (attempts < 10) {
                                withFrameNanos {}
                                attempts++
                                if (!listState.canScrollForward) break
                                listState.scrollBy(Float.MAX_VALUE)
                            }
                        }
                    }

                    // Last stage of the TTFT trace: fires once streamingText first goes
                    // non-empty, then waits for the *next* frame callback so the mark reflects
                    // an actual committed frame rather than just the recomposition being
                    // scheduled -- Compose's snapshot state write here doesn't by itself mean
                    // anything has hit the screen yet.
                    LaunchedEffect(uiState.streamingText.isNotEmpty()) {
                        if (uiState.streamingText.isNotEmpty()) {
                            withFrameNanos {}
                            TtftTracer.markOnce("First token rendered in Compose")
                        }
                    }

                    // During active streaming, debounce the scroll so we don't re-scroll
                    // on every token. 100ms matches typical token inter-arrival time.
                    var lastScrollMs by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(uiState.streamingText) {
                        if (stickToBottom && uiState.isStreaming) {
                            val now = System.currentTimeMillis()
                            if (now - lastScrollMs > 100) {
                                lastScrollMs = now
                                listState.scrollToItem(totalItems - 1)
                                listState.scrollBy(Float.MAX_VALUE)
                            }
                        }
                    }

                    // Tool cards don't live in `messages` -- each one is anchored to the message
                    // index it will precede (see ToolCallUi.anchorMessageCount), so group them here
                    // and interleave rather than always rendering every tool call after every
                    // message regardless of which turn it actually happened in.
                    val toolCallsByAnchor = uiState.activeToolCalls.groupBy { it.anchorMessageCount }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Edit/regenerate mutate session history server-side (truncate), so they're
                        // withheld mid-turn -- racing a truncate against an in-flight send/stream
                        // would desync local state from the server's.
                        val canMutateHistory = !uiState.isSending && !uiState.isStreaming
                        uiState.messages.forEachIndexed { index, message ->
                            toolCallsByAnchor[index]?.forEach { toolCall ->
                                item(key = toolCall.stableId) {
                                    ToolCallCard(toolCall, initiallyExpanded = uiState.expandToolCallsByDefault)
                                }
                            }
                            val historicalToolCall = message.toHistoricalToolCallUi()
                            item(key = message.stableId) {
                                if (historicalToolCall != null) {
                                    ToolCallCard(historicalToolCall, initiallyExpanded = uiState.expandToolCallsByDefault)
                                } else {
                                    MessageBubble(
                                        message = message,
                                        sessionId = sessionId,
                                        serverBaseUrl = serverBaseUrl,
                                        onEdit = if (canMutateHistory && message.role == "user") {
                                            { viewModel.editMessage(index) }
                                        } else null,
                                        // regenerate() always targets the session's actual last message,
                                        // so only the last assistant turn can offer it.
                                        onRegenerate = if (canMutateHistory && message.role != "user" && index == uiState.messages.lastIndex) {
                                            viewModel::regenerate
                                        } else null,
                                    )
                                }
                            }
                        }
                        if (uiState.streamingReasoning.isNotEmpty()) {
                            item(key = "streaming-reasoning") {
                                ReasoningBlock(uiState.streamingReasoning, initiallyExpanded = uiState.expandThinkingByDefault)
                            }
                        }
                        // The current, not-yet-finalized turn's tool calls: anchored at the index
                        // the eventual finalized reply will occupy, i.e. messages.size right now.
                        toolCallsByAnchor[uiState.messages.size]?.forEach { toolCall ->
                            item(key = toolCall.stableId) {
                                ToolCallCard(toolCall, initiallyExpanded = uiState.expandToolCallsByDefault)
                            }
                        }
                        if (uiState.streamingText.isNotEmpty()) {
                            item(key = "streaming-text") { StreamingBubble(uiState.streamingText) }
                        }
                        if (uiState.isStreaming) {
                            item(key = "streaming-status") {
                                Text(
                                    text = "Generating…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Quick-jump controls -- only shown when there's somewhere to jump to, so they
                    // don't clutter a transcript that already fits on one screen.
                    val canJumpToTop by remember { derivedStateOf { listState.canScrollBackward } }
                    val canJumpToBottom by remember { derivedStateOf { listState.canScrollForward } }

                    if (canJumpToTop) {
                        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopEnd) {
                            ChatJumpButton(
                                icon = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Jump to oldest message",
                                onClick = { coroutineScope.launch { listState.scrollToItem(0) } },
                            )
                        }
                    }
                    if (canJumpToBottom) {
                        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomEnd) {
                            ChatJumpButton(
                                icon = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Jump to newest message",
                                onClick = {
                                    stickToBottom = true
                                    coroutineScope.launch {
                                        listState.scrollToItem(totalItems - 1)
                                        listState.scrollBy(Float.MAX_VALUE)
                                    }
                                },
                            )
                        }
                    }
                }

                uiState.errorMessage?.let { message ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Surface(
                            onClick = { viewModel.dismissError() },
                            shape = RoundedCornerShape(HermexRadii.Accessory),
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        Icons.Filled.Close,
                                        "Dismiss",
                                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.sendMessage() },
                                    modifier = Modifier.height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Reattach banner (disconnected stream)
                if (uiState.hasDisconnectedStream && !uiState.isStreaming) {
                    Box(
                        Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        HermexErrorBanner(
                            message = "Response interrupted — reconnect to resume streaming",
                            onRetry = { viewModel.reattachStream() },
                        )
                    }
                }
            }
        }
    }

    uiState.pendingApproval?.let { pending ->
        ApprovalRequestOverlay(
            pending = pending,
            isResponding = uiState.isRespondingToApproval,
            errorMessage = uiState.approvalErrorMessage,
            onChoice = { choice -> viewModel.respondToApproval(choice) },
            onSkipAll = { viewModel.skipAllApprovals() },
        )
    }

    uiState.pendingClarification?.let { pending ->
        ClarificationRequestOverlay(
            pending = pending,
            isResponding = uiState.isRespondingToClarification,
            errorMessage = uiState.clarificationErrorMessage,
            onSubmit = { response -> viewModel.respondToClarification(response) },
        )
    }

    if (showRenameDialog) {
        RenameSessionDialog(
            currentName = sessionTitle ?: "",
            onConfirm = { newTitle ->
                onRenameSession?.invoke(newTitle)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
    if (showDeleteDialog) {
        DeleteSessionDialog(
            sessionTitle = sessionTitle ?: "this session",
            onConfirm = {
                onDeleteSession?.invoke()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
    if (showMoveDialog) {
        MoveToProjectDialog(
            projects = projects,
            currentProjectId = sessionProjectId,
            onConfirm = { projectId ->
                onMoveSession?.invoke(projectId)
                showMoveDialog = false
            },
            onDismiss = { showMoveDialog = false },
        )
    }
    } // end outer Box
}

private fun chatShareSession(context: Context, sessionId: String, sessionTitle: String) {
    val uri = HermexNotificationRoutes.session(sessionId)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, uri)
        putExtra(Intent.EXTRA_SUBJECT, sessionTitle)
    }
    context.startActivity(Intent.createChooser(intent, "Share Session"))
}

/** A small floating circular button for the transcript's jump-to-top/jump-to-bottom controls --
 * styled like [ToolCallCard]'s surface (bordered `surfaceContainerHighest`, primary-tinted icon)
 * rather than a stock `FloatingActionButton`, so it reads as part of the same card language
 * instead of introducing a new visual element. */
@Composable
private fun ChatJumpButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
