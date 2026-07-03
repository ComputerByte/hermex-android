package com.hermex.android.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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

/** What "copy message" actually puts on the clipboard -- pulled out as its own function so the
 * null-content case (a tool/system message with no plain text) is unit-testable without needing
 * a Compose UI test harness, which this project doesn't have. */
fun copyableTextFor(message: ChatMessage): String = message.content.orEmpty()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 320.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                )
                // Long-press to copy -- a simple, discoverable single action rather than a full
                // context menu, matching the scope of this pass (see AGENTS.md/v0.3.0 spec).
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(copyableTextFor(message)))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.content.orEmpty(),
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bound to [ChatUiState.streamingText] -- rendered like an assistant bubble while a reply is
 * still arriving. No markdown/syntax highlighting in this MVP (see API_CONTRACT.md scope). */
@Composable
fun StreamingBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 320.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
