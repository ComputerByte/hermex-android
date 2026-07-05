package com.hermex.android.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermex.android.core.network.dto.SessionSummary
import com.hermex.android.ui.theme.HermexColors
import com.hermex.android.ui.theme.HermexRadii
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionRow(
    session: SessionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val streamingColor = if (isSystemInDarkTheme()) HermexColors.SuccessDark else HermexColors.SuccessLight

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(HermexRadii.Cell),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.pinned == true) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = session.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            supportingContent = {
                val subtitle = listOfNotNull(session.model, session.workspace).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    session.lastMessageAt?.let {
                        Text(
                            relativeTimeText(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (session.isStreaming == true) {
                        Spacer(Modifier.width(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(streamingColor, CircleShape),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Live",
                                style = MaterialTheme.typography.labelSmall,
                                color = streamingColor,
                            )
                        }
                    }
                }
            },
        )
    }
}

/** A small local relative-time formatter -- MVP scope has no need for a date-formatting
 * dependency for this one label. */
internal fun relativeTimeText(epochSeconds: Double): String {
    val nowSeconds = System.currentTimeMillis() / 1000.0
    val diffSeconds = (nowSeconds - epochSeconds).coerceAtLeast(0.0)
    return when {
        diffSeconds < 60 -> "Just now"
        diffSeconds < 3600 -> "${(diffSeconds / 60).toInt()}m ago"
        diffSeconds < 86_400 -> "${(diffSeconds / 3600).toInt()}h ago"
        diffSeconds < 604_800 -> "${(diffSeconds / 86_400).toInt()}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date((epochSeconds * 1000).toLong()))
    }
}
