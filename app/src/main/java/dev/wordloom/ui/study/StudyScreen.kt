package dev.wordloom.ui.study
import android.view.HapticFeedbackConstants
import android.os.Build

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.animation.core.animate
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.wordloom.data.translate.TranslationService
import dev.wordloom.ui.word.GlassTranslateCard
import dev.wordloom.ui.word.SelectableText
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.snapshotFlow
import androidx.annotation.RawRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.model.WordEntry
import dev.wordloom.data.translate.StudyContent
import dev.wordloom.data.translate.StudyDetails
import dev.wordloom.data.audio.Sound
import dev.wordloom.data.audio.SoundService
import dev.wordloom.data.tts.SpeechState
import dev.wordloom.data.translate.StudyExample
import dev.wordloom.srs.Rating
import dev.wordloom.ui.components.rememberAudioPlayer
import dev.wordloom.ui.feedback.rememberAppFeedback
import dev.wordloom.ui.voice.LocalSpeechActions
import dev.wordloom.ui.voice.SpeechActions
import dev.wordloom.ui.voice.SpeechButton
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.wlPress
import dev.wordloom.ui.theme.WordloomTheme
import kotlin.math.min

private enum class Layer { Word, English, Chinese }

/**
 * A quick flick turns the page even when it travels less than the distance
 * threshold. 400 dp/s is the same order as the threshold ViewPager uses for a
 * fling page turn, so a short, deliberate flick is enough — no long drag needed.
 */
/** A long review session must not turn into a 500-word spelling test. */
private const val SPELLING_LIMIT = 20

@Immutable
private data class StudyItem(val word: String, val isNew: Boolean)

private fun Rating.studySound(): Sound = when (this) {
    Rating.Easy -> Sound.Easy
    Rating.Good -> Sound.Good
    Rating.Hard -> Sound.Hard
    Rating.Again -> Sound.Again
}

/**
 * One word at a time, self-report first.
 *
 * The word face asks for a grade before anything is revealed; only then do the
 * layers open. Up is the Chinese side, down is our English entry, right is the
 * example cards; a tap returns to the word. The four grades go straight into
 * FSRS, and an "I was wrong" after the reveal overrides the self-report.
 */
