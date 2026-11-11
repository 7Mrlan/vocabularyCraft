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
 * The app's only press feedback: the target sinks a hair and dims. No ripple,
 * no tinted rectangle — on paper, a touch should move the ink, not paint a box.
 */
@Composable
fun Modifier.wlPress(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = WlMotion.press,
        label = "wlPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (pressed) 0.72f else 1f
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
