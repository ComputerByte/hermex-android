package com.hermex.android.sessions

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.hermex.android.core.network.dto.SessionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionRow(
    session: SessionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = session.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val subtitle = listOfNotNull(session.model, session.workspace).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            session.lastMessageAt?.let { Text(relativeTimeText(it)) }
        },
    )
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