@Composable
fun StudyScreen(
    dueCards: List<CardEntity>,
    /** Every card, so a word already scheduled is never offered as new again. */
    cards: List<CardEntity>,
    /** The current unit of the selected book. */
    newWords: List<String>,
    entries: Map<String, WordEntry>,
    /** True while a curated book's word list is still being read from assets. */
    loading: Boolean,
    onNeedEntry: (String) -> Unit,
    onBack: () -> Unit,
    onRate: (WordEntry, Rating, Boolean) -> Unit,
    onUndo: () -> Unit,
    onMarkKnown: (WordEntry) -> Unit,
    speechActions: SpeechActions = SpeechActions(
        enabled = false,
        speak = { _, _, _ -> },
        prefetch = {},
    ),
) {
    val wl = WordloomTheme.colors
    val view = LocalView.current
    val feedback = rememberAppFeedback()
    val player = rememberAudioPlayer()
    val context = LocalContext.current
    val sfx = remember(context) { SoundService.shared(context) }
    val speech = LocalSpeechActions.current
    val hazeState = remember { HazeState() }
    val clipboard = LocalClipboardManager.current
    // A word picked out of the card with a long press, translated in the same
    // glass card the word page uses.
    var promo by remember { mutableStateOf<String?>(null) }
    var promoTranslation by remember { mutableStateOf<String?>(null) }
    var promoFailure by remember { mutableStateOf<String?>(null) }
    var promoLoading by remember { mutableStateOf(false) }
    LaunchedEffect(promo) {
        val text = promo ?: return@LaunchedEffect
        promoLoading = true
        promoTranslation = null
        promoFailure = null
        TranslationService.translate(text).fold(
            onSuccess = { promoTranslation = it },
            onFailure = { promoFailure = it.message ?: "翻译失败" },
        )
        promoLoading = false
    }

    // A due card must never disappear because its word fell out of the entry
    // cache; the card's own preview is enough to study it.
    val previews = remember(dueCards) {
        dueCards.associate { it.word.lowercase() to it.preview }
    }
    fun entryFor(word: String): WordEntry =
        entries[word.lowercase()] ?: WordEntry(
            word = word,
            shortBlurb = previews[word.lowercase()]?.ifBlank { null },
        )

    // Every due card belongs in this session: a 60-card cap made the final
    // screen say "today is done" while the rest of the queue was still due.
    val dueItems = remember(dueCards) {
        dueCards.map { StudyItem(it.word.lowercase(), false) }
    }
    val newItems = remember(newWords, cards) {
        StudyQueue.newWords(newWords, cards).map { StudyItem(it, true) }
    }

    var queue by remember { mutableStateOf<List<StudyItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var completed by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(dueItems, newItems) {
        val incoming = (dueItems + newItems).distinctBy { it.word.lowercase() }
        // The flows start empty on a cold restore. Before the first answer,
        // merge newly arrived cards instead of dropping them just because a
        // new-word batch already filled the queue.
        if (!started) {
            queue = incoming
            total = queue.size
            started = true
        } else if (completed == 0) {
            val known = queue.mapTo(HashSet(queue.size)) { it.word.lowercase() }
            val fresh = incoming.filter { it.word.lowercase() !in known }
            if (fresh.isNotEmpty()) {
                queue = queue + fresh
                total = queue.size
            }
        }
    }

    // Bumped on every commit and undo: the card is rebuilt from the grade face
    // even when the same word comes back (requeued, or undone).
    var presentation by remember { mutableIntStateOf(0) }

    // Everything committed this session, so earlier words can be looked at
    // again without reopening their grade face.
    val history = remember { mutableStateListOf<Pair<String, Rating>>() }
    var reviewIndex by remember { mutableStateOf<Int?>(null) }
    val seenToday = remember { mutableSetOf<String>() }
    val spellWords = remember { mutableListOf<WordEntry>() }
    var spelling by remember { mutableStateOf(false) }
    var spellScore by remember { mutableIntStateOf(0) }
    var spellTotal by remember { mutableIntStateOf(0) }
    var spellTaken by remember { mutableStateOf(false) }
    var lastCommitted by remember { mutableStateOf<StudyItem?>(null) }

    fun commit(item: StudyItem, rating: Rating, requeue: Boolean) {
        // A stale tap callback must not drop the next card; the committed item
        // is always the head of the queue.
        if (!queue.firstOrNull()?.word.equals(item.word, ignoreCase = true)) return
        val key = item.word.lowercase()
        val firstToday = seenToday.add(key)
        if (firstToday) {
            // Only a real commit can be undone: a requeued word answered a
            // second time writes nothing, so it must not become the target.
            lastCommitted = item
            history.add(item.word to rating)
            completed += 1
            onRate(entryFor(item.word), rating, item.isNew)
            if ((rating == Rating.Good || rating == Rating.Easy) &&
                spellWords.size < SPELLING_LIMIT
            ) {
                spellWords.add(entryFor(item.word))
            }
        } else {
            // A requeued word's second answer writes nothing to the schedule, so
            // it cannot be undone. Leaving the previous word's target here made
            // 撤销 revert a different card than the one on screen.
            lastCommitted = null
        }
        val rest = queue.drop(1)
        queue = if (requeue) {
            when (rating) {
                Rating.Again -> rest + item
                Rating.Hard -> {
                    val at = min(3, rest.size)
                    rest.take(at) + item + rest.drop(at)
                }
                else -> rest
            }
        } else {
            rest
        }
        presentation += 1
    }

    val current = queue.firstOrNull()
    // The English entry may not be cached yet. Ask for the current card and the
    // next two, so their clips are already on disk by the time they are tapped.
    LaunchedEffect(queue.firstOrNull()?.word, queue.size) {
        queue.take(3).forEach { item ->
            onNeedEntry(item.word)
            // The current card and the prefetch share one fetch (StudyContent
            // dedupes in flight), so the ring's page count is stable before it
            // is swiped.
            launch { StudyContent.details(item.word) }
        }
        // Only the word on screen is warmed as AI audio; the next example is
        // prefetched after the current page settles, not a whole batch ahead.
        queue.take(1).forEach { item -> speechActions.prefetch(item.word) }
    }
    val progress = if (total == 0) 0f else completed.toFloat() / total

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(
                Brush.verticalGradient(
                    listOf(
                        wl.ink.copy(alpha = 0.035f),
                        wl.surface,
                        wl.surface,
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.wl_back),
                    contentDescription = "返回",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = "背单词",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = wl.ink,
            )
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty() && !spelling) {
                Text(
                    text = "回顾",
                    fontFamily = SerifFamily,
                    fontSize = 12.5.sp,
                    color = wl.inkMuted,
                    modifier = Modifier
                        .wlPress { reviewIndex = history.lastIndex }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            if (current != null) {
                IconButton(
                    onClick = {
                        val item = lastCommitted
                        if (item != null) {
                            val rest = queue.filterNot {
                                it.word.equals(item.word, ignoreCase = true)
                            }
                            queue = listOf(item) + rest
                            // Put the word back where it was so rating it again
                            // is a first answer, not a silent no-op.
                            seenToday.remove(item.word.lowercase())
                            spellWords.removeAll { it.word.equals(item.word, ignoreCase = true) }
                            // The undone rating is history now; the review list
                            // must not keep showing it.
                            if (history.isNotEmpty()) history.removeAt(history.lastIndex)
                            lastCommitted = null
                            completed = (completed - 1).coerceAtLeast(0)
                            presentation += 1
                            onUndo()
                        }
                    },
                    enabled = lastCommitted != null,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.wl_refresh),
                        contentDescription = "撤销上一次评分",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(22.dp)
                            .alpha(if (lastCommitted != null) 1f else 0.3f),
                    )
                }
                Text(
                    text = "熟知",
                    fontFamily = SerifFamily,
                    fontSize = 12.5.sp,
                    color = wl.inkMuted,
                    modifier = Modifier
                        .clickable {
                            // "Already know it" is a finish too: the ring and the
                            // counter must move together.
                            if (seenToday.add(current.word.lowercase())) {
                                completed += 1
                            }
                            // 熟知 is a finish, not a rating: nothing is left to
                            // undo, and the previous word's undo must not fire.
                            lastCommitted = null
                            onMarkKnown(entryFor(current.word))
                            queue = queue.drop(1)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            if (total > 0) {
                Text(
                    text = "$completed/$total",
                    fontFamily = SerifFamily,
                    fontSize = 12.5.sp,
                    color = wl.inkMuted,
                    modifier = Modifier.padding(end = 14.dp),
                )
            }
        }

        when {
            !started -> Unit

            // A curated book is read off disk on the IO dispatcher; until it
            // lands, "今天没有要背的词" would be a lie.
            loading && total == 0 -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("正在载入词表…", fontSize = 12.sp, color = wl.inkMuted)
            }

            total == 0 -> EmptyState(onBack = onBack)

            current == null && spelling -> CompositionLocalProvider(
                LocalSpeechActions provides speechActions,
            ) {
                SpellingRound(
                    words = spellWords,
                    onSpeak = { word ->
                        // The spelling cue follows the same AI/fallback switch as
                        // every other word: a recorded clip must not slip through
                        // when the reader chose an AI-only voice.
                        val clip = entryFor(word).audio.firstOrNull()?.url
                        if (speech != null && speech.enabled) {
                            speech.speak(word, clip) { }
                        } else if (!clip.isNullOrBlank()) {
                            player.play(clip)
                        }
                    },
                    onSound = { correct ->
                        sfx.play(if (correct) Sound.SpellRight else Sound.SpellWrong)
                    },
                    onFinish = { correct ->
                        spellScore = correct
                        spellTaken = true
                        spelling = false
                    },
                )
            }

            current == null -> DoneState(
                total = total,
                hasSpelling = spellWords.isNotEmpty(),
                spelled = spellTaken,
                spellScore = spellScore,
                spellTotal = spellTotal,
                onStartSpelling = {
                    spellTotal = spellWords.size
                    spelling = true
                },
                onBack = onBack,
            )

            else -> CompositionLocalProvider(LocalSpeechActions provides speechActions) {
                // A new card must start a fresh AnimatedContent. Without this
                // key the previous card's English layer is kept as the outgoing
                // content of the Word transition, so its RingAudio would read
                // the new card's example over the grade face.
                key(current.word, presentation) {
                StudyCard(
                    item = current,
                    entry = entryFor(current.word),
                    presentation = presentation,
                    progress = progress,
                    onCommit = { rating, requeue -> commit(current, rating, requeue) },
                    // The grade itself answers with its sound; the commit is silent.
                    onGradeTone = { rating -> sfx.play(rating.studySound()) },
                    onWrongTone = { sfx.play(Sound.Again) },
                    onReveal = { feedback.reveal(view) },
                    onWrong = { feedback.wrong(view) },
                    onAutoPlay = { url -> player.play(url) },
                    onPrefetch = { url -> player.prefetch(url) },
                    onSelect = { text, _ -> promo = text },
                )
                }
            }
        }
    }

    promo?.let { chosen ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { promo = null },
            )
            GlassTranslateCard(
                source = chosen,
                translation = promoTranslation,
                failure = promoFailure,
                loading = promoLoading,
                hazeState = hazeState,
                onCopy = {
                    promoTranslation?.let { clipboard.setText(AnnotatedString(it)) }
                },
                onCopySource = { clipboard.setText(AnnotatedString(chosen)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 96.dp)
                    // Consume taps on the card itself; otherwise the scrim
                    // behind it treats a tap on the translation as "dismiss".
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { },
            )
        }
    }

    val reviewing = reviewIndex?.let { history.getOrNull(it) }
    if (reviewing != null) {
        CompositionLocalProvider(LocalSpeechActions provides speechActions) {
            ReviewOverlay(
                word = reviewing.first,
                rating = reviewing.second,
                entry = entryFor(reviewing.first),
                index = reviewIndex ?: 0,
                total = history.size,
                canOlder = (reviewIndex ?: 0) > 0,
                onOlder = { reviewIndex = (reviewIndex ?: 0) - 1 },
                onClose = { reviewIndex = null },
            )
        }
    }
    }
}

