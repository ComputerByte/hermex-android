package com.hermex.android.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.network.dto.ProjectColorPalette
import com.hermex.android.core.network.dto.ProjectSummary
import com.hermex.android.sessions.relativeTimeText
import com.hermex.android.ui.theme.HermexReadableContent
import com.hermex.android.ui.theme.HermexRadii

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<ProjectSummary?>(null) }

    if (showCreateDialog) {
        ProjectFormDialog(
            title = "New Project",
            initialName = "",
            initialColor = ProjectColorPalette.colors[uiState.projects.size % ProjectColorPalette.colors.size],
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color ->
                showCreateDialog = false
                viewModel.create(name, color)
            },
        )
    }

    editingProject?.let { project ->
        ProjectFormDialog(
            title = "Rename Project",
            initialName = project.name.orEmpty(),
            initialColor = project.color ?: ProjectColorPalette.colors.first(),
            onDismiss = { editingProject = null },
            onConfirm = { name, color ->
                editingProject = null
                project.projectId?.let { viewModel.rename(it, name, color) }
            },
        )
    }

    deleteCandidate?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            shape = RoundedCornerShape(HermexRadii.Dialog),
            title = { Text("Delete project?") },
            text = { Text("Delete \"${project.displayName}\"? This cannot be undone. Sessions in it are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    project.projectId?.let(viewModel::delete)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Projects",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isMutating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        FilledIconButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "New project")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        HermexReadableContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::load) { Text("Retry") }
                    }
                }

                uiState.projects.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(uiState.projects, key = { it.projectId ?: it.hashCode() }) { project ->
                            ProjectRow(
                                project = project,
                                onRename = { editingProject = project },
                                onDelete = { deleteCandidate = project },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                if (uiState.projects.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Surface(
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
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: ProjectSummary,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(HermexRadii.Cell),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { ColorSwatch(project.color, size = 20.dp) },
            headlineContent = { Text(project.displayName, fontWeight = FontWeight.SemiBold) },
            supportingContent = project.createdAt?.let { createdAt -> { Text("Created ${relativeTimeText(createdAt)}") } },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ProjectFormDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Project name") },
                    singleLine = true,
                    shape = RoundedCornerShape(HermexRadii.Cell),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ProjectColorPalette.colors.forEach { hex ->
                        ColorSwatch(
                            hex = hex,
                            size = 32.dp,
                            selected = hex == color,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { color = hex },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ColorSwatch(
    hex: String?,
    size: androidx.compose.ui.unit.Dp,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
