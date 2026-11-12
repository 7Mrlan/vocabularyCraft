package dev.wordloom.ui.onboarding

import android.content.Context

/**
 * One flag, one promise: the first-run guide appears once. TTS setup is kept
 * separately so "skip for now" never looks like "configured".
 */
class OnboardingStore(context: Context) {

    private val prefs = context.getSharedPreferences("wordloom_onboarding", Context.MODE_PRIVATE)

    val done: Boolean
        get() = prefs.getBoolean(KEY_DONE, false)

    val ttsSkipped: Boolean
        get() = prefs.getBoolean(KEY_TTS_SKIPPED, false)

    fun complete(skipped: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DONE, true)
            .putBoolean(KEY_TTS_SKIPPED, skipped)
            .putLong(KEY_SEEN_AT, System.currentTimeMillis())
            .apply()
    }

    fun markTtsConfigured() {
        prefs.edit().putBoolean(KEY_TTS_SKIPPED, false).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_DONE = "onboarding_v1_done"
        const val KEY_TTS_SKIPPED = "tts_setup_skipped"
        const val KEY_SEEN_AT = "onboarding_v1_seen_at"
    }
}
