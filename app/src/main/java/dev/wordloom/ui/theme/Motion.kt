package dev.wordloom.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * One spring, for the one place motion earns its keep: a card sinking under the
 * finger. Everything else switches instantly — page changes, question changes and
 * card advances all felt slower with a transition than without one.
 */
object WlMotion {
    val press: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = 800f)
}
