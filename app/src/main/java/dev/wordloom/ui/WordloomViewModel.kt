package dev.wordloom.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wordloom.data.ListWord
import dev.wordloom.data.QuizBuilder
import dev.wordloom.data.QuizQuestion
import dev.wordloom.data.WordRepository
import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.local.ListWithCount
import dev.wordloom.data.model.Suggestion
import dev.wordloom.data.model.WordEntry
import dev.wordloom.srs.DAY_MS
import dev.wordloom.srs.Rating
import dev.wordloom.ui.map.MapCounters
import dev.wordloom.srs.ReviewOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WordUiState {
    data object Idle : WordUiState
    data class Loading(val term: String) : WordUiState
    data class Loaded(val entry: WordEntry, val inDeck: Boolean) : WordUiState
    data class NotFound(val term: String) : WordUiState
    data class Failed(val term: String, val message: String) : WordUiState
}

class WordloomViewModel(private val repo: WordRepository) : ViewModel() {

    private val _wordState = MutableStateFlow<WordUiState>(WordUiState.Idle)
    val wordState: StateFlow<WordUiState> = _wordState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    /** True while a suggestion request is in flight, so the UI never reports "no match" too early. */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _randomRequest = MutableStateFlow(0)

    private var suggestJob: Job? = null

    val recent = repo.recentWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Map card pool: words the person opened (daily word excluded until tapped). */
    val mapWords = repo.openedWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cards: StateFlow<List<CardEntity>> = repo.cards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCards: StateFlow<List<CardEntity>> = repo.dueCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCount: StateFlow<Int> = cards
        .map { list -> list.count { it.dueAt <= System.currentTimeMillis() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _dailyWord = MutableStateFlow<WordEntry?>(null)
    val dailyWord: StateFlow<WordEntry?> = _dailyWord.asStateFlow()

    val lists: StateFlow<List<ListWithCount>> = repo.lists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _quiz = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quiz: StateFlow<List<QuizQuestion>> = _quiz.asStateFlow()

    suspend fun exportBackup(): String = repo.exportBackup()

    suspend fun importBackup(text: String): Result<Int> = repo.importBackup(text)

    /** One round of four-choice practice, built from the local cache only. */
    fun startQuiz() {
        viewModelScope.launch {
            val entries = runCatching { repo.cachedEntries().first() }.getOrElse { emptyList() }
            _quiz.value = QuizBuilder.build(entries)
        }
    }

    private val _listWords = MutableStateFlow<List<ListWord>>(emptyList())
    val listWords: StateFlow<List<ListWord>> = _listWords.asStateFlow()

    val counters: StateFlow<MapCounters> = cards
        .map { list ->
            val now = System.currentTimeMillis()
            MapCounters(
                saved = list.size,
                due = list.count { it.dueAt <= now },
                reviewed = list.count { it.reps > 0 && it.lastReviewAt >= now - 7 * DAY_MS },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapCounters())

    init {
        loadDailyWord()
    }

    fun loadDailyWord() {
        viewModelScope.launch {
            val term = dev.wordloom.data.DailyWord.pick()
            _dailyWord.value = runCatching { repo.lookup(term, markRecent = false) }.getOrNull()
        }
    }

    fun createList(name: String, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch { onCreated(repo.createList(name)) }
    }

    fun addToList(listId: Long, word: String) {
        viewModelScope.launch { repo.addToList(listId, word) }
    }

    fun removeFromList(listId: Long, word: String) {
        viewModelScope.launch { repo.removeFromList(listId, word) }
    }

    fun deleteList(listId: Long) {
        viewModelScope.launch { repo.deleteList(listId) }
    }

    fun openList(listId: Long) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            repo.wordsInList(listId).collect { _listWords.value = it }
        }
    }

    private var listJob: Job? = null

    fun addDailyToDeck(entry: WordEntry) {
        viewModelScope.launch { repo.addToDeck(entry) }
    }

    /** Seeds the page from an entry we already hold, so it opens without a spinner. */
    fun showWord(entry: WordEntry) {
        _wordState.value = WordUiState.Loaded(entry, false)
        viewModelScope.launch {
            val inDeck = runCatching { repo.isInDeck(entry.word) }.getOrDefault(false)
            val state = _wordState.value
            if (state is WordUiState.Loaded && state.entry.word.equals(entry.word, ignoreCase = true)) {
                _wordState.value = state.copy(inDeck = inDeck)
            }
        }
    }

    fun loadWord(term: String, forceRefresh: Boolean = false) {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) return
        // Already showing this word: a second pass would only flash the skeleton.
        val current = _wordState.value
        if (!forceRefresh && current is WordUiState.Loaded &&
            current.entry.word.equals(cleaned, ignoreCase = true)
        ) {
            return
        }
        _wordState.value = WordUiState.Loading(cleaned)
        viewModelScope.launch {
            runCatching { repo.lookup(cleaned, forceRefresh) }
                .onSuccess { entry ->
                    _wordState.value = if (entry == null || !entry.hasContent && entry.ipa.isEmpty()) {
                        WordUiState.NotFound(cleaned)
                    } else {
                        WordUiState.Loaded(entry, repo.isInDeck(entry.word))
                    }
                }
                .onFailure { error ->
                    _wordState.value = WordUiState.Failed(cleaned, error.message ?: "查询失败")
                }
        }
    }

    fun onQueryChanged(prefix: String) {
        suggestJob?.cancel()
        if (prefix.isBlank()) {
            _suggestions.value = emptyList()
            _searching.value = false
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            try {
                _searching.value = true
                _suggestions.value = runCatching { repo.suggest(prefix) }.getOrElse { emptyList() }
            } finally {
                _searching.value = false
            }
        }
    }

    fun randomWord(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val word = repo.randomWord()
            if (word != null) onReady(word)
        }
    }

    fun toggleDeck(entry: WordEntry, currentlyInDeck: Boolean) {
        viewModelScope.launch {
            if (currentlyInDeck) repo.removeFromDeck(entry.word) else repo.addToDeck(entry)
            val state = _wordState.value
            if (state is WordUiState.Loaded && state.entry.word == entry.word) {
                _wordState.value = state.copy(inDeck = !currentlyInDeck)
            }
        }
    }

    fun review(card: CardEntity, rating: Rating, onDone: (ReviewOutcome) -> Unit) {
        viewModelScope.launch {
            onDone(repo.review(card, rating))
        }
    }

    fun trimCache() {
        viewModelScope.launch { repo.trimOldCache() }
    }
}
