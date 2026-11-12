package dev.wordloom.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf
import dev.wordloom.data.tts.SpeechState

/**
 * The study card's only audio entry point. When AI TTS is enabled every word
 * and example goes through [speak]; [fallbackUrl] is kept for old human/Youdao
 * clips when synthesis fails or when AI is not configured.
 */
data class SpeechActions(
    val enabled: Boolean,
    val speak: (text: String, fallbackUrl: String?, onState: (SpeechState) -> Unit) -> Unit,
    val prefetch: (text: String) -> Unit,
)

val LocalSpeechActions = staticCompositionLocalOf<SpeechActions?> { null }
