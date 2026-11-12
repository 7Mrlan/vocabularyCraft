package dev.wordloom.data.wordbook

import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.model.WordEntry

/** The three books that write themselves from what the reader does. */
enum class AutoBook(
    val id: String,
    val title: String,
    val kicker: String,
    val subtitle: String,
    val countNote: String,
    val emptyHint: String,
) {
    Looked(
        id = "looked",
        title = "查过的词",
        kicker = "词书 · 自动",
        subtitle = "你查过的每一个词",
        countNote = "词 · 最近在前",
        emptyHint = "还没有查过的词。搜一个，或者从首页的每日一词开始。",
    ),
    Wrong(
        id = "wrong",
        title = "错词本",
        kicker = "词书 · 自动",
        subtitle = "答错过的词，回头再看",
        countNote = "词 · 错得多的在前",
        emptyHint = "错词本还是空的。背词时点「忘记」的词会自动进来。",
    ),
    Saved(
        id = "saved",
        title = "收藏夹",
        kicker = "词书 · 自建",
        subtitle = "你亲手存下的词",
        countNote = "词 · 收藏时间",
        emptyHint = "收藏夹还是空的。在词条页点右上角书签，词就会收进来。",
    );

    companion object {
        fun fromId(id: String?): AutoBook? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Deriving the three automatic books. Order matters more than membership:
 * wrong words go by how often they were forgotten, saved words by when they
 * were saved, looked-up words by when they were last opened.
 */
object AutoWords {

    fun looked(opened: List<WordEntry>): List<String> =
        opened.map { it.word.lowercase() }.distinct()

    fun wrong(cards: List<CardEntity>): List<String> =
        cards.filter { it.lapses > 0 }
            .sortedWith(
                compareByDescending<CardEntity> { it.lapses }
                    .thenByDescending { it.lastReviewAt }
                    .thenBy { it.word },
            )
            .map { it.word.lowercase() }
            .distinct()

    fun saved(cards: List<CardEntity>): List<String> =
        cards.filter { it.savedAt > 0L }
            .sortedByDescending { it.savedAt }
            .map { it.word.lowercase() }
            .distinct()

    fun of(kind: AutoBook, opened: List<WordEntry>, cards: List<CardEntity>): List<String> =
        when (kind) {
            AutoBook.Looked -> looked(opened)
            AutoBook.Wrong -> wrong(cards)
            AutoBook.Saved -> saved(cards)
        }
}
