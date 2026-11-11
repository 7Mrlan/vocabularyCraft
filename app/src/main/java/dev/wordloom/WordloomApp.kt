package dev.wordloom

import android.app.Application
import dev.wordloom.data.WordRepository
import dev.wordloom.data.local.WordloomDb
import dev.wordloom.data.remote.DictionarySource
import dev.wordloom.data.remote.VocabularyComSource
import dev.wordloom.data.translate.TranslationService
import dev.wordloom.data.wordbook.WordBookSelection
import dev.wordloom.data.wordbook.WordBookStore

class AppContainer(app: Application) {
    private val db = WordloomDb.build(app)

    /** Connects to Vocabulary.com directly with fallback to Wiktionary. */
    val source: DictionarySource = VocabularyComSource()

    val repository = WordRepository(source, db.dao())
    val wordBooks = WordBookStore(app)
    val bookSelection = WordBookSelection(app)
}

class WordloomApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialised once, before any MapView is created.
        runCatching { org.maplibre.android.MapLibre.getInstance(this) }
        TranslationService.init()
    }
}
