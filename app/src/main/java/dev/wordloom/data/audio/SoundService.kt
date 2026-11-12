package dev.wordloom.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import dev.wordloom.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class Sound(@RawRes val res: Int, val gain: Float) {
    Easy(R.raw.grade_easy, 0.62f),
    Good(R.raw.grade_good, 0.55f),
    Hard(R.raw.grade_hard, 0.45f),
    Again(R.raw.grade_again, 0.40f),
    SpellRight(R.raw.spell_right, 0.62f),
    SpellWrong(R.raw.spell_wrong, 0.40f),
    QuizCorrect(R.raw.quiz_correct, 0.55f),
    PageTurn(R.raw.gear_click, 0.32f),
}

data class SoundSettings(
    val enabled: Boolean = true,
    val volume: Float = 0.60f,
    val ratings: Boolean = true,
    val spelling: Boolean = true,
    val quiz: Boolean = true,
    val pageTurn: Boolean = false,
)

/**
 * One SoundPool for every short cue in the app. The old screens each built
 * their own pool and played at 0.5+ gain; this keeps the room quiet and makes
 * the settings page a real switch instead of a decoration.
 */
class SoundService private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wordloom_sound", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // Media, not sonification: on most phones the system-sound
                // stream is muted independently, which made every cue silent
                // even though TTS (media) played fine.
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = Sound.entries.associateWith { pool.load(context, it.res, 1) }

    fun play(sound: Sound) {
        val current = _settings.value
        if (!current.enabled) return
        val allowed = when (sound) {
            Sound.Easy, Sound.Good, Sound.Hard, Sound.Again -> current.ratings
            Sound.SpellRight, Sound.SpellWrong -> current.spelling
            Sound.QuizCorrect -> current.quiz
            Sound.PageTurn -> current.pageTurn
        }
        if (!allowed) return
        val id = ids[sound] ?: return
        val gain = (current.volume * sound.gain).coerceIn(0f, 1f)
        // A hair of pitch variation keeps repeated ratings from sounding like a
        // machine gun; it is deliberately inaudible as a pitch change.
        pool.play(id, gain, gain, 1, 0, 0.98f + Random.nextFloat() * 0.04f)
    }

    /** Audition: honours the volume but ignores the per-group switches. */
    fun preview(sound: Sound) {
        val id = ids[sound] ?: return
        val gain = (_settings.value.volume * sound.gain).coerceIn(0f, 1f)
        pool.play(id, gain, gain, 1, 0, 1f)
    }

    fun setEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(enabled = enabled)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setVolume(volume: Float) {
        val next = volume.coerceIn(0f, 1f)
        _settings.value = _settings.value.copy(volume = next)
        prefs.edit().putFloat(KEY_VOLUME, next).apply()
    }

    fun setRatings(enabled: Boolean) {
        _settings.value = _settings.value.copy(ratings = enabled)
        prefs.edit().putBoolean(KEY_RATINGS, enabled).apply()
    }

    fun setSpelling(enabled: Boolean) {
        _settings.value = _settings.value.copy(spelling = enabled)
        prefs.edit().putBoolean(KEY_SPELLING, enabled).apply()
    }

    fun setQuiz(enabled: Boolean) {
        _settings.value = _settings.value.copy(quiz = enabled)
        prefs.edit().putBoolean(KEY_QUIZ, enabled).apply()
    }

    fun setPageTurn(enabled: Boolean) {
        _settings.value = _settings.value.copy(pageTurn = enabled)
        prefs.edit().putBoolean(KEY_PAGE_TURN, enabled).apply()
    }

    fun reset() {
        _settings.value = SoundSettings()
        prefs.edit().clear().apply()
    }

    private fun load(): SoundSettings = SoundSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        volume = prefs.getFloat(KEY_VOLUME, 0.60f).coerceIn(0f, 1f),
        ratings = prefs.getBoolean(KEY_RATINGS, true),
        spelling = prefs.getBoolean(KEY_SPELLING, true),
        quiz = prefs.getBoolean(KEY_QUIZ, true),
        pageTurn = prefs.getBoolean(KEY_PAGE_TURN, false),
    )

    companion object {
        @Volatile
        private var instance: SoundService? = null

        fun shared(context: Context): SoundService =
            instance ?: synchronized(this) {
                instance ?: SoundService(context.applicationContext).also { instance = it }
            }

        private const val KEY_ENABLED = "enabled"
        private const val KEY_VOLUME = "volume"
        private const val KEY_RATINGS = "ratings"
        private const val KEY_SPELLING = "spelling"
        private const val KEY_QUIZ = "quiz"
        private const val KEY_PAGE_TURN = "page_turn"
    }
}
