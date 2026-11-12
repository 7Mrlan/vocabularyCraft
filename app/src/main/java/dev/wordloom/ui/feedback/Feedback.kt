package dev.wordloom.ui.feedback

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.wordloom.data.audio.Sound
import dev.wordloom.data.audio.SoundService

/**
 * Short, clear answers to a tap: a two-note chime on a correct pick, and the
 * platform's own CONFIRM / REJECT haptics. Constants are used instead of a
 * hand-rolled vibration so the effect matches the device and the user's settings.
 */
class AppFeedback(context: Context) {

    private val sounds = SoundService.shared(context)

    fun correct(view: View?) {
        sounds.play(Sound.QuizCorrect)
        view.haptic(HapticFeedbackConstants.CONFIRM)
    }

    fun wrong(view: View?) {
        view.haptic(HapticFeedbackConstants.REJECT)
    }

    /** Committing a choice: the same weight as a system confirmation, no sound. */
    fun commit(view: View?) {
        view.haptic(HapticFeedbackConstants.CONFIRM)
    }

    /** A page turned: lighter than a confirmation, heavier than a tick. */
    fun reveal(view: View?) {
        view.haptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun release() {
        // The shared SoundService is process-scoped; screens do not own it.
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
