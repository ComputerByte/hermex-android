package com.hermex.android.chat

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermex.android.core.network.dto.ModelCatalogGroup
import com.hermex.android.core.network.dto.ModelCatalogOption
import com.hermex.android.core.network.dto.ProfileSummary
import com.hermex.android.core.util.HermexLog
import com.hermex.android.ui.theme.HermexRadii

/** [ChatComposer]'s callbacks, grouped so adding a future action (slash commands) doesn't widen
 * [ChatComposer]'s own parameter list. */
data class ChatComposerActions(
    val onTextChanged: (String) -> Unit,
    val onSend: () -> Unit,
    val onStop: () -> Unit,
    val onSelectProfile: (String) -> Unit,
    val onOpenModelPicker: () -> Unit,
    val onSelectModel: (ModelCatalogOption) -> Unit,
    val onAttachFile: (Uri) -> Unit,
    val onRemoveAttachment: (String) -> Unit,
)

/** The profile dropdown's own list/selection data -- separate from [ChatComposerState] because
 * it's plain display data, not busy/disabled state. */
data class ChatComposerProfileSelectorState(
    val profileOptions: List<ProfileSummary>,
    val selectedProfileName: String?,
) {
    companion object {
        fun from(uiState: ChatUiState): ChatComposerProfileSelectorState = ChatComposerProfileSelectorState(
            profileOptions = uiState.profileOptions,
            selectedProfileName = uiState.selectedProfileName,
        )
    }
}

/** The model dropdown's own list/selection data -- separate from [ChatComposerState] for the
 * same reason as [ChatComposerProfileSelectorState]. */
data class ChatComposerModelSelectorState(
    val modelCatalogGroups: List<ModelCatalogGroup>,
    val currentModel: String?,
    val currentModelProvider: String?,
    val isLoadingModelCatalog: Boolean,
) {
    companion object {
        fun from(uiState: ChatUiState): ChatComposerModelSelectorState = ChatComposerModelSelectorState(
            modelCatalogGroups = uiState.modelCatalogGroups,
            currentModel = uiState.currentModel,
            currentModelProvider = uiState.currentModelProvider,
            isLoadingModelCatalog = uiState.isLoadingModelCatalog,
        )
    }
}

/** The pending-attachment strip's own list data -- separate from [ChatComposerState] for the same
 * reason as [ChatComposerProfileSelectorState]: it's plain display data, not busy/disabled state. */
data class ChatComposerAttachmentState(
    val pendingAttachments: List<PendingAttachmentUi>,
) {
    companion object {
        fun from(uiState: ChatUiState): ChatComposerAttachmentState = ChatComposerAttachmentState(
            pendingAttachments = uiState.pendingAttachments,
        )
    }
}

