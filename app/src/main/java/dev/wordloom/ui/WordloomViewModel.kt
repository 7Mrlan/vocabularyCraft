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
import dev.wordloom.data.wordbook.WordBook
import dev.wordloom.data.wordbook.WordBookSelection
import dev.wordloom.data.wordbook.WordBookStore
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
    data class Loaded(
        val entry: WordEntry,
        val inDeck: Boolean,
        val requestedTerm: String = entry.word,
    ) : WordUiState
    data class NotFound(val term: String) : WordUiState
    data class Failed(val term: String, val message: String) : WordUiState
}

/** Where a suggestion request stands. The query travels with the result so the UI never
 *  reports "no match" for text the person has already moved past. */
enum class SuggestPhase { Idle, Typing, Loading, Done, Empty, Error }

data class SuggestUiState(
    val query: String = "",
    val phase: SuggestPhase = SuggestPhase.Idle,
    val items: List<Suggestion> = emptyList(),
)

/** Questions plus a round number, so a repeat round still resets the screen. */
data class QuizState(
    val questions: List<QuizQuestion> = emptyList(),
    val round: Int = 0,
)

class WordloomViewModel(
    private val repo: WordRepository,
    private val bookStore: WordBookStore,
    private val bookSelection: WordBookSelection,
) : ViewModel() {

    private val _wordState = MutableStateFlow<WordUiState>(WordUiState.Idle)
    val wordState: StateFlow<WordUiState> = _wordState.asStateFlow()

    private val _suggest = MutableStateFlow(SuggestUiState())
    val suggest: StateFlow<SuggestUiState> = _suggest.asStateFlow()

    private var suggestJob: Job? = null
    private var wordJob: Job? = null

    val recent = repo.recentWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Map card pool: words the person opened (daily word excluded until tapped). */
    val mapWords = repo.openedWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A curated book's name, or an [AutoBook] id. */
    private val _selectedId = MutableStateFlow(bookSelection.selectedId)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    /** The selected book's headwords, in study order. Loaded off the main thread. */
    private val _bookWords = MutableStateFlow<List<String>>(emptyList())
    val bookWords: StateFlow<List<String>> = _bookWords.asStateFlow()

    /**
     * Entries fetched for study cards. Kept apart from [mapWords] so preloading a
     * unit doesn't push ten words into "最近查过".
     */
    private val _studyEntries = MutableStateFlow<Map<String, WordEntry>>(emptyMap())
    val studyEntries: StateFlow<Map<String, WordEntry>> = _studyEntries.asStateFlow()
    private val prefetching = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        loadBook(_selectedId.value)
    }

    fun selectBook(id: String) {
        if (id == _selectedId.value) return
        _selectedId.value = id
        bookSelection.selectedId = id
        loadBook(id)
    }

    /** Automatic books have no word list on disk; their words come from history. */
    private fun loadBook(id: String) {
        val curated = WordBook.entries.firstOrNull { it.name == id }
        if (curated == null) {
            _bookWords.value = emptyList()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _bookWords.value = bookStore.words(curated)
        }
    }

    /** Pull one study word's entry in the background; the card upgrades when it lands. */
    fun prefetchForStudy(term: String) {
        val key = term.trim().lowercase()
        if (key.isEmpty() || _studyEntries.value.containsKey(key) || !prefetching.add(key)) return
        viewModelScope.launch {
            try {
                repo.lookup(key, markRecent = false)?.let { entry ->
                    _studyEntries.value = _studyEntries.value + (key to entry)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // Offline or a dead source: the card still has its Chinese side.
            } finally {
                prefetching.remove(key)
            }
        }
    }

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

    private val _quiz = MutableStateFlow(QuizState())
    val quiz: StateFlow<QuizState> = _quiz.asStateFlow()

    private var quizLoading = false

    suspend fun exportBackup(): String = repo.exportBackup()

    suspend fun importBackup(text: String): Result<Int> = repo.importBackup(text)

    /** One round of four-choice practice, built from the local cache only. */
    fun startQuiz() {
        // A tap during the previous round's load must not queue a second one.
        if (quizLoading) return
        quizLoading = true
        viewModelScope.launch {
            try {
                val entries = runCatching { repo.cachedEntries().first() }.getOrElse { emptyList() }
                val previous = _quiz.value
                _quiz.value = QuizState(QuizBuilder.build(entries), previous.round + 1)
            } finally {
                quizLoading = false
            }
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
        // Entries past the 30-day freshness window are refetched anyway; keep
        // the cache bounded without touching cards or lists.
        trimCache()
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

    fun openList(listId: Long) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            repo.wordsInList(listId).collect { _listWords.value = it }
        }
    }

    /** Stop observing list rows once the detail screen is gone. */
    fun closeList() {
        listJob?.cancel()
        listJob = null
    }

    private var listJob: Job? = null

    fun addDailyToDeck(entry: WordEntry) {
        viewModelScope.launch { repo.addToDeck(entry) }
    }

    /** Seeds the page from an entry we already hold, so it opens without a spinner. */
    fun showWord(entry: WordEntry) {
        val knownInDeck = cards.value.any { it.word.equals(entry.word, ignoreCase = true) }
        _wordState.value = WordUiState.Loaded(entry, knownInDeck, entry.word)
        viewModelScope.launch {
            val inDeck = runCatching { repo.isInDeck(entry.word) }.getOrDefault(knownInDeck)
            val state = _wordState.value
            if (state is WordUiState.Loaded && state.entry.word.equals(entry.word, ignoreCase = true)) {
                _wordState.value = state.copy(inDeck = inDeck)
            }
        }
    }

    fun loadWord(term: String, forceRefresh: Boolean = false) {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) return
        // Already showing or already fetching this word: a second pass would only
        // flash the skeleton (or cancel the request the first tap started).
        val current = _wordState.value
        if (!forceRefresh) {
            if (current is WordUiState.Loaded &&
                (current.entry.word.equals(cleaned, ignoreCase = true) ||
                    current.requestedTerm.equals(cleaned, ignoreCase = true))
            ) return
            if (current is WordUiState.Loading && current.term.equals(cleaned, ignoreCase = true)) return
        }
        wordJob?.cancel()
        _wordState.value = WordUiState.Loading(cleaned)
        wordJob = viewModelScope.launch {
            val result = try {
                Result.success(repo.lookup(cleaned, forceRefresh))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            // A newer request owns the screen now; this answer is stale.
            val state = _wordState.value
            if (state !is WordUiState.Loading || state.term != cleaned) return@launch
            result.onSuccess { entry ->
                _wordState.value = if (entry == null || (!entry.hasContent && entry.ipa.isEmpty())) {
                    WordUiState.NotFound(cleaned)
                } else {
                    val inDeck = runCatching { repo.isInDeck(entry.word) }.getOrDefault(false)
                    WordUiState.Loaded(entry, inDeck, cleaned)
                }
            }.onFailure { error ->
                _wordState.value = WordUiState.Failed(cleaned, error.message ?: "查询失败")
            }
        }
    }

    fun onQueryChanged(prefix: String) {
        val query = prefix.trim()
        suggestJob?.cancel()
        if (query.isEmpty()) {
            _suggest.value = SuggestUiState()
            return
        }
        // Keep whatever the list already shows while the next request is on its way:
        // clearing here is what made the empty state flash mid-word.
        _suggest.value = SuggestUiState(query, SuggestPhase.Typing, _suggest.value.items)
        suggestJob = viewModelScope.launch {
            delay(250)
            if (_suggest.value.query != query) return@launch
            _suggest.value = _suggest.value.copy(phase = SuggestPhase.Loading)
            val items = try {
                repo.suggest(query)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (_suggest.value.query == query) {
                    // The old rows belong to an earlier query; keeping them would
                    // hide the failure behind results the person no longer asked for.
                    _suggest.value = SuggestUiState(query, SuggestPhase.Error)
                }
                return@launch
            }
            // A newer keystroke owns the field now; this answer is stale.
            if (_suggest.value.query != query) return@launch
            _suggest.value = SuggestUiState(
                query = query,
                phase = if (items.isEmpty()) SuggestPhase.Empty else SuggestPhase.Done,
                items = items,
            )
        }
    }

    /** Re-runs the current suggestion request after a failure. */
    fun retrySuggest() {
        val query = _suggest.value.query
        if (query.isNotEmpty()) onQueryChanged(query)
    }

    fun randomWord(onReady: (String) -> Unit, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            val word = try {
                repo.randomWord()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (word != null) onReady(word) else onFailed()
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

    /** One rating from the study card: a new word joins the deck first. */
    fun rateStudy(entry: WordEntry, rating: Rating, isNew: Boolean, onDone: (Int?) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val before = repo.card(entry.word)
                if (isNew) repo.addToDeck(entry)
                val card = repo.card(entry.word)
                val interval = card?.let { repo.review(it, rating).intervalDays }
                lastStudyUndo = {
                    if (isNew) repo.removeFromDeck(entry.word) else before?.let { repo.restoreCard(it) }
                }
                interval
            }.onSuccess(onDone).onFailure { onDone(null) }
        }
    }

    private var lastStudyUndo: (suspend () -> Unit)? = null

    /** Reverts the most recent rating, so a mis-tap never schedules a card wrongly. */
    fun undoStudy() {
        viewModelScope.launch {
            lastStudyUndo?.invoke()
            lastStudyUndo = null
        }
    }

    /** The reader says they already know this word: take it out of the queue. */
    fun markKnown(entry: WordEntry) {
        viewModelScope.launch { runCatching { repo.removeFromDeck(entry.word) } }
    }

    fun trimCache() {
        viewModelScope.launch { repo.trimOldCache() }
    }
}
