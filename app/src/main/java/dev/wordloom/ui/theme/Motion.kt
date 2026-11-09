package dev.wordloom.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * The only three motions the app is allowed to use, taken from the M3 Expressive
 * physics system: movement and effects are separate, and both come from a spring
 * so every transition shares one feel. Compose multiplies these by the system's
 * animator duration scale, so "remove animations" turns them off for free.
 */
object WlMotion {
    /** Small surfaces: press states, chips, icon swaps. */
    val spatialFast: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = 800f)

    /** Pages and cards: the default travel. */
    val spatialDefault: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.75f, stiffness = 350f)

    /** Same spring, for offset-based transitions (navigation slides). */
    val spatialDefaultOffset: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 0.75f, stiffness = 350f)

    /** Navigation: critically damped so a page never overshoots and bounces back. */
    val navOffset: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 1f, stiffness = 420f)

    /** Colour, alpha, cross-fades — critically damped, no overshoot. */
    val effectsFast: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)
}