@Composable
fun ChatComposer(
    composerState: ChatComposerState,
    profileSelectorState: ChatComposerProfileSelectorState,
    modelSelectorState: ChatComposerModelSelectorState,
    attachmentState: ChatComposerAttachmentState,
    actions: ChatComposerActions,
    modifier: Modifier = Modifier,
) {
    // enableEdgeToEdge() (MainActivity) draws the app behind the system navigation bar, so a
    // bottom-docked bar like this one must explicitly reserve space for it -- otherwise the
    // send button/text field render underneath the system's back/home/recents buttons.
    // Deliberately NOT also adding imePadding() here: Scaffold's own default
    // contentWindowInsets (WindowInsets.safeDrawing, which includes ime) already accounts for
    // the keyboard once, so stacking an explicit imePadding() on top double-counted the
    // keyboard height and left a large blank gap above it when the keyboard opened.
    // Logged only on change, not every recomposition -- lets `adb logcat -s Hermex/Composer`
    // confirm Scaffold's innerPadding is actually reserving this much space for the transcript
    // (see ChatScreen investigation notes on composer/content overlap).
    var lastLoggedHeightPx by remember { mutableStateOf(-1) }
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .onGloballyPositioned { coordinates ->
                val heightPx = coordinates.size.height
                if (heightPx != lastLoggedHeightPx) {
                    lastLoggedHeightPx = heightPx
                    HermexLog.d("Composer", "measured height=${heightPx}px")
                }
            },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 3.dp,
    ) {
        Column {
            if (attachmentState.pendingAttachments.isNotEmpty()) {
                PendingAttachmentStrip(
                    attachments = attachmentState.pendingAttachments,
                    onRemove = actions.onRemoveAttachment,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachFileButton(
                    enabled = composerState.isAttachButtonEnabled,
                    isUploading = composerState.isUploadingAttachment,
                    onAttachFile = actions.onAttachFile,
                )
                ProfileSelectorButton(
                    profileOptions = profileSelectorState.profileOptions,
                    selectedProfileName = profileSelectorState.selectedProfileName,
                    isSwitchingProfile = composerState.isProfileSelectorLoading,
                    onSelectProfile = actions.onSelectProfile,
                )
                ModelSelectorButton(
                    modelCatalogGroups = modelSelectorState.modelCatalogGroups,
                    currentModel = modelSelectorState.currentModel,
                    currentModelProvider = modelSelectorState.currentModelProvider,
                    isLoadingModelCatalog = modelSelectorState.isLoadingModelCatalog,
                    isUpdatingComposerConfiguration = composerState.isModelSelectorLoading,
                    onOpenModelPicker = actions.onOpenModelPicker,
                    onSelectModel = actions.onSelectModel,
                )
                OutlinedTextField(
                    value = composerState.text,
                    onValueChange = actions.onTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    enabled = composerState.isTextFieldEnabled,
                    maxLines = 5,
                    shape = RoundedCornerShape(HermexRadii.Composer),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
                )
                Spacer(Modifier.width(4.dp))
                when {
                    composerState.showStopButton -> FilledTonalIconButton(
                        onClick = actions.onStop,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Stop")
                    }
                    composerState.showSendingSpinner -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> FilledIconButton(onClick = actions.onSend, enabled = composerState.canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

/** No paperclip icon ships in `material-icons-core` (only `material-icons-extended` has one, and
 * adding that dependency purely for one icon isn't worth it) -- `Add` is the closest already-
 * available icon and is what the MVP spec explicitly allows as a fallback. Opens the system
 * document picker (Storage Access Framework), so no storage permission is needed: the picker
 * itself grants this app read access to whatever the user selects. */
@Composable
private fun AttachFileButton(
    enabled: Boolean,
    isUploading: Boolean,
    onAttachFile: (Uri) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttachFile)
    }

    if (isUploading) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        return
    }

    IconButton(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = enabled) {
        Icon(Icons.Filled.Add, contentDescription = "Attach file")
    }
}

@Composable
private fun PendingAttachmentStrip(
    attachments: List<PendingAttachmentUi>,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(HermexRadii.Accessory),
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = attachment.name ?: "attachment",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        attachment.size?.let { size ->
                            Text(
                                text = Formatter.formatShortFileSize(context, size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(attachment.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${attachment.name ?: "attachment"}", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSelectorButton(
    profileOptions: List<ProfileSummary>,
    selectedProfileName: String?,
    isSwitchingProfile: Boolean,
    onSelectProfile: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    if (isSwitchingProfile) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        return
    }

    IconButton(onClick = { menuExpanded = true }, enabled = profileOptions.isNotEmpty()) {
        Icon(Icons.Filled.Person, contentDescription = "Profile: ${selectedProfileName ?: "none"}")
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        profileOptions.forEach { profile ->
            val name = profile.normalizedName ?: return@forEach
            DropdownMenuItem(
                text = { Text(profile.displayName) },
                trailingIcon = {
                    if (name == selectedProfileName) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectProfile(name)
                },
            )
        }
    }
}

@Composable
private fun ModelSelectorButton(
    modelCatalogGroups: List<ModelCatalogGroup>,
    currentModel: String?,
    currentModelProvider: String?,
    isLoadingModelCatalog: Boolean,
    isUpdatingComposerConfiguration: Boolean,
    onOpenModelPicker: () -> Unit,
    onSelectModel: (ModelCatalogOption) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    if (isUpdatingComposerConfiguration) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        return
    }

    IconButton(onClick = {
        menuExpanded = true
        onOpenModelPicker()
    }) {
        Icon(Icons.Filled.Settings, contentDescription = "Model: ${currentModel ?: "none"}")
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.heightIn(max = 400.dp),
    ) {
        when {
            isLoadingModelCatalog && modelCatalogGroups.isEmpty() -> DropdownMenuItem(
                text = { Text("Loading models...") },
                onClick = {},
                enabled = false,
            )
            modelCatalogGroups.isEmpty() -> DropdownMenuItem(
                text = { Text("No models available") },
                onClick = {},
                enabled = false,
            )
            else -> modelCatalogGroups.forEach { group ->
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                group.models.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        trailingIcon = {
                            if (option.matchesSelection(currentModel, currentModelProvider)) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onSelectModel(option)
                        },
                    )
                }
            }
        }
    }
}
