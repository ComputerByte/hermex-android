package com.hermex.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.storage.HermexServerConfig
import com.hermex.android.ui.theme.HermexReadableContent
import com.hermex.android.ui.theme.HermexRadii

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    viewModel: ServersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // True only in the wide-layout right pane, where the persistent left pane already shows
    // which section is open -- the top bar's own literal title adds nothing there, so it's
    // dropped rather than shown redundantly. The back button stays exactly as it is either way.
    isPaneMode: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showEditor) {
        ServerEditorDialog(uiState = uiState, viewModel = viewModel)
    }

    uiState.serverPendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRemoval,
            shape = RoundedCornerShape(HermexRadii.Dialog),
            title = { Text("Remove ${target.name}?") },
            text = {
                Text(
                    if (target.id == uiState.activeServerId && uiState.servers.size > 1) {
                        "This server is active. Another configured server will become active. Its saved session and headers will be deleted."
                    } else if (target.id == uiState.activeServerId) {
                        "This is your only configured server. Removing it returns to setup. Its saved session and headers will be deleted."
                    } else {
                        "Its saved session and headers will be deleted."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemoval) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRemoval) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (!isPaneMode) {
                        Text(
                            "Servers",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        HermexReadableContent(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.servers, key = { it.id }) { config ->
                        ServerRow(
                            config = config,
                            isActive = config.id == uiState.activeServerId,
                            isSwitching = config.id == uiState.switchingServerId,
                            onSwitch = { viewModel.switchTo(config.id) },
                            onEdit = { viewModel.startEditing(config) },
                            onRemove = { viewModel.requestRemoval(config) },
                        )
                    }
                    item(key = "add-server") {
                        OutlinedButton(
                            onClick = viewModel::startAdding,
                            shape = RoundedCornerShape(HermexRadii.Cell),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Add Server")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    config: HermexServerConfig,
    isActive: Boolean,
    isSwitching: Boolean,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(HermexRadii.Cell))
            .clickable(enabled = !isActive && !isSwitching, onClick = onSwitch)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(config.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                isSwitching -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                isActive -> Icon(Icons.Filled.Check, contentDescription = "Active server", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ServerEditorDialog(uiState: ServersUiState, viewModel: ServersViewModel) {
    val isEditing = uiState.editingServerId != null
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    AlertDialog(
        onDismissRequest = viewModel::dismissEditor,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text(if (isEditing) "Edit Server" else "Add Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.editorName,
                    onValueChange = viewModel::updateEditorName,
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Mac Mini") },
                    singleLine = true,
                    shape = RoundedCornerShape(HermexRadii.Cell),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.editorUrl,
                    onValueChange = viewModel::updateEditorUrl,
                    label = { Text("Server URL") },
                    placeholder = { Text("hermes.example.com") },
                    singleLine = true,
                    isError = uiState.editorError != null,
                    shape = RoundedCornerShape(HermexRadii.Cell),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Test Connection button + result
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = viewModel::testConnection,
                        enabled = !uiState.isSavingEditor && !uiState.isTestingConnection &&
                            uiState.editorUrl.isNotBlank(),
                        shape = RoundedCornerShape(HermexRadii.Cell),
                    ) {
                        if (uiState.isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Testing…")
                        } else {
                            Text("Test Connection")
                        }
                    }
                    uiState.connectionTestError?.let { error ->
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (uiState.connectionTestError == null && uiState.editorUrl.isNotBlank() && !uiState.isTestingConnection) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                uiState.editorError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (uiState.isSavingEditor) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = viewModel::saveEditor) { Text(if (isEditing) "Save" else "Add") }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissEditor) { Text("Cancel") }
        },
    )
}
