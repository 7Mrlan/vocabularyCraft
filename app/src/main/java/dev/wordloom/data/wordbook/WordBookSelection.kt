package dev.wordloom.data.wordbook

import android.content.Context

/**
 * Which book the reader is currently in — a curated [WordBook] or one of the
 * automatic books, so the key is the book's id rather than the enum.
 */
class WordBookSelection(context: Context) {

    private val prefs = context.getSharedPreferences("wordloom_books", Context.MODE_PRIVATE)

    var selectedId: String
        get() = prefs.getString(KEY, null) ?: WordBook.Core.name
        set(value) {
            prefs.edit().putString(KEY, value).apply()
        }

    private companion object {
        const val KEY = "selected_book"
    }
}
