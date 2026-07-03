package com.hermex.android.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    // enableEdgeToEdge() (MainActivity) draws the app behind the system navigation bar, so a
    // bottom-docked bar like this one must explicitly reserve space for it -- otherwise the
    // send button/text field render underneath the system's back/home/recents buttons.
    // Deliberately NOT also adding imePadding() here: Scaffold's own default
    // contentWindowInsets (WindowInsets.safeDrawing, which includes ime) already accounts for
    // the keyboard once, so stacking an explicit imePadding() on top double-counted the
    // keyboard height and left a large blank gap above it when the keyboard opened.
    Surface(
        modifier = modifier.navigationBarsPadding(),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                enabled = !isSending && !isStreaming,
                maxLines = 5,
            )
            when {
                isStreaming -> IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop")
                }
                isSending -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
