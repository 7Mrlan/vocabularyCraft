package dev.wordloom.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.wordloom.R

/**
 * Short, clear answers to a tap: a two-note chime on a correct pick, and the
 * platform's own CONFIRM / REJECT haptics. Constants are used instead of a
 * hand-rolled vibration so the effect matches the device and the user's settings.
 */
class AppFeedback(context: Context) {

    private val sounds = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val correctChime = sounds.load(context, R.raw.quiz_correct, 1)

    fun correct(view: View?) {
        sounds.play(correctChime, 0.55f, 0.55f, 1, 0, 1f)
        view.haptic(HapticFeedbackConstants.CONFIRM)
    }

    fun wrong(view: View?) {
        view.haptic(HapticFeedbackConstants.REJECT)
    }

    /** A page turned: lighter than a confirmation, heavier than a tick. */
    fun reveal(view: View?) {
        view.haptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun release() {
        sounds.release()
    }
}

private fun View?.haptic(constant: Int) {
    val v = this ?: return
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) constant
    else HapticFeedbackConstants.LONG_PRESS
    v.performHapticFeedback(effect)
}

@Composable
fun rememberAppFeedback(): AppFeedback {
    val context = LocalContext.current
    val feedback = remember { AppFeedback(context) }
    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }
    return feedback
}
