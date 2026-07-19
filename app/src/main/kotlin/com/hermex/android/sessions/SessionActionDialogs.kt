package com.hermex.android.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermex.android.core.network.dto.ProjectSummary
import com.hermex.android.ui.theme.HermexRadii

@Composable
fun RenameSessionDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(HermexRadii.Cell),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DeleteSessionDialog(
    sessionTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text("Delete Session") },
        text = {
            Text("Delete \"$sessionTitle\"? This cannot be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun MoveToProjectDialog(
    projects: List<ProjectSummary>,
    currentProjectId: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
    isLoadingProjects: Boolean = false,
    projectsErrorMessage: String? = null,
) {
    var selectedProjectId by remember { mutableStateOf(currentProjectId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text("Move to Project") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedProjectId = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedProjectId == null, onClick = { selectedProjectId = null })
                    Spacer(Modifier.width(8.dp))
                    Text("No project")
                }
                // Projects load asynchronously (see SessionListViewModel.loadProjects) -- while in
                // flight or on failure, the picker still degrades to just "No project" above rather
                // than looking broken or blocking the dialog.
                if (isLoadingProjects && projects.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (projectsErrorMessage != null && projects.isEmpty()) {
                    Text(
                        text = projectsErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    projects.forEach { project ->
                        val id = project.projectId ?: return@forEach
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProjectId = id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedProjectId == id, onClick = { selectedProjectId = id })
                            Spacer(Modifier.width(8.dp))
                            Text(project.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedProjectId) }) { Text("Move") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
