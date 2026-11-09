package dev.wordloom.data

import dev.wordloom.data.local.CachedWordEntity
import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.local.ListWordEntity
import dev.wordloom.data.local.WordListEntity
import kotlinx.serialization.Serializable

/**
 * Everything worth keeping when the phone changes: review cards, lists, and the
 * cached entries (so list rows still show a gloss offline). `version` lets a
 * later format change refuse or migrate instead of mis-reading.
 */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long,
    val cards: List<CardEntity> = emptyList(),
    val lists: List<WordListEntity> = emptyList(),
    val listWords: List<ListWordEntity> = emptyList(),
    val words: List<CachedWordEntity> = emptyList(),
)
