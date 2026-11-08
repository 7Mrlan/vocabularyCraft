package dev.wordloom.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The reference map is not a natural basemap: it is washed in a blue duotone,
 * lighter at the top, deeper at the edges, so it merges into the blue section.
 * One translucent gradient over the tiles gets the same read without touching
 * the style JSON.
 */
@Composable
fun MapTint(modifier: Modifier = Modifier, strength: Float = 1f) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color(0xFF7FC4FF).copy(alpha = 0.30f * strength),
                0.34f to Color(0xFF2E8BF7).copy(alpha = 0.24f * strength),
                0.70f to Color(0xFF1266FE).copy(alpha = 0.26f * strength),
                1f to Color(0xFF0A3EA8).copy(alpha = 0.42f * strength),
            )
        )
    )
}
