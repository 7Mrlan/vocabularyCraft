package dev.wordloom.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf
import dev.wordloom.data.tts.SpeechState
import dev.wordloom.data.tts.WordHint

/**
 * The study card's only audio entry point. When AI TTS is enabled every word
 * and example goes through [speak]; [fallbackUrl] is kept for old human/Youdao
 * clips when synthesis fails or when AI is not configured.
 *
 * [speakWord] carries the per-word director's note (sense and IPA) that a
 * sentence already has in its own context. Callers that only have a string
 * keep using [speak] and the defaults degrade to it.
 */
data class SpeechActions(
    val enabled: Boolean,
    val speak: (text: String, fallbackUrl: String?, onState: (SpeechState) -> Unit) -> Unit,
    val prefetch: (text: String) -> Unit,
    val speakWord: (
        word: String,
        hint: WordHint?,
        fallbackUrl: String?,
        onState: (SpeechState) -> Unit,
    ) -> Unit = { word, _, fallbackUrl, onState -> speak(word, fallbackUrl, onState) },
    val prefetchWord: (word: String, hint: WordHint?) -> Unit = { word, _ -> prefetch(word) },
)

val LocalSpeechActions = staticCompositionLocalOf<SpeechActions?> { null }
