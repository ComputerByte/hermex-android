package com.hermex.android.chat

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * A two-tier composer dock (input row on top, a horizontally-scrollable control strip of
 * Hermex-styled chips below), modeled on the Hermes WebUI/Desktop composer rather than a plain
 * Material `TextField` row: the dock is edge-to-edge with only its top corners rounded (a real
 * dock rising from the bottom of the screen, not a floating margin'd card), and Attach/Profile/
 * Model render as small tonal chips -- with a label when the underlying data already carries one
 * (profile name, model name) -- instead of bare icons.
 */
@Composable
fun ChatComposer(
    composerState: ChatComposerState,
    profileSelectorState: ChatComposerProfileSelectorState,
    modelSelectorState: ChatComposerModelSelectorState,
    attachmentState: ChatComposerAttachmentState,
    actions: ChatComposerActions,
    modifier: Modifier = Modifier,
) {
    // enableEdgeToEdge() (MainActivity) draws the app behind the system navigation bar. The dock's
    // tonal background is allowed to extend all the way to the physical bottom edge (behind
    // gesture nav), matching a real bottom dock -- only the interactive content inside is padded
    // clear of the nav bar via navigationBarsPadding() on the inner Column, not the outer Surface.
    // Deliberately NOT also adding imePadding(): Scaffold's own default contentWindowInsets
    // (WindowInsets.safeDrawing, which includes ime) already accounts for the keyboard once, so
    // stacking an explicit imePadding() on top double-counted the keyboard height and left a large
    // blank gap above it when the keyboard opened.
    // Logged only on change, not every recomposition -- lets `adb logcat -s Hermex/Composer`
    // confirm Scaffold's innerPadding is actually reserving this much space for the transcript
    // (see ChatScreen investigation notes on composer/content overlap).
    var lastLoggedHeightPx by remember { mutableStateOf(-1) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val heightPx = coordinates.size.height
                if (heightPx != lastLoggedHeightPx) {
                    lastLoggedHeightPx = heightPx
                    HermexLog.d("Composer", "measured height=${heightPx}px")
                }
            },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = HermexRadii.SettingsCard, topEnd = HermexRadii.SettingsCard),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            // Hairline separating the dock from the message list above -- the app-wide substitute
            // for wrapping the whole (now asymmetrically-rounded) shape in a border.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)

            if (attachmentState.pendingAttachments.isNotEmpty()) {
                PendingAttachmentStrip(
                    attachments = attachmentState.pendingAttachments,
                    onRemove = actions.onRemoveAttachment,
                )
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = composerState.text,
                        onValueChange = actions.onTextChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Hermex…") },
                        enabled = composerState.isTextFieldEnabled,
                        maxLines = 5,
                        shape = RoundedCornerShape(HermexRadii.Composer),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Fixed-size slot for the trailing action -- Stop/Send (both IconButton-family,
                    // 48dp by default) and the bare sending spinner previously had no shared box, so
                    // the control visibly jumped size as the composer moved between states.
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
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
                Spacer(Modifier.height(8.dp))
                // The bottom control strip -- Hermex-styled chips for Attach/Profile/Model, each
                // mapping to a real existing action, scrollable so it never clips or wraps
                // awkwardly on narrow phones.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                }
            }
        }
    }
}

/** Shared visual container for the composer's bottom control strip -- a small tonal pill with an
 * icon and an optional label, so Attach/Profile/Model all read as one family of "Hermex chips"
 * instead of bare icons. [onClick] is required: every chip here must map to a real, already-
 * existing action -- a chip that looks tappable but does nothing is worse than no chip at all. */
@Composable
private fun ComposerChip(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val chipContent: @Composable RowScope.() -> Unit = {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                icon,
                contentDescription = if (label == null) contentDescription else null,
                modifier = Modifier.size(15.dp),
                tint = contentColor,
            )
        }
        if (label != null) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
    Surface(
        modifier = Modifier.clickable(enabled = enabled && !isLoading, onClick = onClick),
        shape = RoundedCornerShape(HermexRadii.Accessory),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label != null) 10.dp else 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = chipContent,
        )
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

    ComposerChip(
        icon = Icons.Filled.Add,
        label = null,
        contentDescription = "Attach file",
        enabled = enabled,
        isLoading = isUploading,
        onClick = { launcher.launch(arrayOf("*/*")) },
    )
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
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
    val selectedLabel = profileOptions.firstOrNull { it.normalizedName == selectedProfileName }?.displayName

    Box {
        ComposerChip(
            icon = Icons.Filled.Person,
            label = selectedLabel,
            contentDescription = "Profile: ${selectedProfileName ?: "none"}",
            enabled = profileOptions.isNotEmpty(),
            isLoading = isSwitchingProfile,
            onClick = { menuExpanded = true },
        )
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

    Box {
        ComposerChip(
            icon = Icons.Filled.Settings,
            label = currentModel,
            contentDescription = "Model: ${currentModel ?: "none"}",
            isLoading = isUpdatingComposerConfiguration,
            onClick = {
                menuExpanded = true
                onOpenModelPicker()
            },
        )
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
}

