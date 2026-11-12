package dev.wordloom.ui.voice

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.wordloom.R
import dev.wordloom.data.tts.SpeechState
import dev.wordloom.ui.components.rememberAudioPlayer
import dev.wordloom.ui.theme.WlMotion
import dev.wordloom.ui.theme.WordloomTheme

/**
 * The study card's speaker. It prefers AI TTS through [LocalSpeechActions] and
 * falls back to the old URL clip when AI is not configured or fails.
 *
 * The playing pulse is read inside graphicsLayer, so a 480 ms breathing loop
 * never recomposes this button or competes with a horizontal swipe.
 */
@Composable
fun SpeechButton(
    text: String,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
) {
    val wl = WordloomTheme.colors
    val speech = LocalSpeechActions.current
    val player = rememberAudioPlayer()
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = WlMotion.press,
        label = "speechPress",
    )
    val pulse = if (playing) {
        val transition = rememberInfiniteTransition(label = "speechPlaying")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "speechWave",
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .graphicsLayer {
                val scale = pressScale * (pulse?.value ?: 1f)
                scaleX = scale
                scaleY = scale
            }
            .background(
                if (playing) wl.ink.copy(alpha = 0.10f) else wl.surfaceAlt,
                CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                if (speech != null && speech.enabled) {
                    playing = true
                    speech.speak(text, fallbackUrl) { state ->
                        playing = state is SpeechState.Playing
                        if (state is SpeechState.Failed) {
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val url = fallbackUrl?.takeIf { it.isNotBlank() } ?: return@clickable
                    playing = true
                    player.play(url) { playing = false }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.wl_volume),
            contentDescription = "播放发音",
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp),
        )
    }
}
