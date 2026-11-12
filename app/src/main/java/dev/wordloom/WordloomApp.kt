package dev.wordloom

import android.app.Application
import dev.wordloom.data.WordRepository
import dev.wordloom.data.local.WordloomDb
import dev.wordloom.data.remote.DictionarySource
import dev.wordloom.data.remote.VocabularyComSource
import dev.wordloom.data.audio.AudioCache
import dev.wordloom.data.translate.TranslationService
import dev.wordloom.data.tts.SpeechService
import dev.wordloom.data.tts.TtsConfig
import dev.wordloom.data.tts.TtsConfigStore
import dev.wordloom.data.wordbook.WordBookSelection
import dev.wordloom.data.wordbook.WordBookStore

class AppContainer(app: Application) {
    private val db = WordloomDb.build(app)

    /** Connects to Vocabulary.com directly with fallback to Wiktionary. */
    val source: DictionarySource = VocabularyComSource()

    val repository = WordRepository(source, db)
    val wordBooks = WordBookStore(app)
    val bookSelection = WordBookSelection(app)
    // Shared with the speaker buttons so all clip downloads use one queue.
    val audioCache = AudioCache.shared(app)

    // Debug builds may bootstrap a local TTS endpoint from local.properties;
    // release builds get empty BuildConfig values and never read that file.
    // The encrypted store is the real source and always wins when present.
    val speech = SpeechService(
        app,
        TtsConfig.fromBuildConfig(),
        TtsConfigStore(app),
        audioCache,
    )
}

class WordloomApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialised once, before any MapView is created.
        runCatching { org.maplibre.android.MapLibre.getInstance(this) }
            .onFailure { android.util.Log.w("Wordloom", "MapLibre init failed", it) }
        TranslationService.init()
    }
}
