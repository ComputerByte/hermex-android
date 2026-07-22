package com.hermex.android.chat

import android.text.Spannable
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.MetricAffectingSpan
import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hermex.android.core.font.resolveAndroidTypeface
import com.hermex.android.ui.theme.HermexColors
import com.hermex.android.ui.theme.LocalMonospaceFontFamily
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.core.spans.CodeSpan
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
 *
 * Monospace font family is read from [LocalMonospaceFontFamily] and applied to inline code spans
 * and fenced code blocks after Markwon renders them.
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
    val monospaceFontFamily = LocalMonospaceFontFamily.current
    val monospaceTypeface = resolveAndroidTypeface(monospaceFontFamily)
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
            // Apply the user's monospace typeface to code spans after Markwon renders.
            applyMonospaceToCodeSpans(textView, monospaceTypeface)
        },
    )
}

/** Walks the TextView's Spannable and applies [typeface] to all [CodeSpan] and
 * [CodeBlockSpan] regions left by Markwon. */
private fun applyMonospaceToCodeSpans(textView: TextView, typeface: Typeface?) {
    if (typeface == null) return
    val text = textView.text
    if (text !is Spannable) return

    // Collect code spans first to avoid ConcurrentModification-like issues from
    // setSpan inside getSpans iteration.
    val codeRegions = mutableListOf<Pair<Int, Int>>()
    // Inline code spans
    val codeSpans = text.getSpans(0, text.length, CodeSpan::class.java)
    for (span in codeSpans) {
        codeRegions.add(text.getSpanStart(span) to text.getSpanEnd(span))
    }
    // Fenced / indented code blocks
    val blockSpans = text.getSpans(0, text.length, CodeBlockSpan::class.java)
    for (span in blockSpans) {
        codeRegions.add(text.getSpanStart(span) to text.getSpanEnd(span))
    }

    for ((start, end) in codeRegions) {
        text.setSpan(
            CodeTypefaceSpan(typeface),
            start, end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

/** A [MetricAffectingSpan] that applies a custom [Typeface] to the spanned text. */
private class CodeTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.typeface = typeface }
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = typeface }
}
