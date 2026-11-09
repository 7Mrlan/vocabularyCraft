package dev.wordloom.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.wordloom.data.model.WordEntry
import dev.wordloom.ui.home.HomeScreen
import dev.wordloom.ui.lists.ListDetailScreen
import dev.wordloom.ui.lists.ListsScreen
import dev.wordloom.ui.map.MapScreen
import dev.wordloom.ui.quiz.QuizScreen
import dev.wordloom.ui.review.ReviewScreen
import dev.wordloom.ui.search.SearchScreen
import dev.wordloom.ui.word.SynonymsScreen
import dev.wordloom.ui.word.WordScreen

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(viewModel: WordloomViewModel) {
    val nav = rememberNavController()

    val wordState by viewModel.wordState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val dueCards by viewModel.dueCards.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val counters by viewModel.counters.collectAsStateWithLifecycle()
    val dailyWord by viewModel.dailyWord.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val listWords by viewModel.listWords.collectAsStateWithLifecycle()
    val quiz by viewModel.quiz.collectAsStateWithLifecycle()

    fun openWord(term: String) {
        nav.navigate("word/${Uri.encode(term)}")
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
                onRandom = { viewModel.randomWord(::openWord) },
                onReview = { nav.navigate("review") },
                onWord = ::openWord,
                onOpenMap = { nav.navigate("map") },
                onOpenLists = { nav.navigate("lists") },
                onExportBackup = viewModel::exportBackup,
                onImportBackup = viewModel::importBackup,
            )
        }

        composable("search") {
            SearchScreen(
                suggestions = suggestions,
                searching = searching,
                recent = recent.map { it.word }.take(8),
                onQueryChange = viewModel::onQueryChanged,
                onSubmit = { term ->
                    viewModel.loadWord(term)
                    openWord(term)
                },
                onBack = { nav.popBackStack() },
                onRandom = { viewModel.randomWord(::openWord) },
            )
        }

        composable(
            route = "word/{term}",
            arguments = listOf(navArgument("term") { type = NavType.StringType }),
        ) { entry ->
            val term = Uri.decode(entry.arguments?.getString("term").orEmpty())
            LaunchedEffect(term) { viewModel.loadWord(term) }
            WordScreen(
                state = wordState,
                card = cards.firstOrNull { it.word == term.lowercase() },
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
                entry = (wordState as? WordUiState.Loaded)?.entry
                    ?.takeIf { it.word.equals(term, ignoreCase = true) },
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
            ListDetailScreen(
                name = name,
                words = listWords,
                onBack = { nav.popBackStack() },
                onWord = ::openWord,
                onRemove = { word -> viewModel.removeFromList(id, word) },
            )
        }

        composable("map") {
            MapScreen(
                counters = counters,
                words = cards.map { it.word to it.preview },
                onBack = { nav.popBackStack() },
                onRandomWord = { viewModel.randomWord(::openWord) },
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
                questions = quiz,
                onBack = { nav.popBackStack() },
                onRestart = viewModel::startQuiz,
            )
        }
    }
}
