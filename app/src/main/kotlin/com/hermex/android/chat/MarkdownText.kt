package com.hermex.android.chat

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.hermex.android.ui.theme.HermexColors
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

/**
 * Renders [markdown] as styled text using Markwon. Wraps a [TextView] via [AndroidView] so that
 * Markwon's Spannable rendering works (it operates on Android [android.text.Spanned] directly).
 *
 * [textColor] should come from the enclosing bubble's theme color so the Markdown text respects
 * the user/assistant color scheme.
 *
 * The [Markwon] instance (including its [MarkwonTheme] colors, captured from [MaterialTheme] at
 * creation time) is created once per composition and reused for all re-renders to avoid
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
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val quoteColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f).toArgb()
    val codeBackgroundColor = HermexColors.CodeBackgroundDark.toArgb()
    val codeTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    // Un-themed Markwon defaults don't pick up the app's dark palette --
                    // code/links/quotes rendered as plain text with no visual distinction from
                    // surrounding prose. HermexColors.CodeBackgroundDark already exists in the
                    // theme for exactly this (previously unused).
                    builder
                        .codeTextColor(codeTextColor)
                        .codeBlockTextColor(codeTextColor)
                        .codeBackgroundColor(codeBackgroundColor)
                        .codeBlockBackgroundColor(codeBackgroundColor)
                        .linkColor(linkColor)
                        .blockQuoteColor(quoteColor)
                }
            })
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
