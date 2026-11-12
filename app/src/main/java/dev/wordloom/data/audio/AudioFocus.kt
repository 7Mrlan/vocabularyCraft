package dev.wordloom.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Short-lived speech focus. A word or sentence should pause when a call or
 * another player takes over, and release focus as soon as the clip ends.
 */
class AudioFocus(context: Context) {

    private val manager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null

    /** Returns false when another app currently owns the audio path. */
    fun request(onLoss: () -> Unit): Boolean {
        abandon()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLoss()
            }
            .build()
        if (manager.requestAudioFocus(next) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return false
        }
        request = next
        return true
    }

    fun abandon() {
        request?.let { manager.abandonAudioFocusRequest(it) }
        request = null
    }
}
