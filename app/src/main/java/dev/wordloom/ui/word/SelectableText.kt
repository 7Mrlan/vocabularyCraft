package dev.wordloom.ui.word

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.wordloom.ui.theme.WordloomTheme

/**
 * A paragraph you can hold and drag across. Long-press snaps to the word under the
 * finger (system long-press haptic), dragging extends word by word with a light
 * tick per new word. On release it reports the selected text and where it sits, so
 * the page can place its translation card there instead of covering the page with
 * a sheet.
 */
@Composable
fun SelectableText(
    text: String,
    style: TextStyle,
    color: Color,
    onSelection: (String, Rect) -> Unit,
    resetKey: Long,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var selection by remember(text) { mutableStateOf<IntRange?>(null) }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember(text) { mutableStateOf(Offset.Zero) }
    var lastWordStart by remember(text) { mutableStateOf(-1) }

    LaunchedEffect(resetKey) { selection = null }

    // Ink, not the app blue: selection is a pencil mark on paper.
    val highlight = WordloomTheme.colors.ink.copy(alpha = 0.14f)
    val annotated = remember(text, selection, highlight) {
        buildAnnotatedString {
            append(text)
            val r = selection
            if (r != null && r.first < r.last) {
                addStyle(SpanStyle(background = highlight), r.first, r.last.coerceAtMost(text.length))
            }
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        Text(
            text = annotated,
            style = style.copy(color = color),
            onTextLayout = { layout = it },
            modifier = Modifier.pointerInput(text) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val l = layout ?: return@detectDragGesturesAfterLongPress
                        val range = wordRangeAt(text, l.getOffsetForPosition(offset))
                        selection = range
                        lastWordStart = range.first
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    },
                    onDrag = { change, _ ->
                        val l = layout ?: return@detectDragGesturesAfterLongPress
                        val current = selection ?: return@detectDragGesturesAfterLongPress
                        val word = wordRangeAt(text, l.getOffsetForPosition(change.position))
                        val merged = minOf(current.first, word.first)..maxOf(current.last, word.last)
                        if (merged != current) {
                            selection = merged
                            if (word.first != lastWordStart) {
                                lastWordStart = word.first
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        val l = layout ?: return@detectDragGesturesAfterLongPress
                        val range = selection ?: return@detectDragGesturesAfterLongPress
                        val chosen = text.substring(range.first, range.last).trim()
                        if (chosen.isNotEmpty()) {
                            val box = l.getBoundingBox(range.first)
                            onSelection(chosen, box.translate(origin))
                        }
                    },
                )
            },
        )
    }
}

/** Expands a character offset to the whole word it sits in. */
private fun wordRangeAt(text: String, offset: Int): IntRange {
    if (text.isEmpty()) return 0..0
    var index = offset.coerceIn(0, text.length - 1)
    if (index >= text.length - 1 && !text[index].isWordChar()) index = (index - 1).coerceAtLeast(0)

    var start = index
    while (start > 0 && text[start - 1].isWordChar()) start--
    var end = index
    while (end < text.length && text[end].isWordChar()) end++
    if (start == end) end = (start + 1).coerceAtMost(text.length)
    return start..end
}

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '\'' || this == '-' || this == '_'
