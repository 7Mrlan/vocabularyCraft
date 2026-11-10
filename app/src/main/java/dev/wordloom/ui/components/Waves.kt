package dev.wordloom.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * A silky band edge: the curve is sampled from a sine so every segment turns
 * with the same radius — no cusps, no visible seam.
 *
 * [flipped] = false fills the colour across the bottom of the canvas (wave on
 * top); true fills across the top (wave on the bottom).
 */
@Composable
fun WaveDivider(
    color: Color,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    amplitude: Float = 0.26f,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseY = if (flipped) h * 0.46f else h * 0.54f
        val amp = h * amplitude
        val steps = 220

        val path = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = w * t
            // One full crest and trough across the band, easing in at both ends.
            val wave = sin(2.0 * PI * t - PI / 2).toFloat()
            val y = baseY - amp * wave * easeEnds(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (flipped) {
            path.lineTo(w, 0f)
            path.lineTo(0f, 0f)
        } else {
            path.lineTo(w, h)
            path.lineTo(0f, h)
        }
        path.close()
        drawPath(path, color)
    }
}

/** Softens the crest near x = 0 and x = width so the band meets the edges flat. */
private fun easeEnds(t: Float): Float {
    val edge = 0.10f
    return when {
        t < edge -> 0.55f + 0.45f * (t / edge)
        t > 1f - edge -> 0.55f + 0.45f * ((1f - t) / edge)
        else -> 1f
    }
}

/**
 * Ripples spreading from the search field: concentric rings in thin white
 * strokes. Vector, quiet, and it says "sound / a word being spoken" rather
 * than decorating for its own sake.
 */
fun DrawScope.drawSearchRipples(center: Offset) {
    val stroke = 1.4f * density
    val radii = listOf(58f, 104f, 150f, 196f, 242f)
    radii.forEachIndexed { index, dp ->
        drawCircle(
            color = Color.White.copy(alpha = 0.16f - index * 0.025f),
            radius = dp * density,
            center = center,
            style = Stroke(width = stroke),
        )
    }
}
