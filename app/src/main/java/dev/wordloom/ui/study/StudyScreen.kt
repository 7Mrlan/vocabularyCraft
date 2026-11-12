package dev.wordloom.ui.study
import android.view.HapticFeedbackConstants
import android.os.Build
import android.media.SoundPool
import android.content.Context

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.animation.core.animate
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.annotation.RawRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.platform.LocalDensity
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
import dev.wordloom.data.translate.StudyExample
import dev.wordloom.srs.Rating
import dev.wordloom.ui.components.SpeakerButton
import dev.wordloom.ui.components.rememberAudioPlayer
import dev.wordloom.ui.feedback.rememberAppFeedback
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.wlPress
import dev.wordloom.ui.theme.WordloomTheme
import kotlin.math.abs
import kotlin.math.min

private enum class Layer { Word, English, Chinese }

/** A quick flick turns the page even when it travels less than the distance threshold. */
private const val FLING_DP_PER_SECOND = 700f

@Immutable
private data class StudyItem(val word: String, val isNew: Boolean)

/**
 * The review's voice. Four grades get four different gestures — a glass drop, a
 * wooden tick, a page turn and a closing book — so the answer is heard before
 * the screen explains it. Spelling has its own pair.
 */
private enum class StudySound(@RawRes val res: Int, val volume: Float) {
    Easy(R.raw.grade_easy, 0.60f),
    Good(R.raw.grade_good, 0.55f),
    Hard(R.raw.grade_hard, 0.45f),
    Again(R.raw.grade_again, 0.35f),
    SpellRight(R.raw.spell_right, 0.65f),
    SpellWrong(R.raw.spell_wrong, 0.35f),
}

private class SoundBank(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(2).build()
    private val ids = HashMap<StudySound, Int>()

    init {
        StudySound.entries.forEach { ids[it] = pool.load(context, it.res, 1) }
    }

    fun play(sound: StudySound) {
        ids[sound]?.let { pool.play(it, sound.volume, sound.volume, 1, 0, 1f) }
    }

    fun release() = pool.release()
}

@Composable
private fun rememberSoundBank(): SoundBank {
    val context = LocalContext.current
    val bank = remember { SoundBank(context.applicationContext) }
    DisposableEffect(bank) { onDispose { bank.release() } }
    return bank
}

