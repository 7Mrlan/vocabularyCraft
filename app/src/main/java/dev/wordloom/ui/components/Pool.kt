package dev.wordloom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.wordloom.ui.theme.WordloomTheme

/**
 * The shallow end of the home hero: a navy-to-blue field that ends in the same
 * wave we use between bands on the home screen. Search and lists sit inside it,
 * so those pages keep the app's atmosphere instead of opening on bare white.
 *
 * [content] should leave ~34dp at the bottom for the wave.
 */
@Composable
fun PoolHeader(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val wl = WordloomTheme.colors
    val top = if (wl.isDark) Color(0xFF04101F) else Color(0xFF061B45)
    val bottom = if (wl.isDark) Color(0xFF0B2A5B) else Color(0xFF0C3B9E)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .statusBarsPadding(),
            content = content,
        )
        WaveDivider(
            color = wl.surface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(26.dp),
        )
    }
}
