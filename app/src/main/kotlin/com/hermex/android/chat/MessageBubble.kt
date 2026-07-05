package com.hermex.android.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.ui.theme.HermexRadii
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What "copy message" actually puts on the clipboard -- pulled out as its own function so the
 * null-content case (a tool/system message with no plain text) is unit-testable without needing
 * a Compose UI test harness, which this project doesn't have. */
fun copyableTextFor(message: ChatMessage): String = message.content.orEmpty()

/** Per the design system's `MessageBubble` spec: user turns get a right-aligned gray bubble;
 * assistant turns are plain full-width prose -- never bubbled. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyOnLongClick = Modifier.combinedClickable(
        onClick = {},
        onLongClick = {
            clipboardManager.setText(AnnotatedString(copyableTextFor(message)))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        },
    )
    if (isUser) {
        Box(modifier = modifier.fillMaxWidth().padding(start = 32.dp)) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = 320.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(HermexRadii.Bubble),
                        )
                        // Long-press to copy -- a simple, discoverable single action rather than a
                        // full context menu, matching the scope of this pass (see AGENTS.md/v0.3.0
                        // spec).
                        .then(copyOnLongClick)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    MarkdownText(
                        markdown = message.content.orEmpty(),
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                message.effectiveTimestamp?.let { timestamp ->
                    Text(
                        text = messageTimeText(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp, end = 4.dp),
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(copyOnLongClick)
                .padding(end = 48.dp, top = 2.dp, bottom = 2.dp),
        ) {
            MarkdownText(
                markdown = message.content.orEmpty(),
                textColor = MaterialTheme.colorScheme.onSurface,
            )
            message.effectiveTimestamp?.let { timestamp ->
                Text(
                    text = messageTimeText(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** A short clock-time caption under each bubble, mirroring the design system's per-turn
 * timestamp -- distinct from the session list's relative "Xh ago", which serves a different
 * purpose (recency at a glance) rather than pinpointing when a specific message was sent. */
private fun messageTimeText(epochSeconds: Double): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date((epochSeconds * 1000).toLong()))

/** Bound to [ChatUiState.streamingText] -- rendered like an in-flight assistant reply, so it must
 * match [MessageBubble]'s plain (unbubbled) assistant styling exactly: otherwise the bubble
 * chrome would visibly pop away the instant streaming finalizes into a real message. Markdown
 * supported via [MarkdownText]. */
@Composable
fun StreamingBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 48.dp, top = 2.dp, bottom = 2.dp),
    ) {
        MarkdownText(markdown = text, textColor = MaterialTheme.colorScheme.onSurface)
    }
}
