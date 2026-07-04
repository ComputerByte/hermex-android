package com.hermex.android.chat

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

/**
 * Renders [markdown] as styled text using Markwon. Wraps a [TextView] via [AndroidView] so that
 * Markwon's Spannable rendering works (it operates on Android [android.text.Spanned] directly).
 *
 * [textColor] should come from the enclosing bubble's theme color so the Markdown text respects
 * the user/assistant color scheme.
 *
 * The [Markwon] instance is created once per composition and reused for all re-renders to avoid
 * recreating the plugin set on every recomposition.
 *
 * Supported GFM features: bold, italic, inline code, fenced code blocks, bullet/numbered lists,
 * links, strikethrough, and tables (via [TablePlugin]).
 */
@Composable
fun MarkdownText(
    markdown: String,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 14f // sp, matches MaterialTheme.typography.bodyMedium
                setLineSpacing(0f, 1.35f)
                setPadding(0, 0, 0, 0)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            markwon.setMarkdown(textView, markdown)
        },
    )
}
