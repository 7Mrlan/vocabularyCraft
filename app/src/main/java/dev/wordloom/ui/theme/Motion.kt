package dev.wordloom.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One spring, for the one place motion earns its keep: a card sinking under the
 * finger. Everything else switches instantly — page changes, question changes and
 * card advances all felt slower with a transition than without one.
 */
object WlMotion {
    val press: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = 800f)
}

/**
 * The app's only press feedback: the target sinks a hair and, for text-sized
 * targets, dims. No ripple, no tinted rectangle — on paper, a touch should move
 * the ink, not paint a box.
 *
 * Large paper cards pass [dim] = false and a shallower [scale]: dimming a whole
 * white card reads as a flash, while a text button needs the extra weight.
 */
@Composable
fun Modifier.wlPress(
    enabled: Boolean = true,
    scale: Float = 0.96f,
    dim: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = WlMotion.press,
        label = "wlPress",
    )
    return this
        .graphicsLayer {
            scaleX = pressedScale
            scaleY = pressedScale
            alpha = if (pressed && dim) 0.72f else 1f
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
