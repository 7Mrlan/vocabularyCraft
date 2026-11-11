package dev.wordloom.data.wordbook

import android.content.Context

/** Which book the reader is currently in. One key, so plain SharedPreferences. */
class WordBookSelection(context: Context) {

    private val prefs = context.getSharedPreferences("wordloom_books", Context.MODE_PRIVATE)

    var selected: WordBook
        get() = WordBook.fromId(prefs.getString(KEY, null))
        set(value) {
            prefs.edit().putString(KEY, value.name).apply()
        }

    private companion object {
        const val KEY = "selected_book"
    }
}
