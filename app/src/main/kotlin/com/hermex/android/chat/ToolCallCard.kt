package com.hermex.android.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermex.android.ui.theme.HermexRadii

/** [name]/[preview]/status only in this MVP -- no raw args display (see API_CONTRACT.md scope).
 * Collapsed by default (matching [ReasoningBlock]): the status icon and name stay visible so the
 * user can always tell a tool ran, but [preview]/[durationSeconds] are hidden until tapped.
 * Styled as a first-class Hermex surface (bordered, rounded via the shared [HermexRadii.Cell]
 * token) to match the app-wide card language rather than a bare colored background. */
@Composable
fun ToolCallCard(
    toolCall: ToolCallUi,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(HermexRadii.Cell),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                !toolCall.isComplete -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                toolCall.isError -> Icon(
                    Icons.Filled.Warning,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                else -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = toolCall.name?.takeIf { it.isNotBlank() } ?: "Tool call",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (expanded) {
                    toolCall.preview?.takeIf { it.isNotBlank() }?.let { preview ->
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    toolCall.durationSeconds?.let { duration ->
                        Text(
                            text = "%.1fs".format(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