private fun Rating.studySound(): StudySound = when (this) {
    Rating.Easy -> StudySound.Easy
    Rating.Good -> StudySound.Good
    Rating.Hard -> StudySound.Hard
    Rating.Again -> StudySound.Again
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
) {
    val wl = WordloomTheme.colors
    val view = LocalView.current
    val feedback = rememberAppFeedback()
    val player = rememberAudioPlayer()
    val sfx = rememberSoundBank()
    val hazeState = remember { HazeState() }
    val clipboard = LocalClipboardManager.current
    // A word picked out of the card with a long press, translated in the same
    // glass card the word page uses.
    var promo by remember { mutableStateOf<String?>(null) }
    var promoTranslation by remember { mutableStateOf<String?>(null) }
    var promoLoading by remember { mutableStateOf(false) }
    LaunchedEffect(promo) {
        val text = promo ?: return@LaunchedEffect
        promoLoading = true
        promoTranslation = null
        TranslationService.translate(text).fold(
            onSuccess = { promoTranslation = it },
            onFailure = { promoTranslation = null },
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

    val dueItems = remember(dueCards) {
        dueCards.take(60).map { StudyItem(it.word.lowercase(), false) }
    }
    val newItems = remember(newWords, cards) {
        StudyQueue.newWords(newWords, cards).map { StudyItem(it, true) }
    }

    var queue by remember { mutableStateOf<List<StudyItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var completed by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(dueItems, newItems) {
        // The flows start empty on a cold restore; the first real emission must
        // be allowed to replace the empty queue while nothing was answered yet.
        if (!started || (queue.isEmpty() && completed == 0)) {
            queue = (dueItems + newItems).distinctBy { it.word.lowercase() }
            total = queue.size
            started = true
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
        val key = item.word.lowercase()
        val firstToday = seenToday.add(key)
        if (firstToday) {
            // Only a real commit can be undone: a requeued word answered a
            // second time writes nothing, so it must not become the target.
            lastCommitted = item
            history.add(item.word to rating)
            completed += 1
            onRate(entryFor(item.word), rating, item.isNew)
            if (rating == Rating.Good || rating == Rating.Easy) {
                spellWords.add(entryFor(item.word))
            }
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
        queue.take(3).forEach { onNeedEntry(it.word) }
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

            current == null && spelling -> SpellingRound(
                words = spellWords,
                onPlay = { url -> player.play(url) },
                onSound = { correct ->
                    sfx.play(if (correct) StudySound.SpellRight else StudySound.SpellWrong)
                },
                onFinish = { correct ->
                    spellScore = correct
                    spellTaken = true
                    spelling = false
                },
            )

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

            else -> StudyCard(
                item = current,
                entry = entryFor(current.word),
                presentation = presentation,
                progress = progress,
                onCommit = { rating, requeue -> commit(current, rating, requeue) },
                // The grade itself answers with its sound; the commit is silent.
                onGradeTone = { rating -> sfx.play(rating.studySound()) },
                onWrongTone = { sfx.play(StudySound.Again) },
                onReveal = { feedback.reveal(view) },
                onWrong = { feedback.wrong(view) },
                onAutoPlay = { url -> player.play(url) },
                onPrefetch = { url -> player.prefetch(url) },
                onSelect = { text, _ -> promo = text },
            )
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
                failure = null,
                loading = promoLoading,
                hazeState = hazeState,
                onCopy = {
                    promoTranslation?.let { clipboard.setText(AnnotatedString(it)) }
                },
                onCopySource = { clipboard.setText(AnnotatedString(chosen)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 96.dp),
            )
        }
    }

    val reviewing = reviewIndex?.let { history.getOrNull(it) }
    if (reviewing != null) {
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
                            SpeakerButton(url = clip.url, modifier = Modifier.size(30.dp))
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
private class RingState {
    var index by mutableIntStateOf(0)
    var offset by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(1f)
    var job: Job? = null
}

private fun examplesOf(details: StudyDetails?, entry: WordEntry): List<StudyExample> =
    details?.examples.orEmpty().ifEmpty {
        entry.senses.flatMap { sense -> sense.examples.map { StudyExample(it, "") } }.take(5)
    }

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
    val density = LocalDensity.current.density
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var layer by remember(item.word, presentation) { mutableStateOf(Layer.Word) }
    // Once a grade is given it is locked: there is no path back to the grade
    // face, and no way to rate the same word twice.
    var pending by remember(item.word, presentation) { mutableStateOf<Rating?>(null) }
    var details by remember(item.word) { mutableStateOf<StudyDetails?>(null) }
    var detailsFailed by remember(item.word) { mutableStateOf(false) }

    val examples = remember(details, entry) { examplesOf(details, entry) }
    val pageCount = 1 + examples.size

    // One horizontal ring: page 0 is the word card, pages 1..n are examples.
    val ring = remember(item.word, presentation) { RingState() }
    val page = ring.index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val wordAudioUrl = entry.audio.firstOrNull()?.url
    val gestureVelocity = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }

    // The clip may arrive after the card is first composed (cold cache): key the
    // autoplay on the URL, not just the word, or the very first word stays silent.
    LaunchedEffect(item.word, wordAudioUrl) {
        if (!wordAudioUrl.isNullOrBlank()) onAutoPlay(wordAudioUrl)
    }

    LaunchedEffect(item.word) {
        details = StudyContent.details(item.word)
        detailsFailed = details == null
    }

    // Warm the first example clips only: the ring keeps three pages composed, so
    // downloading all eight sentences per card was wasted data and IO.
    LaunchedEffect(details) {
        details?.examples?.take(3)?.forEach { example ->
            example.audioUrl.takeIf { it.isNotBlank() }?.let(onPrefetch)
        }
    }

    // Pages 1..n speak their own example when they land. Page 0 is the entry
    // face now, so it stays quiet (the word itself was voiced on arrival).
    // Key on the URL, not the list: `examples` is rebuilt on every recomposition
    // and would otherwise replay the same sentence.
    val pageAudioUrl = if (page == 0) null else examples.getOrNull(page - 1)?.audioUrl
    LaunchedEffect(layer, page, pageAudioUrl) {
        if (layer != Layer.English || pageAudioUrl.isNullOrBlank()) return@LaunchedEffect
        onAutoPlay(pageAudioUrl)
    }

    fun settleRing() {
        val width = ring.width
        val threshold = width * 0.16f
        val vx = gestureVelocity.calculateVelocity().x
        // Velocity is px/s while the threshold is a physical speed: scale by
        // density, or a 3x screen would trigger on a much slower finger.
        val fling = FLING_DP_PER_SECOND * density
        // Distance or speed: a short, fast flick still turns the page.
        val step = when {
            ring.offset < -threshold || vx < -fling -> 1
            ring.offset > threshold || vx > fling -> -1
            else -> 0
        }
        ring.job?.cancel()
        if (step != 0) {
            // Turn on release instead of when the spring ends: a second fast
            // swipe used to cancel the settle and lose the turn entirely.
            ring.index = Math.floorMod(ring.index + step, pageCount)
            // Shift by exactly one page so the incoming page starts where the
            // outgoing neighbour was; the settle then only has to run to rest.
            ring.offset += step * width
        }
        ring.job = scope.launch {
            animate(
                initialValue = ring.offset,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
            ) { value, _ -> ring.offset = value }
            ring.offset = 0f
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

        val cardGestures = Modifier.pointerInput(item.word, layer, pageCount) {
            var totalX = 0f
            var totalY = 0f
            var axis = 0
            detectDragGestures(
                onDragStart = { position ->
                    totalX = 0f
                    totalY = 0f
                    axis = 0
                    // A new touch owns the offset; never fight a running settle.
                    ring.job?.cancel()
                    gestureVelocity.resetTracking()
                    gestureVelocity.addPosition(android.os.SystemClock.uptimeMillis(), position)
                },
                onDrag = { change, amount ->
                    totalX += amount.x
                    totalY += amount.y
                    gestureVelocity.addPosition(change.uptimeMillis, change.position)
                    // Decide on the first movement past the touch slop. A second
                    // 14dp threshold here is what made the card feel stuck.
                    if (axis == 0 && (totalX != 0f || totalY != 0f)) {
                        axis = if (abs(totalX) >= abs(totalY)) 1 else 2
                    }
                    if (axis == 1 && layer == Layer.English) {
                        change.consume()
                        ring.offset = (ring.offset + amount.x).coerceIn(-ring.width, ring.width)
                    }
                },
                onDragEnd = {
                    val t = 56.dp.toPx()
                    if (axis == 1 && layer == Layer.English) {
                        settleRing()
                    } else if (axis == 2) {
                        if (totalY < -t && layer == Layer.English) {
                            layer = Layer.Chinese
                        } else if (totalY > t && layer == Layer.Chinese) {
                            layer = Layer.English
                        }
                    }
                },
                onDragCancel = { ring.offset = 0f },
            )
        }

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
                modifier = Modifier
                    .fillMaxSize()
                    .then(cardGestures),
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
                            page = page,
                            offsetProvider = { ring.offset },
                            onWidth = { ring.width = it },
                            resetKey = presentation.toLong(),
                            onSelect = onSelect,
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
 * finger; releasing past a sixth of the width settles on the neighbour, and
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
    page: Int,
    // Read inside graphicsLayer so a drag invalidates drawing, not composition.
    offsetProvider: () -> Float,
    onWidth: (Float) -> Unit,
    resetKey: Long,
    onSelect: (String, Rect) -> Unit,
    onToggleChinese: () -> Unit,
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val wl = WordloomTheme.colors
    var widthPx by remember { mutableFloatStateOf(1f) }
    val count = 1 + examples.size

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged {
                    widthPx = it.width.toFloat().coerceAtLeast(1f)
                    onWidth(widthPx)
                },
        ) {
            // Both neighbours are always composed off-screen, so the first
            // pixel of a drag never has to lay out a new page.
            RingPage(
                entry = entry,
                details = details,
                examples = examples,
                page = Math.floorMod(page + 1, count),
                resetKey = resetKey,
                onSelect = onSelect,
                modifier = Modifier.graphicsLayer { translationX = offsetProvider() + widthPx },
            )
            RingPage(
                entry = entry,
                details = details,
                examples = examples,
                page = Math.floorMod(page - 1, count),
                resetKey = resetKey,
                onSelect = onSelect,
                modifier = Modifier.graphicsLayer { translationX = offsetProvider() - widthPx },
            )
            RingPage(
                entry = entry,
                details = details,
                examples = examples,
                page = page,
                resetKey = resetKey,
                onSelect = onSelect,
                modifier = Modifier.graphicsLayer { translationX = offsetProvider() },
            )
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
private fun RingPage(
    entry: WordEntry,
    details: StudyDetails?,
    examples: List<StudyExample>,
    page: Int,
    resetKey: Long,
    onSelect: (String, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (page == 0) {
            MainFaceContent(
                entry = entry,
                details = details,
                resetKey = resetKey,
                onSelect = onSelect,
            )
        } else {
            examples.getOrNull(page - 1)?.let { example ->
                ExamplePage(entry = entry, example = example)
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
            entry.audio.firstOrNull()?.let { clip -> SpeakerButton(url = clip.url) }
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
                SpeakerButton(url = clip.url, modifier = Modifier.size(30.dp))
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

        // The examples live in the horizontal ring; repeating the first one here
        // made page 1 an exact duplicate of the main card.
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
                SpeakerButton(url = clip.url, modifier = Modifier.size(30.dp))
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
            example.audioUrl.takeIf { it.isNotBlank() }?.let { url ->
                SpeakerButton(url = url, modifier = Modifier.size(28.dp))
            }
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
    onPlay: (String) -> Unit,
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
            entry.audio.firstOrNull()?.url?.takeIf { it.isNotBlank() }?.let(onPlay)
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
                            SpeakerButton(
                                url = clip.url,
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
