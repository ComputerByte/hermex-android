package com.hermex.android.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        IconButton(onClick = viewModel::navigateUp, enabled = !uiState.isAtRoot) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up to parent folder")
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
                FileViewerContent(file = openFile, onRetry = viewModel::retryOpenFile)
            } else {
                DirectoryContent(
                    uiState = uiState,
                    onEntryClick = { entry ->
                        if (entry.isFolder) viewModel.navigateInto(entry) else viewModel.openFile(entry)
                    },
                    onRetry = viewModel::retryDirectory,
                )
            }
        }
    }
}

@Composable
private fun DirectoryContent(
    uiState: WorkspaceUiState,
    onEntryClick: (WorkspaceEntry) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.entries, key = { it.path ?: it.name ?: it.hashCode() }) { entry ->
                    WorkspaceEntryRow(entry = entry, onClick = { onEntryClick(entry) })
                }
            }
        }

        // A navigation failure that left the previous listing on screen (see
        // WorkspaceViewModel.loadDirectory) surfaces here instead, alongside the stale-but-still
        // valid entries above, matching how errors are shown elsewhere in this app.
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
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(entry.name ?: entry.path ?: "Unnamed", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            if (entry.isFolder) {
                Text("Folder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = entry.size?.let(::formatFileSize) ?: "File",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun FileViewerContent(
    file: FileViewState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
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

            file.content is WorkspaceFileContent.Text -> Column(
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
                // Raw text, no syntax highlighting -- consistent with SkillDetailScreen's content
                // view and the rest of this MVP's scope.
                Text(text = file.content.text, style = MaterialTheme.typography.bodyMedium)
            }

            // file.content == null and no error and not loading shouldn't happen in practice, but
            // render *something* rather than a blank screen if it ever does.
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No content to show.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
