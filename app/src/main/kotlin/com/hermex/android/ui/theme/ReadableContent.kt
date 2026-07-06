package com.hermex.android.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Caps how wide plain right-pane content (lists, detail forms, settings sections) ever gets, so
 * a large tablet's wide-layout right pane doesn't stretch it edge-to-edge into an uncomfortably
 * wide read. Never binds on phone-scale widths -- harmless there. Matches the composer's own cap
 * from the chat pane polish pass. */
val HermexReadableContentMaxWidth: Dp = 840.dp

/**
 * Drop-in replacement for a top-level `Box(modifier = Modifier.fillMaxSize().padding(innerPadding))`
 * used as a Scaffold's content root: same `BoxScope` content lambda (so any existing
 * `Modifier.align(...)` usage inside still works unchanged), but caps and centers that content at
 * [maxWidth] rather than always stretching to the full available width. On phone-scale widths
 * (compact, or the adaptive shell's ~400dp right pane) the cap never binds, so this renders
 * identically to a plain `Box` there.
 */
@Composable
fun HermexReadableContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = HermexReadableContentMaxWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // fillMaxWidth() must NOT precede widthIn(max=...) here: fillMaxWidth() pins both min and
        // max width to the full available space, and widthIn's max can only narrow within the
        // incoming range -- so a max below an already-pinned min gets silently overridden back up
        // to full width, defeating the cap entirely on any pane wider than `maxWidth`.
        Box(modifier = Modifier.widthIn(max = maxWidth)) {
            content()
        }
    }
}
