package com.hermex.android.core.font

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermex.android.ui.theme.HermexRadii

/**
 * Reusable font picker dialog. Two modes determined by the text field:
 *
 * - **Empty** — shows system font options filtered by [isMonoPicker].
 * - **Non-empty** — hides system fonts, shows the typed name as a Google Font candidate.
 *
 * @param title Dialog title (e.g. "UI Font" or "Monospace Font").
 * @param currentKey The currently selected [FontFamilyOption.storageKey].
 * @param isMonoPicker If true, show monospace system fonts; otherwise UI system fonts.
 * @param onSelect Called with the chosen storage key when the user taps a row or the Google
 *                 Font field and hits "Apply".
 * @param onDismiss Close callback.
 */
@Composable
fun FontPickerDialog(
    title: String,
    currentKey: String,
    isMonoPicker: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val systemOptions = remember { FontFamilyOption.systemOptions(isMonoPicker) }
    var googleFontQuery by remember { mutableStateOf("") }
    var appliedSelection by remember { mutableStateOf(currentKey) }

    // Reflect the currentKey if it's a Google font
    LaunchedEffect(currentKey) {
        val current = FontFamilyOption.fromStorageKey(currentKey)
        if (current is FontFamilyOption.GoogleFont) {
            googleFontQuery = current.name
        }
    }

    val showSystem = googleFontQuery.isBlank()
    val googleFontOption = if (googleFontQuery.isNotBlank()) {
        FontFamilyOption.GoogleFont(googleFontQuery.trim())
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Always visible Google Font text field
                OutlinedTextField(
                    value = googleFontQuery,
                    onValueChange = { googleFontQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search or enter a Google Font name…") },
                    shape = RoundedCornerShape(HermexRadii.Accessory),
                )

                Spacer(Modifier.height(12.dp))

                // ── System fonts section (visible only when no Google Font typed) ──
                if (showSystem) {
                    systemOptions.forEach { option ->
                        FontOptionRow(
                            option = option,
                            isSelected = appliedSelection == option.storageKey,
                            onClick = {
                                appliedSelection = option.storageKey
                                onSelect(option.storageKey)
                            },
                        )
                    }
                }

                // ── Google Font preview (visible when text is typed) ──
                if (googleFontOption != null) {
                    FontOptionRow(
                        option = googleFontOption,
                        isSelected = appliedSelection == googleFontOption.storageKey,
                        onClick = {
                            appliedSelection = googleFontOption.storageKey
                            onSelect(googleFontOption.storageKey)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalKey = if (googleFontOption != null && showSystem) {
                    // Google Font typed, but user may have clicked a system option or the
                    // Google Font option. Use appliedSelection as-is.
                    appliedSelection
                } else {
                    appliedSelection
                }
                onSelect(finalKey)
                onDismiss()
            }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun FontOptionRow(
    option: FontFamilyOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Spacer to reserve the check icon width so the text doesn't shift when selected
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
            } else {
                Spacer(Modifier.width(24.dp))
            }

            Column {
                Text(
                    text = option.displayName,
                    style = if (isSelected) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when (option) {
                        is FontFamilyOption.GoogleFont -> "Google Font"
                        is FontFamilyOption.SystemDefault -> "Platform default"
                        is FontFamilyOption.SystemFont -> "System font"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
