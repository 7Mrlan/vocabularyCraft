package dev.wordloom.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import kotlin.math.cos
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
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
 * The decorative corner: a dense cluster of overlapping discs with letterforms
 * half-hidden behind them. Rebuilt as vector shapes rather than a bitmap so it
 * stays crisp at any density and carries no third-party artwork.
 */
@Composable
fun HeroRosette(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) { drawHeroRosette(measurer, scale = 1.0f) }
}

/**
 * Draws the rosette straight into the hero's background layer, so it never
 * contributes height: the hero is as tall as its content, and the flower fills
 * exactly that box with the search pill crossing its middle.
 *
 * One centre disc (larger) plus eight petals at 45°, colours mirrored across
 * both axes; four diagonal petals carry a glyph clipped to the petal.
 */
fun DrawScope.drawHeroRosette(measurer: TextMeasurer, scale: Float = 1.0f) {
    val w = size.width
    val h = size.height
    val u = h
    val cx = w / 2f
    val cy = h / 2f

    val navy = Color(0xFF0B3AA6)
    val mid = Color(0xFF1557D6)
    val blue = Color(0xFF1E63E8)
    val bright = Color(0xFF2F7BF6)
    val sky = Color(0xFF4EA0FF)
    val pale = Color(0xFF69B4FF)
    val cyan = Color(0xFF57C8F5)
    val teal = Color(0xFF2FBFA6)
    val green = Color(0xFF1E9E64)

    val centerR = 0.300f * u
    val petalR = 0.175f * u
    val ring = 0.270f * u

    val petalColors = listOf(mid, cyan, pale, cyan, mid, sky, pale, sky)
    val glyphs = mapOf(
        1 to Triple("A", Color.White, cyan),
        3 to Triple("PP", green, cyan),
        5 to Triple("B", navy, sky),
        7 to Triple("A", navy, sky),
    )

    drawCircle(bright, centerR, Offset(cx, cy))

    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val center = Offset(
            cx + (cos(a) * ring).toFloat(),
            cy + (kotlin.math.sin(a) * ring).toFloat(),
        )
        drawCircle(petalColors[i], petalR, center)

        val g = glyphs[i] ?: continue
        val layout = measurer.measure(
            text = AnnotatedString(g.first),
            style = TextStyle(
                fontSize = (2.30f * petalR / density).sp,
                fontWeight = FontWeight.ExtraBold,
                color = g.second,
            ),
        )
        clipPath(Path().apply { addOval(Rect(center, petalR)) }) {
            drawText(
                layout,
                topLeft = Offset(
                    center.x - layout.size.width / 2f,
                    center.y - layout.size.height / 2f,
                ),
            )
        }
    }

    drawCircle(blue, centerR * 0.55f, Offset(cx, cy))
    drawCircle(
        color = Color.White.copy(alpha = 0.12f),
        radius = centerR * 0.55f,
        center = Offset(cx, cy),
        style = Stroke(width = u * 0.002f),
    )
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
