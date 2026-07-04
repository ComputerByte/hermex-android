package com.hermex.android.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
// Icons used without explicit import: ArrowBack, KeyboardArrowUp via Icons.AutoMirrored.Filled / Icons.Filled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.network.dto.WorkspaceEntry

/**
 * Read-only workspace file browser for one chat session. Directory listing and the open-file
 * viewer are the same screen/[WorkspaceViewModel] switching content based on
 * [WorkspaceUiState.selectedFile] (null = listing, non-null = viewer) -- "back" from the viewer
 * just closes it locally, it never re-navigates or refetches.
 *
 * No create/rename/delete/upload controls exist anywhere on this screen -- there is nothing here
 * that mutates the workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openFile = uiState.selectedFile
    val context = LocalContext.current

    // Load git status when the directory path changes (root → subfolder → parent).
    LaunchedEffect(uiState.currentPath) {
        viewModel.loadGitStatus(uiState.currentPath)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(openFile?.name ?: "Files", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (openFile == null) {
                            Text(
                                text = if (uiState.isAtRoot) "Root" else uiState.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    // While a file is open this closes just the viewer, back to the directory
                    // listing underneath -- it does not leave the Workspace screen.
                    IconButton(onClick = { if (openFile != null) viewModel.closeFile() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (openFile == null) {
                        // Refresh
                        IconButton(onClick = viewModel::refreshDirectory) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = viewModel::navigateUp, enabled = !uiState.isAtRoot) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up to parent folder")
                        }
                        // New file
                        TextButton(onClick = viewModel::showCreateFileDialog, modifier = Modifier.height(32.dp)) {
                            Text("+ File", style = MaterialTheme.typography.labelSmall)
                        }
                        // New folder
                        TextButton(onClick = viewModel::showCreateFolderDialog, modifier = Modifier.height(32.dp)) {
                            Text("+ Folder", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (openFile != null) {
                FileViewerContent(
                    file = openFile,
                    onRetry = viewModel::retryOpenFile,
                    onStartEdit = viewModel::startEditing,
                    onUpdateEditedContent = viewModel::updateEditedContent,
                    onSave = viewModel::saveFile,
                    onCancelEdit = viewModel::cancelEditing,
                    onClose = viewModel::closeFile,
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search field
                    // Git status card — always visible when loaded, even for non-git repos
                    uiState.gitState?.let { git ->
                        GitStatusCard(
                            gitState = git,
                            onOpenDiff = { file -> viewModel.openGitDiff(file) },
                            onCloseDiff = viewModel::closeGitDiff,
                            onRetry = { viewModel.loadGitStatus(viewModel.uiState.value.currentPath) },
                        )
                    }
                    // Always show search bar and directory content
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                    )
                    DirectoryContent(
                        uiState = uiState,
                        onEntryClick = { entry ->
                            if (entry.isFolder) viewModel.navigateInto(entry) else viewModel.openFile(entry)
                        },
                        onRetry = viewModel::retryDirectory,
                        onCopyPath = { entry ->
                            copyToClipboard(context, entry.name ?: entry.path ?: "entry", entry.path ?: "")
                        },
                        onRename = viewModel::showRenameDialog,
                        onDelete = viewModel::showDeleteFileDialog,
                    )
                }
            }
        }
    }

    // Create file/folder dialog
    uiState.createDialog?.let { dialog ->
        CreateDialog(
            dialog = dialog,
            onDismiss = viewModel::dismissCreateDialog,
            onNameChange = viewModel::updateCreateName,
            onConfirm = viewModel::confirmCreate,
        )
    }

    // Rename dialog
    uiState.renameDialog?.let { dialog ->
        RenameDialog(
            dialog = dialog,
            onDismiss = viewModel::dismissRenameDialog,
            onNameChange = viewModel::updateRenameName,
            onConfirm = viewModel::confirmRename,
        )
    }

    // Delete confirmation dialog
    uiState.deleteDialog?.let { dialog ->
        DeleteFileDialog(
            dialog = dialog,
            onDismiss = viewModel::dismissDeleteDialog,
            onConfirm = viewModel::confirmDeleteFile,
        )
    }
}

@Composable
private fun DeleteFileDialog(
    dialog: DeleteDialogState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${dialog.targetName}?") },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete \"${dialog.targetName}\"?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (dialog.errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = dialog.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !dialog.isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (dialog.isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isDeleting) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenameDialog(
    dialog: RenameDialogState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column {
                Text(
                    text = "Rename \"${dialog.originalName}\" to:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text("New name") },
                    singleLine = true,
                    enabled = !dialog.isRenaming,
                    modifier = Modifier.fillMaxWidth(),
                    isError = dialog.errorMessage != null,
                    supportingText = if (dialog.errorMessage != null) {
                        { Text(dialog.errorMessage ?: "", color = MaterialTheme.colorScheme.error) }
                    } else null,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = dialog.isValid && !dialog.isUnchanged && !dialog.isRenaming,
            ) {
                if (dialog.isRenaming) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isRenaming) { Text("Cancel") }
        },
    )
}

@Composable
private fun CreateDialog(
    dialog: CreateDialogState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val title = when (dialog.mode) {
        CreateMode.FILE -> "New file"
        CreateMode.FOLDER -> "New folder"
    }
    val label = when (dialog.mode) {
        CreateMode.FILE -> "File name"
        CreateMode.FOLDER -> "Folder name"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text(label) },
                    singleLine = true,
                    enabled = !dialog.isCreating,
                    modifier = Modifier.fillMaxWidth(),
                    isError = dialog.errorMessage != null,
                    supportingText = if (dialog.errorMessage != null) {
                        { Text(dialog.errorMessage ?: "", color = MaterialTheme.colorScheme.error) }
                    } else null,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = dialog.isValid && !dialog.isCreating,
            ) {
                if (dialog.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isCreating) { Text("Cancel") }
        },
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("Search files...") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { /* filter is applied live */ }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun DirectoryContent(
    uiState: WorkspaceUiState,
    onEntryClick: (WorkspaceEntry) -> Unit,
    onRetry: () -> Unit,
    onCopyPath: (WorkspaceEntry) -> Unit,
    onRename: (WorkspaceEntry) -> Unit,
    onDelete: (WorkspaceEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Client-side search filter: narrow entries by name without modifying the original list.
    val filteredEntries = if (uiState.searchQuery.isBlank()) {
        uiState.entries
    } else {
        uiState.entries.filter { entry ->
            entry.name?.contains(uiState.searchQuery, ignoreCase = true) == true ||
                entry.path?.contains(uiState.searchQuery, ignoreCase = true) == true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            // First-load (or navigation) failure with nothing to show underneath -- the one case
            // that gets a dedicated full-screen state with a prominent Retry action.
            uiState.errorMessage != null && uiState.entries.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }

            uiState.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This folder is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            filteredEntries.isEmpty() && uiState.searchQuery.isNotBlank() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No files match \"${uiState.searchQuery}\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredEntries, key = { it.path ?: it.name ?: it.hashCode() }) { entry ->
                    WorkspaceEntryRow(entry = entry, onClick = { onEntryClick(entry) }, onCopyPath = { onCopyPath(entry) }, onRename = { onRename(entry) }, onDelete = { onDelete(entry) })
                }
            }
        }

        // A navigation failure that left the previous listing on screen
        if (uiState.errorMessage != null && uiState.entries.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.errorMessage, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceEntryRow(
    entry: WorkspaceEntry,
    onClick: () -> Unit,
    onCopyPath: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(entry.name ?: entry.path ?: "Unnamed", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isFolder) {
                    Text("Folder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        text = entry.size?.let(::formatFileSize) ?: "File",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions",
                            modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy path") },
                            onClick = {
                                showMenu = false
                                onCopyPath()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                        )
                        if (!entry.isFolder) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FileViewerContent(
    file: FileViewState,
    onRetry: () -> Unit,
    onStartEdit: () -> Unit,
    onUpdateEditedContent: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Handle back/close with unsaved changes
    fun handleClose() {
        if (file.hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            if (file.isEditing) onCancelEdit()
            onClose()
        }
    }

    // Discard confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes to ${file.name}.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onCancelEdit()
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Edit/Save buttons at top
        if (file.content is WorkspaceFileContent.Text && !file.content.truncated) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (file.isEditing) {
                    Button(
                        onClick = { handleClose() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.height(32.dp).padding(end = 8.dp),
                    ) { Text("Cancel", style = MaterialTheme.typography.labelSmall) }
                    Button(
                        onClick = onSave,
                        enabled = file.hasUnsavedChanges && !file.isSaving,
                        modifier = Modifier.height(32.dp),
                    ) { Text("Save", style = MaterialTheme.typography.labelSmall) }
                } else {
                    TextButton(onClick = onStartEdit, modifier = Modifier.height(32.dp)) {
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Main content area (adjusted for button space at top)
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 40.dp)
        ) {
            when {
                file.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                file.errorMessage != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = file.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }

                file.content is WorkspaceFileContent.Unavailable -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = file.content.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                file.content is WorkspaceFileContent.Text -> {
                    if (file.isEditing) {
                        // Editable text field
                        OutlinedTextField(
                            value = file.editedContent,
                            onValueChange = onUpdateEditedContent,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            if (file.content.truncated) {
                                Text(
                                    text = "Showing partial content -- this file is too large to display in full.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                )
                            }
                            Text(text = file.content.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // Save error
                    if (file.saveError != null) {
                        Text(
                            text = file.saveError,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    // Saving indicator
                    if (file.isSaving) {
                        Box(
                            Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Saving...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No content to show.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Unsaved changes dialog on back (file close)
    // Use handleClose for the back action
}

/** Copies a workspace path to the system clipboard and shows a brief toast. */
private fun copyToClipboard(context: Context, label: String, path: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, path))
    Toast.makeText(context, "Copied: $path", Toast.LENGTH_SHORT).show()
}

/** Some servers only send `type: "dir"`, others only `is_directory`/`is_dir` -- treat either as
 * authoritative. Mirrors the same check [WorkspaceViewModel] uses internally (kept private and
 * duplicated per-file rather than shared, since it's a one-line display/dispatch decision, not a
 * DTO or API concern). */
private val WorkspaceEntry.isFolder: Boolean
    get() = isDirectory == true || type == "dir"

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

// ── Git (read-only) ──

@Composable
private fun GitStatusCard(
    gitState: GitState,
    onOpenDiff: (com.hermex.android.core.network.dto.GitFileStatus) -> Unit,
    onCloseDiff: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // If a diff is open, show the diff viewer instead of the status card
    val diff = gitState.selectedDiff
    if (diff != null) {
        GitDiffViewer(
            diff = diff,
            onClose = onCloseDiff,
        )
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when {
                gitState.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading git status...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                gitState.errorMessage != null -> {
                    Text(gitState.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onRetry, modifier = Modifier.height(28.dp)) { Text("Retry", style = MaterialTheme.typography.labelSmall) }
                }

                else -> {
                    // Not a git repo or repo with no changes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Git",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (gitState.isGit) {
                            Text(
                                text = gitState.branch ?: "(detached)",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            gitState.commit?.let { commit ->
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = commit,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        } else {
                            Text(
                                text = "Not a git repository in workspace",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (gitState.isGit) {
                        if (gitState.changedFileCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${gitState.changedFileCount} changed file(s): +${gitState.additions}/-${gitState.deletions}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            gitState.files.forEach { file ->
                                val statusColor = when (file.status) {
                                    "M" -> MaterialTheme.colorScheme.primary
                                    "A" -> MaterialTheme.colorScheme.tertiary
                                    "D" -> MaterialTheme.colorScheme.error
                                    "?" -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenDiff(file) }
                                        .padding(vertical = 2.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "[${file.status ?: "?"}]",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = statusColor,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = file.path ?: "(unknown)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if ((file.additions ?: 0) > 0 || (file.deletions ?: 0) > 0) {
                                        val adds = file.additions ?: 0
                                        val dels = file.deletions ?: 0
                                        Text(
                                            text = "+$adds/-$dels",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (adds > dels) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text("Clean working tree.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitDiffViewer(
    diff: DiffViewState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Diff: ${diff.path}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onClose, modifier = Modifier.height(28.dp)) {
                    Text("Close", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                diff.isLoading -> {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                diff.errorMessage != null -> {
                    Text(diff.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                diff.binary -> {
                    Text("Binary file — diff not available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(
                        text = diff.diff,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
