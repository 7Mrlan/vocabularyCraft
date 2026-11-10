package dev.wordloom.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.wordloom.data.model.WordEntry
import dev.wordloom.ui.books.BooksScreen
import dev.wordloom.ui.home.HomeScreen
import dev.wordloom.ui.lists.ListDetailScreen
import dev.wordloom.ui.lists.ListsScreen
import dev.wordloom.ui.map.MapScreen
import dev.wordloom.ui.quiz.QuizScreen
import dev.wordloom.ui.review.ReviewScreen
import dev.wordloom.ui.search.SearchScreen
import dev.wordloom.ui.word.SynonymsScreen
import dev.wordloom.ui.word.WordScreen

@Composable
fun AppNav(
    viewModel: WordloomViewModel,
    onToggleTheme: () -> Unit,
) {
    val nav = rememberNavController()
    val context = LocalContext.current

    val wordState by viewModel.wordState.collectAsStateWithLifecycle()
    val suggest by viewModel.suggest.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val dueCards by viewModel.dueCards.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val counters by viewModel.counters.collectAsStateWithLifecycle()
    val dailyWord by viewModel.dailyWord.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val listWords by viewModel.listWords.collectAsStateWithLifecycle()
    val quiz by viewModel.quiz.collectAsStateWithLifecycle()
    val mapWords by viewModel.mapWords.collectAsStateWithLifecycle()

    fun openWord(term: String) {
        nav.navigate("word/${Uri.encode(term)}")
    }

    fun pickRandomWord() {
        viewModel.randomWord(
            onReady = ::openWord,
            onFailed = {
                Toast.makeText(context, "暂时抽不到随机词，检查网络后重试", Toast.LENGTH_SHORT).show()
            },
        )
    }

    /** The map already holds the full entry; hand it to the page before routing. */
    fun openMappedWord(term: String) {
        mapWords.firstOrNull { it.word.equals(term, ignoreCase = true) }
            ?.let(viewModel::showWord)
        openWord(term)
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        // No page transition at all. A full-screen slide cost a frame here and
        // there on real hardware and read as a hitch; switching instantly is
        // smoother than a transition that stutters.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable("home") {
            HomeScreen(
                dueCount = dueCount,
                totalCards = cards.size,
                recent = recent,
                dailyWord = dailyWord,
                dailyInDeck = dailyWord?.let { entry -> cards.any { it.word == entry.word.lowercase() } } == true,
                onAddDaily = viewModel::addDailyToDeck,
                onSearch = { nav.navigate("search") },
                onRandom = ::pickRandomWord,
                onReview = { nav.navigate("review") },
                onWord = ::openWord,
                onOpenMap = { nav.navigate("map") },
                onOpenLists = { nav.navigate("lists") },
                onExportBackup = viewModel::exportBackup,
                onImportBackup = viewModel::importBackup,
                onQuiz = { nav.navigate("quiz") },
                onToggleTheme = onToggleTheme,
                onOpenBooks = { nav.navigate("books") },
            )
        }

        composable("books") {
            BooksScreen(onBack = { nav.popBackStack() })
        }

        composable("search") {
            SearchScreen(
                suggest = suggest,
                recent = recent.map { it.word }.take(8),
                onQueryChange = viewModel::onQueryChanged,
                onSubmit = { term ->
                    viewModel.loadWord(term)
                    openWord(term)
                },
                onRetrySuggest = viewModel::retrySuggest,
                onBack = { nav.popBackStack() },
                onRandom = ::pickRandomWord,
            )
        }

        composable(
            route = "word/{term}",
            arguments = listOf(navArgument("term") { type = NavType.StringType }),
        ) { entry ->
            val term = Uri.decode(entry.arguments?.getString("term").orEmpty())
            val loadedWord = (wordState as? WordUiState.Loaded)?.entry?.word
            LaunchedEffect(term) { viewModel.loadWord(term) }
            WordScreen(
                term = term,
                state = wordState,
                card = cards.firstOrNull {
                    it.word.equals(term.trim(), ignoreCase = true) ||
                        (loadedWord != null && it.word.equals(loadedWord, ignoreCase = true))
                },
                lists = lists,
                onBack = { nav.popBackStack() },
                onRetry = { viewModel.loadWord(term, forceRefresh = true) },
                onToggleDeck = { word: WordEntry, inDeck: Boolean ->
                    viewModel.toggleDeck(word, inDeck)
                },
                onAddToList = viewModel::addToList,
                onCreateList = viewModel::createList,
                onWord = ::openWord,
                onOpenSynonyms = {
                    (wordState as? WordUiState.Loaded)?.entry?.word?.let { loaded ->
                        nav.navigate("synonyms/${Uri.encode(loaded)}")
                    }
                },
            )
        }

        composable(
            route = "synonyms/{term}",
            arguments = listOf(navArgument("term") { type = NavType.StringType }),
        ) { entry ->
            val term = Uri.decode(entry.arguments?.getString("term").orEmpty())
            // The word page already has this entry loaded; reloading would flash a spinner.
            LaunchedEffect(term) {
                val loaded = (wordState as? WordUiState.Loaded)?.entry
                if (loaded == null || !loaded.word.equals(term, ignoreCase = true)) {
                    viewModel.loadWord(term)
                }
            }
            SynonymsScreen(
                entry = (wordState as? WordUiState.Loaded)
                    ?.takeIf {
                        it.entry.word.equals(term.trim(), ignoreCase = true) ||
                            it.requestedTerm.equals(term.trim(), ignoreCase = true)
                    }
                    ?.entry,
                loading = wordState is WordUiState.Loading,
                onBack = { nav.popBackStack() },
                onWord = ::openWord,
            )
        }

        composable("lists") {
            ListsScreen(
                lists = lists,
                onBack = { nav.popBackStack() },
                onOpen = { list -> nav.navigate("list/${list.id}/${Uri.encode(list.name)}") },
                onCreate = { name -> viewModel.createList(name) },
            )
        }

        composable(
            route = "list/{id}/{name}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
            LaunchedEffect(id) { viewModel.openList(id) }
            // Not keyed on id: this destination is one back-stack entry, so the
            // observer only needs to stop when the screen itself leaves.
            DisposableEffect(Unit) { onDispose { viewModel.closeList() } }
            ListDetailScreen(
                name = name,
                words = listWords,
                onBack = { nav.popBackStack() },
                onWord = ::openWord,
                onRemove = { word -> viewModel.removeFromList(id, word) },
            )
        }

        composable("map") {
            // Decoding and mapping up to 400 cached entries is too much to redo
            // on every AppNav recomposition; key it on the list itself.
            val words = remember(mapWords) {
                mapWords.map { entry ->
                    entry.word to (
                        entry.shortBlurb?.takeIf { it.isNotBlank() }
                            ?: entry.senses.firstOrNull()?.definition.orEmpty()
                        )
                }
            }
            MapScreen(
                counters = counters,
                words = words,
                onBack = { nav.popBackStack() },
                onRandomWord = ::pickRandomWord,
                onOpenWord = ::openMappedWord,
            )
        }

        composable("review") {
            ReviewScreen(
                dueCards = dueCards,
                onBack = { nav.popBackStack() },
                onRate = { card, rating -> viewModel.review(card, rating) {} },
                onOpenWord = ::openWord,
                onQuiz = { nav.navigate("quiz") },
            )
        }

        composable("quiz") {
            LaunchedEffect(Unit) { viewModel.startQuiz() }
            QuizScreen(
                questions = quiz.questions,
                round = quiz.round,
                onBack = { nav.popBackStack() },
                onRestart = viewModel::startQuiz,
            )
        }
    }
}