private fun Rating.label(): String = when (this) {
    Rating.Easy -> "秒懂"
    Rating.Good -> "认识"
    Rating.Hard -> "模糊"
    Rating.Again -> "忘记"
}

/**
 * A word that was already committed, opened read-only. It never shows the grade
 * face again: the rating is history, not a question.
 */
@Composable
private fun ReviewOverlay(
    word: String,
    rating: Rating,
    entry: WordEntry,
    index: Int,
    total: Int,
    canOlder: Boolean,
    onOlder: () -> Unit,
    onClose: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Box(modifier = Modifier.fillMaxSize()) {
        // Sits under the content: taps on empty paper stop here instead of
        // reaching the live card below.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(wl.surface)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes.forEach { it.consume() }
                        }
                    }
                },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "回顾 ${index + 1}/$total",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = wl.ink,
                )
                Spacer(Modifier.weight(1f))
                if (canOlder) {
                    Text(
                        text = "更早",
                        fontFamily = SerifFamily,
                        fontSize = 12.5.sp,
                        color = wl.inkMuted,
                        modifier = Modifier
                            .wlPress(onClick = onOlder)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Text(
                    text = "回到当前",
                    fontFamily = SerifFamily,
                    fontSize = 12.5.sp,
                    color = wl.ink,
                    modifier = Modifier
                        .wlPress(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (wl.isDark) wl.surfaceAlt else Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = word,
                            fontFamily = SerifFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = wl.ink,
                        )
                        Spacer(Modifier.width(8.dp))
                        entry.audio.firstOrNull()?.let { clip ->
                            SpeechButton(
                                text = word,
                                fallbackUrl = clip.url,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    entry.ipa.firstOrNull()?.let { ipa ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = ipa.text,
                            fontFamily = SerifFamily,
                            fontSize = 14.sp,
                            color = wl.inkMuted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "当时选了 ${rating.label()}",
                        fontSize = 11.5.sp,
                        color = wl.inkMuted,
                    )
                    Spacer(Modifier.height(18.dp))
                    entry.senses.take(3).forEach { sense ->
                        Row(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text(
                                text = sense.partOfSpeech,
                                fontFamily = SerifFamily,
                                fontSize = 10.5.sp,
                                lineHeight = 21.sp,
                                color = wl.inkMuted,
                                modifier = Modifier.width(54.dp),
                            )
                            Text(
                                text = sense.definition,
                                fontSize = 13.5.sp,
                                lineHeight = 21.sp,
                                color = wl.ink,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/**
 * The horizontal ring's live state. Kept in one object so the gesture coroutine
 * always reads the current index and offset, never a value captured by an older
 * composition.
 */
private fun examplesOf(details: StudyDetails?, entry: WordEntry): List<StudyExample> =
    details?.examples.orEmpty().ifEmpty {
        entry.senses.flatMap { sense -> sense.examples.map { StudyExample(it, "") } }.take(5)
    }

/** Page 0 is the entry (with the first example); page n is examples[n]. */
private fun pageCountFor(examples: List<StudyExample>): Int = examples.size.coerceAtLeast(2)

private fun exampleForPage(examples: List<StudyExample>, page: Int): StudyExample? =
    if (page == 0) examples.firstOrNull() else examples.getOrNull(page)

@Composable
private fun StudyCard(
    item: StudyItem,
    entry: WordEntry,
    presentation: Int,
    progress: Float,
    onCommit: (Rating, Boolean) -> Unit,
    onGradeTone: (Rating) -> Unit,
    onWrongTone: () -> Unit,
    onReveal: () -> Unit,
    onWrong: () -> Unit,
    onAutoPlay: (String) -> Unit,
    onPrefetch: (String) -> Unit,
    onSelect: (String, Rect) -> Unit,
) {
    val wl = WordloomTheme.colors
    val speech = LocalSpeechActions.current

    var layer by remember(item.word, presentation) { mutableStateOf(Layer.Word) }
    // Once a grade is given it is locked: there is no path back to the grade
    // face, and no way to rate the same word twice.
    var pending by remember(item.word, presentation) { mutableStateOf<Rating?>(null) }
    var details by remember(item.word) { mutableStateOf<StudyDetails?>(null) }
    var detailsFailed by remember(item.word) { mutableStateOf(false) }

    val examples = remember(details, entry) { examplesOf(details, entry) }
    val wordAudioUrl = entry.audio.firstOrNull()?.url

    // AI first, so a brand-new book's first word is voiced even before its entry
    // (and its human clip URL) has been fetched. The old URL remains the fallback.
    var wordSpoken by remember(item.word) { mutableStateOf(false) }
    LaunchedEffect(item.word, wordAudioUrl, speech?.enabled) {
        if (wordSpoken) return@LaunchedEffect
        if (speech != null && speech.enabled) {
            wordSpoken = true
            speech.speak(item.word, wordAudioUrl) { state ->
                if (state is SpeechState.Failed) wordSpoken = false
            }
        } else if (!wordAudioUrl.isNullOrBlank()) {
            wordSpoken = true
            onAutoPlay(wordAudioUrl)
        }
    }

    LaunchedEffect(item.word) {
        details = StudyContent.details(item.word)
        detailsFailed = details == null
    }

    // Warm the current card's word and the next few example sentences. With AI
    // enabled this is synthesis, not a URL download; with AI off it is the old
    // human/Youdao clip prefetch.
    LaunchedEffect(item.word, speech?.enabled) {
        if (speech != null && speech.enabled) speech.prefetch(item.word)
    }
    LaunchedEffect(details, speech?.enabled) {
        if (speech != null && speech.enabled) {
            details?.examples?.take(2)?.forEach { example -> speech.prefetch(example.en) }
        } else {
            details?.examples?.take(3)?.forEach { example ->
                example.audioUrl.takeIf { it.isNotBlank() }?.let(onPrefetch)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        val cardWidth = minOf(maxWidth * 0.92f, 360.dp)
        val cardHeight = minOf(maxHeight * 0.88f, 500.dp)

        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight),
        ) {
            // The session's progress is a pen stroke around the card; the ring
            // is drawn over the paper edge, not used as a border.
            val ringProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                label = "studyRing",
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (wl.isDark) wl.surfaceAlt else Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                AnimatedContent(
                    targetState = layer,
                    transitionSpec = {
                        val spec = tween<androidx.compose.ui.unit.IntOffset>(
                            durationMillis = 240,
                            easing = FastOutSlowInEasing,
                        )
                        if (targetState == Layer.Chinese || initialState == Layer.Chinese) {
                            val down = targetState == Layer.Chinese
                            (slideInVertically(spec) { if (down) it else -it }) togetherWith
                                (slideOutVertically(spec) { if (down) -it else it })
                        } else {
                            // Full-height travel: a shallow slide leaves both
                            // faces legible on top of each other for a beat.
                            (slideInVertically(spec) { it }) togetherWith
                                (slideOutVertically(spec) { -it })
                        }
                    },
                    label = "studyLayer",
                ) { currentLayer ->
                    when (currentLayer) {
                        Layer.Word -> WordFace(
                            entry = entry,
                            onGrade = { rating ->
                                if (pending == null) {
                                    pending = rating
                                    onGradeTone(rating)
                                    onReveal()
                                    layer = Layer.English
                                }
                            },
                        )

                        Layer.English -> RatedRing(
                            entry = entry,
                            details = details,
                            examples = examples,
                            resetKey = presentation.toLong(),
                            onSelect = onSelect,
                            onAutoPlay = onAutoPlay,
                            onToggleChinese = { layer = Layer.Chinese },
                            onNext = {
                                val rating = pending ?: Rating.Good
                                onCommit(rating, rating == Rating.Again || rating == Rating.Hard)
                            },
                            onWrong = {
                                onWrongTone()
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )

                        Layer.Chinese -> ChineseFace(
                            entry = entry,
                            details = details,
                            failed = detailsFailed,
                            onToggleEnglish = { layer = Layer.English },
                            onNext = {
                                val rating = pending ?: Rating.Good
                                onCommit(rating, rating == Rating.Again || rating == Rating.Hard)
                            },
                            onWrong = {
                                onWrongTone()
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (ringProgress > 0.001f) {
                    val path = ringPath(size.width, size.height, 24.dp.toPx(), 1.5.dp.toPx())
                    val measure = PathMeasure()
                    measure.setPath(path, false)
                    val segment = Path()
                    measure.getSegment(0f, measure.length * ringProgress, segment, true)
                    drawPath(
                        path = segment,
                        color = wl.ink.copy(alpha = 0.09f),
                        style = Stroke(
                            width = 8.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                    drawPath(
                        path = segment,
                        color = wl.ink.copy(alpha = 0.78f),
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                    val head = measure.getPosition(measure.length * ringProgress)
                    drawCircle(color = wl.ink.copy(alpha = 0.14f), radius = 6.dp.toPx(), center = head)
                    drawCircle(color = wl.ink.copy(alpha = 0.85f), radius = 2.9.dp.toPx(), center = head)
                }
            }
        }
    }
}

/**
 * Page 0 is the word card, the rest are its examples. The pages follow the
 * finger; releasing past about an eighth of the width — or with a quick flick —
 * settles on the neighbour, and
 * since the ring wraps, the last example leads back to the word card.
 */
private fun ringPath(width: Float, height: Float, radius: Float, inset: Float): Path {
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val r = radius.coerceAtMost(minOf(right - left, bottom - top) / 2f)
    return Path().apply {
        moveTo((left + right) / 2f, top)
        lineTo(right - r, top)
        arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)
        lineTo(right, bottom - r)
        arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
        lineTo(left + r, bottom)
        arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
        lineTo(left, top + r)
        arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
        lineTo((left + right) / 2f, top)
        close()
    }
}

@Composable
private fun RatedRing(
    entry: WordEntry,
    details: StudyDetails?,
    examples: List<StudyExample>,
    resetKey: Long,
    onSelect: (String, Rect) -> Unit,
    onAutoPlay: (String) -> Unit,
    onToggleChinese: () -> Unit,
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val count = pageCountFor(examples)
    // A huge virtual count plus modulo content is an infinite ring: the
    // pager keeps native fling/preload and can always be swiped both ways.
    val virtualCount = Int.MAX_VALUE
    val start = remember(count) {
        val middle = virtualCount / 2
        middle - middle % count
    }
    val pager = rememberPagerState(initialPage = start) { virtualCount }

    // The heavy entry face keeps one composition as it moves between virtual
    // cycles, instead of being re-composed at every loop.
    val mainContent = remember(entry, details, examples, resetKey, onSelect) {
        movableContentOf {
            MainPage(entry, details, examples, resetKey, onSelect)
        }
    }

    // No pager state is read in this body. The dots and audio read it in their
    // own composables so a page flip never recomposes the pager or its pages.
    RingAudioEffect(pager, examples, onAutoPlay)

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            beyondViewportPageCount = 1,
            userScrollEnabled = count > 1,
        ) { virtual ->
            val page = Math.floorMod(virtual, count)
            Box(modifier = Modifier.fillMaxSize()) {
                if (page == 0) {
                    // A movable composition can only live in one place; with a
                    // two-page ring both neighbours are the main page, so keep
                    // the regular composition for count 1–2 instead.
                    if (count >= 3) {
                        mainContent()
                    } else {
                        MainPage(entry, details, examples, resetKey, onSelect)
                    }
                } else {
                    val example = exampleForPage(examples, page)
                    if (example != null) {
                        ExamplePage(entry = entry, example = example)
                    } else {
                        PendingExamplePage(details)
                    }
                }
            }
        }

        PagerDots(pager, count)

        Spacer(Modifier.height(6.dp))
        RatedActions(
            alternateLabel = "中文",
            onAlternate = onToggleChinese,
            onNext = onNext,
            onWrong = onWrong,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PendingExamplePage(details: StudyDetails?) {
    val wl = WordloomTheme.colors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (details == null) "正在取例句…" else "没有更多例句",
            fontSize = 12.5.sp,
            color = wl.inkMuted,
        )
    }
}

@Composable
private fun MainPage(
    entry: WordEntry,
    details: StudyDetails?,
    examples: List<StudyExample>,
    resetKey: Long,
    onSelect: (String, Rect) -> Unit,
) {
    MainFaceContent(
        entry = entry,
        details = details,
        example = exampleForPage(examples, 0),
        resetKey = resetKey,
        onSelect = onSelect,
    )
}

@Composable
private fun PagerDots(pager: PagerState, count: Int) {
    val wl = WordloomTheme.colors
    val page by remember(pager, count) {
        derivedStateOf { Math.floorMod(pager.currentPage, count) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.5.dp)
                    .size(if (index == page) 6.dp else 4.dp)
                    .background(if (index == page) wl.ink else wl.outline, CircleShape),
            )
        }
    }
}

/**
 * Page audio keys off the settled page and waits 250 ms, so a fast second swipe
 * cancels the pending sentence. Prefetch only the next example, not a batch.
 */
@Composable
private fun RingAudioEffect(
    pager: PagerState,
    examples: List<StudyExample>,
    onAutoPlay: (String) -> Unit,
) {
    val speech = LocalSpeechActions.current
    var lastKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pager, examples, speech?.enabled) {
        snapshotFlow { pager.settledPage to pager.isScrollInProgress }.collectLatest { (settled, scrolling) ->
            if (scrolling) return@collectLatest
            delay(250)
            if (pager.isScrollInProgress) return@collectLatest
            val count = pageCountFor(examples)
            val page = Math.floorMod(settled, count)
            val example = exampleForPage(examples, page) ?: return@collectLatest
            val sentence = example.en
            if (sentence.isBlank()) return@collectLatest
            val key = "$settled:$sentence"
            if (key != lastKey) {
                lastKey = key
                if (speech != null && speech.enabled) {
                    speech.speak(sentence, example.audioUrl.takeIf { it.isNotBlank() }) { state ->
                        if (state is SpeechState.Failed) lastKey = null
                    }
                } else {
                    example.audioUrl.takeIf { it.isNotBlank() }?.let(onAutoPlay)
                }
            }
            // Warm only the next example, after the current page has settled.
            val next = exampleForPage(examples, Math.floorMod(page + 1, count))?.en
            if (!next.isNullOrBlank() && next != sentence && speech != null && speech.enabled) {
                speech.prefetch(next)
            }
        }
    }
}

@Composable
private fun RatedActions(
    alternateLabel: String,
    onAlternate: () -> Unit,
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = alternateLabel,
            fontFamily = SerifFamily,
            fontSize = 12.5.sp,
            color = wl.inkMuted,
            modifier = Modifier
                .wlPress(onClick = onAlternate)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Text(
            text = "下一词",
            fontFamily = SerifFamily,
            fontSize = 15.sp,
            color = wl.ink,
            modifier = Modifier
                .wlPress(onClick = onNext)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Text(
            text = "记错了",
            fontFamily = SerifFamily,
            fontSize = 15.sp,
            color = wl.coral,
            modifier = Modifier
                .wlPress(onClick = onWrong)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun WordFace(entry: WordEntry, onGrade: (Rating) -> Unit) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 26.dp),
    ) {
        Spacer(Modifier.height(26.dp))
        Text(
            text = entry.word,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            color = wl.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            entry.ipa.firstOrNull()?.let { ipa ->
                Text(
                    text = ipa.text,
                    fontFamily = SerifFamily,
                    fontSize = 15.sp,
                    color = wl.inkMuted,
                )
                Spacer(Modifier.width(8.dp))
            }
            entry.audio.firstOrNull()?.let { clip ->
                SpeechButton(text = entry.word, fallbackUrl = clip.url)
            }
        }
        Spacer(Modifier.weight(1f))
        GradeRow(onGrade)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun GradeRow(onGrade: (Rating) -> Unit) {
    val wl = WordloomTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GradeOption("秒懂", Modifier.weight(1f)) { onGrade(Rating.Easy) }
            GradeOption("认识", Modifier.weight(1f)) { onGrade(Rating.Good) }
            GradeOption("模糊", Modifier.weight(1f)) { onGrade(Rating.Hard) }
            GradeOption("忘记", Modifier.weight(1f)) { onGrade(Rating.Again) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "凭第一反应，再揭晓",
            fontFamily = SerifFamily,
            fontSize = 10.5.sp,
            color = wl.inkMuted.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Four answers, one voice. No colours, no rules, no second labels — the words
 * themselves carry the scale, and the only thing a touch does is sink the ink.
 */
@Composable
private fun GradeOption(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .wlPress(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = SerifFamily,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp,
            color = WordloomTheme.colors.ink.copy(alpha = 0.86f),
        )
    }
}

@Composable
private fun MainFaceContent(
    entry: WordEntry,
    details: StudyDetails?,
    example: StudyExample?,
    resetKey: Long,
    onSelect: (String, Rect) -> Unit,
) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 22.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.word,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = wl.ink,
            )
            Spacer(Modifier.width(8.dp))
            entry.audio.firstOrNull()?.let { clip ->
                SpeechButton(
                    text = entry.word,
                    fallbackUrl = clip.url,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        entry.ipa.firstOrNull()?.let { ipa ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = ipa.text,
                fontFamily = SerifFamily,
                fontSize = 13.sp,
                color = wl.inkMuted,
            )
        }

        // The Chinese side first: one line the reader can check against.
        val chinese = details?.senses.orEmpty().take(2)
        if (chinese.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = chinese.joinToString("；"),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = wl.ink.copy(alpha = 0.82f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The first example comes before the long blurb so it is visible
        // without scrolling; the ring carries the remaining examples.
        example?.let { ex ->
            Spacer(Modifier.height(14.dp))
            HighlightedSentence(
                sentence = ex.en,
                word = entry.word,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            if (ex.zh.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ex.zh,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = wl.inkMuted,
                )
            }
        }

        entry.shortBlurb?.takeIf { it.isNotBlank() }?.let { blurb ->
            Spacer(Modifier.height(14.dp))
            SelectableText(
                text = blurb,
                style = TextStyle(
                    fontFamily = SerifFamily,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                ),
                color = wl.ink,
                onSelection = onSelect,
                resetKey = resetKey,
            )
        }

        Spacer(Modifier.height(14.dp))
        entry.senses.take(2).forEach { sense ->
            Row(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = sense.partOfSpeech,
                    fontFamily = SerifFamily,
                    fontSize = 10.5.sp,
                    lineHeight = 21.sp,
                    color = wl.inkMuted,
                    maxLines = 1,
                    modifier = Modifier.width(34.dp),
                )
                Text(
                    text = sense.definition,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = wl.ink,
                )
            }
        }

        if (entry.wordFamily.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "同族 · " + entry.wordFamily.take(4).joinToString(" · "),
                fontSize = 10.5.sp,
                color = wl.inkMuted.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChineseFace(
    entry: WordEntry,
    details: StudyDetails?,
    failed: Boolean,
    onToggleEnglish: () -> Unit,
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 22.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.word,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = wl.ink,
            )
            Spacer(Modifier.width(8.dp))
            entry.audio.firstOrNull()?.let { clip ->
                SpeechButton(
                    text = entry.word,
                    fallbackUrl = clip.url,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        details?.senses?.take(3)?.forEach { line ->
            Text(
                text = line,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = wl.ink,
            )
            Spacer(Modifier.height(6.dp))
        }

        details?.examples?.take(2)?.forEach { example ->
            Spacer(Modifier.height(4.dp))
            HighlightedSentence(
                sentence = example.en,
                word = entry.word,
                fontSize = 13.sp,
            )
            Text(
                text = example.zh,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = wl.inkMuted,
            )
            Spacer(Modifier.height(8.dp))
        }

        details?.phrases?.take(2)?.forEach { (phrase, meaning) ->
            Row {
                Text(
                    text = phrase,
                    fontFamily = SerifFamily,
                    fontSize = 12.sp,
                    color = wl.ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = meaning,
                    fontSize = 11.5.sp,
                    color = wl.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        if (details == null) {
            Text(
                text = if (failed) "中文暂不可用" else "正在取中文…",
                fontSize = 12.sp,
                color = wl.inkMuted,
            )
        }

    }
    RatedActions(
        alternateLabel = "英文",
        onAlternate = onToggleEnglish,
        onNext = onNext,
        onWrong = onWrong,
    )
    Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExamplePage(
    entry: WordEntry,
    example: StudyExample,
    modifier: Modifier = Modifier,
) {
    val wl = WordloomTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.word,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = wl.ink,
            )
            Spacer(Modifier.width(6.dp))
            SpeechButton(
                text = example.en,
                fallbackUrl = example.audioUrl.takeIf { it.isNotBlank() },
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        HighlightedSentence(
            sentence = example.en,
            word = entry.word,
            fontSize = 20.sp,
            lineHeight = 29.sp,
        )
        if (example.zh.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = example.zh,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = wl.inkMuted,
            )
        }
    }
}

@Composable
private fun HighlightedSentence(
    sentence: String,
    word: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit = fontSize * 1.45f,
) {
    val wl = WordloomTheme.colors
    val annotated: AnnotatedString = remember(sentence, word) {
        val lower = sentence.lowercase()
        val target = word.lowercase()
        buildAnnotatedString {
            append(sentence)
            var start = lower.indexOf(target)
            while (start >= 0 && target.isNotEmpty()) {
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD9542B),
                    ),
                    start,
                    start + target.length,
                )
                start = lower.indexOf(target, start + target.length)
            }
        }
    }
    Text(
        text = annotated,
        fontFamily = SerifFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = wl.ink,
    )
}

@Composable
private fun EmptyState(onBack: () -> Unit) {
    val wl = WordloomTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "今天没有要背的词",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = wl.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text("先去查几个词，它们会进到这里", fontSize = 12.sp, color = wl.inkMuted)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "返回",
                style = MaterialTheme.typography.labelLarge,
                color = wl.inkMuted,
                modifier = Modifier
                    .wlPress(onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DoneState(
    total: Int,
    hasSpelling: Boolean,
    spelled: Boolean,
    spellScore: Int,
    spellTotal: Int,
    onStartSpelling: () -> Unit,
    onBack: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (spelled && spellTotal > 0 && spellScore == spellTotal) {
                InkStamp(stampSize = 64.dp)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = "今天的词背完了",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = wl.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text("本轮 $total 个词", fontSize = 12.sp, color = wl.inkMuted)
            if (spelled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "拼写 $spellScore/${if (spellTotal > 0) spellTotal else total}",
                    fontSize = 12.sp,
                    color = wl.inkMuted,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (hasSpelling && !spelled) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = wl.ink,
                    modifier = Modifier
                        .width(180.dp)
                        .wlPress(onClick = onStartSpelling),
                ) {
                    Text(
                        text = "拼写检查",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = wl.surface,
                        modifier = Modifier.padding(vertical = 13.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = "返回",
                style = MaterialTheme.typography.labelLarge,
                color = wl.inkMuted,
                modifier = Modifier
                    .wlPress(onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SpellingRound(
    words: List<WordEntry>,
    onSpeak: (String) -> Unit,
    onSound: (Boolean) -> Unit,
    onFinish: (Int) -> Unit,
) {
    if (words.isEmpty()) {
        LaunchedEffect(Unit) { onFinish(0) }
        return
    }
    val wl = WordloomTheme.colors
    val view = LocalView.current
    var index by remember { mutableIntStateOf(0) }
    var typed by remember(index) { mutableStateOf("") }
    var checked by remember(index) { mutableStateOf<Boolean?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var details by remember(index) { mutableStateOf<StudyDetails?>(null) }
    val focus = remember { FocusRequester() }
    val entry = words[index]
    // Spelling works on letters only: "well-known" is nine letters to type,
    // and the hyphen is shown, not entered.
    val letters = remember(entry.word) { entry.word.filter { it.isLetter() } }
    val letterPositions = remember(entry.word) {
        var seen = 0
        entry.word.map { ch ->
            if (ch.isLetter()) {
                val position = seen
                seen += 1
                position
            } else {
                -1
            }
        }
    }

    LaunchedEffect(index) {
        details = StudyContent.details(entry.word)
        focus.requestFocus()
    }

    fun advance() {
        if (index + 1 >= words.size) {
            onFinish(correctCount)
        } else {
            index += 1
            typed = ""
            checked = null
        }
    }

    fun check() {
        if (checked != null) return
        val ok = typed.equals(letters.drop(1), ignoreCase = true)
        checked = ok
        onSound(ok)
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) {
                if (ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
            } else {
                if (ok) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.LONG_PRESS
            },
        )
        if (ok) {
            correctCount += 1
            onSpeak(entry.word)
        }
    }

    LaunchedEffect(checked) {
        if (checked == true) {
            delay(700)
            advance()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "拼写",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = wl.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${index + 1}/${words.size}",
                fontSize = 12.sp,
                color = wl.inkMuted,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = minOf(maxWidth * 0.92f, 360.dp)
            val cardHeight = minOf(maxHeight * 0.82f, 460.dp)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (wl.isDark) wl.surfaceAlt else Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    val meaning = details?.senses?.firstOrNull()
                        ?: entry.senses.firstOrNull()?.definition.orEmpty()
                    Text(
                        text = meaning,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = wl.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "首字母 ${letters.firstOrNull()?.uppercase() ?: ""} · ${letters.length} 个字母",
                            fontSize = 11.5.sp,
                            color = wl.inkMuted,
                        )
                        Spacer(Modifier.width(8.dp))
                        entry.audio.firstOrNull()?.let { clip ->
                            SpeechButton(
                                text = entry.word,
                                fallbackUrl = clip.url,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(26.dp))

                    // Letter slots; a transparent text field sits on top and
                    // takes the keyboard input.
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            entry.word.forEachIndexed { i, ch ->
                                val position = letterPositions[i]
                                val fixed = position < 0
                                val shown = when {
                                    fixed -> ch.toString()
                                    position == 0 -> ch.toString()
                                    position - 1 < typed.length -> typed[position - 1].toString()
                                    else -> ""
                                }
                                val wrong = !fixed && position > 0 && position - 1 < typed.length &&
                                    typed[position - 1].lowercaseChar() != ch.lowercaseChar()
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (wrong) wl.coral.copy(alpha = 0.12f) else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, wl.outline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = shown,
                                            fontFamily = SerifFamily,
                                            fontSize = 18.sp,
                                            color = when {
                                                wrong -> wl.coral
                                                fixed -> wl.inkMuted
                                                else -> wl.ink
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { focus.requestFocus() },
                        )
                        BasicTextField(
                            value = typed,
                            onValueChange = { text ->
                                if (checked == null) {
                                    typed = text.filter { it.isLetter() }
                                        .take((letters.length - 1).coerceAtLeast(0))
                                        .lowercase()
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                            cursorBrush = SolidColor(Color.Transparent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done,
                                autoCorrect = false,
                            ),
                            keyboardActions = KeyboardActions(onDone = { check() }),
                            modifier = Modifier
                                .matchParentSize()
                                .focusRequester(focus)
                                .alpha(0.02f),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    when (checked) {
                        true -> Row(verticalAlignment = Alignment.CenterVertically) {
                            InkStamp()
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "写对了",
                                fontFamily = SerifFamily,
                                fontSize = 15.sp,
                                color = Color(0xFF0FA646),
                            )
                        }
                        false -> Column {
                            Text(
                                text = "应该是 ${entry.word}",
                                fontFamily = SerifFamily,
                                fontSize = 15.sp,
                                color = wl.coral,
                            )
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = wl.surfaceAlt,
                                modifier = Modifier
                                    .width(140.dp)
                                    .wlPress(onClick = { advance() }),
                            ) {
                                Text(
                                    text = "下一个",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = wl.ink,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                        null -> {
                            val ready = typed.length >= letters.length - 1
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (ready) wl.ink else wl.surfaceAlt,
                                modifier = Modifier
                                    .width(150.dp)
                                    .wlPress(enabled = ready, onClick = { check() }),
                            ) {
                                Text(
                                    text = "检查",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (ready) wl.surface else wl.inkMuted,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The reward: a seal pressed onto the page. It lands with a spring (a stamp has
 * weight), the check draws itself in, and six ink flecks leave the edge as it
 * lands. No confetti, no stars — it belongs to a book.
 */
@Composable
private fun InkStamp(modifier: Modifier = Modifier, stampSize: androidx.compose.ui.unit.Dp = 58.dp) {
    val wl = WordloomTheme.colors
    val stamp = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        stamp.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 430f))
    }
    Canvas(
        modifier = modifier
            .size(stampSize)
            .graphicsLayer {
                rotationZ = -8f
                val s = 0.86f + 0.22f * stamp.value
                scaleX = s
                scaleY = s
            },
    ) {
        val p = stamp.value
        val alpha = p.coerceIn(0f, 1f)
        val ink = wl.ink.copy(alpha = 0.88f * alpha)
        val radius = size.minDimension * 0.46f
        drawCircle(color = ink, radius = radius, style = Stroke(width = 2.2.dp.toPx()))

        val check = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.67f)
            lineTo(size.width * 0.73f, size.height * 0.35f)
        }
        val measure = PathMeasure().apply { setPath(check, false) }
        val drawn = Path()
        measure.getSegment(
            0f,
            measure.length * ((p - 0.15f) / 0.55f).coerceIn(0f, 1f),
            drawn,
            true,
        )
        drawPath(drawn, color = ink, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))

        val fade = (1f - p).coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        repeat(6) { index ->
            val angle = (index / 6f) * 2f * PI.toFloat() + 0.35f
            val from = radius * 1.02f
            val to = from + size.minDimension * 0.16f * (1f - fade)
            drawLine(
                color = wl.ink.copy(alpha = 0.45f * fade),
                start = center + Offset(cos(angle) * from, sin(angle) * from),
                end = center + Offset(cos(angle) * to, sin(angle) * to),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
