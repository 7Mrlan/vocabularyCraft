package dev.wordloom.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The status bar is hidden for the whole app (see MainActivity). Content must
 * still clear a notch or a dynamic island, and devices without a cutout still
 * need a small breathing gap instead of touching the glass edge.
 */
@Composable
fun Modifier.immersiveTopPadding(min: Dp = 10.dp): Modifier {
    val cutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    return padding(top = max(cutout.value, min.value).dp)
}
