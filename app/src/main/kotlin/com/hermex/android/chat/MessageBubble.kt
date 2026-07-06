package com.hermex.android.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.MessageAttachment
import com.hermex.android.core.network.dto.fileTypeIcon
import com.hermex.android.core.network.dto.stripAttachedFilesMarker
import com.hermex.android.chat.AttachmentFileOpener
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
    val displayContent = stripAttachedFilesMarker(message.content)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showImageViewer by remember { mutableStateOf<String?>(null) }
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
                        markdown = displayContent.orEmpty(),
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                // Attachment chips
                message.attachments?.let { attachments ->
                    AttachmentChips(attachments = attachments, context = context)
                }
                // Image attachment thumbnails
                message.attachments?.forEach { attachment ->
                    if (attachment.mime?.startsWith("image/") == true && attachment.path != null) {
                        val imageUrl = attachment.path
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = attachment.name ?: "Image",
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .widthIn(max = 260.dp)
                                .clip(RoundedCornerShape(HermexRadii.Accessory))
                                .clickable { showImageViewer = imageUrl },
                            contentScale = ContentScale.FillWidth,
                        )
                    }
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
                markdown = displayContent.orEmpty(),
                textColor = MaterialTheme.colorScheme.onSurface,
            )
            // Attachment chips
            message.attachments?.let { attachments ->
                AttachmentChips(attachments = attachments, context = context)
            }
            // Image attachment thumbnails
            message.attachments?.forEach { attachment ->
                if (attachment.mime?.startsWith("image/") == true && attachment.path != null) {
                    val imageUrl = attachment.path
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = attachment.name ?: "Image",
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(HermexRadii.Accessory))
                            .clickable { showImageViewer = imageUrl },
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
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

    // Full-screen image viewer
    showImageViewer?.let { url ->
        ImageViewer(
            imageUrl = url,
            onDismiss = { showImageViewer = null },
        )
    }
}

/** A short clock-time caption under each bubble, mirroring the design system's per-turn
 * timestamp -- distinct from the session list's relative "Xh ago", which serves a different
 * purpose (recency at a glance) rather than pinpointing when a specific message was sent. */
private fun messageTimeText(epochSeconds: Double): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date((epochSeconds * 1000).toLong()))

@Composable
private fun AttachmentChips(
    attachments: List<MessageAttachment>,
    context: android.content.Context,
) {
    if (attachments.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.path ?: it.name ?: it.hashCode().toString() }) { attachment ->
            AttachmentChip(attachment = attachment, context = context)
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: MessageAttachment,
    context: android.content.Context,
) {
    Surface(
        onClick = {
            AttachmentFileOpener.openAttachment(context, attachment)
        },
        shape = RoundedCornerShape(HermexRadii.Accessory),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = fileTypeIcon(attachment.mime),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = attachment.name ?: attachment.path ?: "file",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
