package dev.wordloom.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/**
 * The app answers a touch with motion (a small scale step), never with a grey
 * rectangle. Material's ripple paints a tinted box behind text that doesn't
 * match this paper-and-ink surface, so it is switched off everywhere and the
 * press feedback lives in [wlPress].
 *
 * An indication is just decoration drawn on top of the content; a node that
 * draws nothing is the whole implementation.
 */
object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = EmptyNode()

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = "NoIndication".hashCode()

    private class EmptyNode : Modifier.Node()
}
