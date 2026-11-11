package dev.wordloom.ui.study

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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
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
import dev.wordloom.ui.theme.WordloomTheme
import kotlin.math.abs
import kotlin.math.min

private enum class Layer { Word, English, Chinese, Examples }

@Immutable
private data class StudyItem(val entry: WordEntry, val isNew: Boolean)

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
    words: List<WordEntry>,
    onBack: () -> Unit,
    onRate: (WordEntry, Rating, Boolean) -> Unit,
    onUndo: () -> Unit,
    onMarkKnown: (WordEntry) -> Unit,
) {
    val wl = WordloomTheme.colors
    val view = LocalView.current
    val feedback = rememberAppFeedback()
    val player = rememberAudioPlayer()

    val dueItems = remember(dueCards, words) {
        dueCards.take(60).mapNotNull { card ->
            words.firstOrNull { it.word.equals(card.word, ignoreCase = true) }
                ?.let { StudyItem(it, false) }
        }
    }
    val newItems = remember(words, dueCards) {
        words.filter { entry -> dueCards.none { it.word.equals(entry.word, ignoreCase = true) } }
            .take(10)
            .map { StudyItem(it, true) }
    }

    var queue by remember { mutableStateOf<List<StudyItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(dueItems, newItems) {
        if (!started) {
            queue = (dueItems + newItems).distinctBy { it.entry.word.lowercase() }
            total = queue.size
            started = true
        }
    }

    val seenToday = remember { mutableSetOf<String>() }
    val spellWords = remember { mutableListOf<WordEntry>() }
    var spelling by remember { mutableStateOf(false) }
    var spellScore by remember { mutableIntStateOf(0) }
    var spellTotal by remember { mutableIntStateOf(0) }
    var completed by remember { mutableIntStateOf(0) }
    var spellTaken by remember { mutableStateOf(false) }
    var lastCommitted by remember { mutableStateOf<StudyItem?>(null) }

    fun commit(item: StudyItem, rating: Rating, requeue: Boolean) {
        lastCommitted = item
        val key = item.entry.word.lowercase()
        val firstToday = seenToday.add(key)
        if (firstToday) {
            completed += 1
            onRate(item.entry, rating, item.isNew)
            if (rating == Rating.Good || rating == Rating.Easy) {
                spellWords.add(item.entry)
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
    }

    val current = queue.firstOrNull()
    val progress = if (total == 0) 0f else completed.toFloat() / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        wl.primary.copy(alpha = 0.07f),
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
            if (current != null) {
                IconButton(
                    onClick = {
                        val item = lastCommitted
                        if (item != null) {
                            val rest = queue.filterNot {
                                it.entry.word.equals(item.entry.word, ignoreCase = true)
                            }
                            queue = listOf(item) + rest
                            lastCommitted = null
                            completed = (completed - 1).coerceAtLeast(0)
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
                            onMarkKnown(current.entry)
                            queue = queue.drop(1)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            if (total > 0) {
                Text(
                    text = "${total - queue.size}/$total",
                    fontFamily = SerifFamily,
                    fontSize = 12.5.sp,
                    color = wl.inkMuted,
                    modifier = Modifier.padding(end = 14.dp),
                )
            }
        }

        when {
            !started -> Unit

            total == 0 -> EmptyState(onBack = onBack)

            current == null && spelling -> SpellingRound(
                words = spellWords,
                onPlay = { url -> player.play(url) },
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
                progress = progress,
                onCommit = { rating, requeue -> commit(current, rating, requeue) },
                onReveal = { feedback.reveal(view) },
                onCorrect = { feedback.correct(view) },
                onWrong = { feedback.wrong(view) },
                onPlay = { url -> player.play(url) },
                onAutoPlay = { url -> player.play(url) },
            )
        }
    }
}

@Composable
private fun StudyCard(
    item: StudyItem,
    progress: Float,
    onCommit: (Rating, Boolean) -> Unit,
    onReveal: () -> Unit,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
    onPlay: (String) -> Unit,
    onAutoPlay: (String) -> Unit,
) {
    val wl = WordloomTheme.colors
    val density = LocalDensity.current.density
    val entry = item.entry
    var layer by remember(entry.word) { mutableStateOf(Layer.Word) }
    var pending by remember(entry.word) { mutableStateOf<Rating?>(null) }
    var details by remember(entry.word) { mutableStateOf<StudyDetails?>(null) }
    var detailsFailed by remember(entry.word) { mutableStateOf(false) }

    LaunchedEffect(entry.word) {
        details = StudyContent.details(entry.word)
        detailsFailed = details == null
        entry.audio.firstOrNull()?.let { onAutoPlay(it.url) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        val cardWidth = minOf(maxWidth * 0.92f, 360.dp)
        val cardHeight = minOf(maxHeight * 0.88f, 500.dp)

        // On the example layer the pager owns horizontal drags and taps, so the
        // card's own gesture detector steps aside.
        val cardGestures = if (layer == Layer.Examples) {
            Modifier
        } else {
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (pending != null && layer != Layer.Word) {
                        layer = Layer.Word
                        pending = null
                    }
                }
                .pointerInput(entry.word, pending) {
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            total += amount
                        },
                        onDragEnd = {
                            if (pending != null) {
                                val t = 44.dp.toPx()
                                layer = when {
                                    abs(total.x) > abs(total.y) && total.x > t -> Layer.Examples
                                    abs(total.x) > abs(total.y) && total.x < -t -> Layer.English
                                    total.y < -t -> Layer.Chinese
                                    total.y > t -> Layer.English
                                    else -> layer
                                }
                            }
                            total = Offset.Zero
                        },
                    )
                }
        }

        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight),
        ) {
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
                        val horizontal = targetState == Layer.Examples || initialState == Layer.Examples
                        if (horizontal) {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut())
                        } else {
                            val down = targetState == Layer.Chinese
                            (slideInVertically { if (down) it / 4 else -it / 4 } + fadeIn()) togetherWith
                                (slideOutVertically { if (down) -it / 4 else it / 4 } + fadeOut())
                        }
                    },
                    label = "studyLayer",
                ) { currentLayer ->
                    when (currentLayer) {
                        Layer.Word -> WordFace(
                            entry = entry,
                            onGrade = { rating ->
                                pending = rating
                                onReveal()
                                layer = Layer.English
                            },
                        )

                        Layer.English -> EnglishFace(
                            entry = entry,
                            onNext = {
                                val rating = pending ?: Rating.Good
                                onCommit(rating, rating == Rating.Again || rating == Rating.Hard)
                            },
                            onWrong = {
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )

                        Layer.Chinese -> ChineseFace(
                            entry = entry,
                            details = details,
                            failed = detailsFailed,
                            onNext = {
                                val rating = pending ?: Rating.Good
                                onCommit(rating, rating == Rating.Again || rating == Rating.Hard)
                            },
                            onWrong = {
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )

                        Layer.Examples -> ExamplesFace(
                            entry = entry,
                            details = details,
                            onPlay = onPlay,
                            onCollapse = { layer = Layer.English },
                            onNext = {
                                val rating = pending ?: Rating.Good
                                onCommit(rating, rating == Rating.Again || rating == Rating.Hard)
                            },
                            onWrong = {
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )
                    }
                }
            }

            // The ring starts at top centre and grows clockwise; a small bead
            // marks the head so the progress reads as a pen stroke, not a border.
            val animated by androidx.compose.animation.core.animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                label = "studyRing",
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (animated > 0.001f) {
                    // No track: at rest the card has one edge, and only the part
                    // already learned is lit. Two outlines was one too many.
                    val path = ringPath(size.width, size.height, 24.dp.toPx(), 1.5.dp.toPx())
                    val measure = PathMeasure()
                    measure.setPath(path, false)
                    val segment = Path()
                    measure.getSegment(0f, measure.length * animated, segment, true)
                    drawPath(
                        path = segment,
                        color = wl.primary.copy(alpha = 0.14f),
                        style = Stroke(
                            width = 8.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                    drawPath(
                        path = segment,
                        color = wl.primary,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                    val head = measure.getPosition(measure.length * animated)
                    drawCircle(
                        color = wl.primary.copy(alpha = 0.18f),
                        radius = 6.dp.toPx(),
                        center = head,
                    )
                    drawCircle(
                        color = wl.primary,
                        radius = 3.dp.toPx(),
                        center = head,
                    )
                }
            }
        }
    }
}


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
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GradeOption("瞬间", "秒懂", Color(0xFF0FA646), Modifier.weight(1f)) { onGrade(Rating.Easy) }
            GradeOption("很快", "认识", wl.primary, Modifier.weight(1f)) { onGrade(Rating.Good) }
            GradeOption("想一会", "模糊", Color(0xFFC98A12), Modifier.weight(1f)) { onGrade(Rating.Hard) }
            GradeOption("想不起", "忘记", wl.coral, Modifier.weight(1f)) { onGrade(Rating.Again) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "先凭第一反应选，再揭晓",
            fontSize = 10.sp,
            color = wl.inkMuted.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GradeOption(
    hint: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hint,
            fontSize = 9.5.sp,
            color = WordloomTheme.colors.inkMuted.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontFamily = SerifFamily,
            fontSize = 14.sp,
            color = tint,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(tint, CircleShape),
        )
    }
}

@Composable
private fun EnglishFace(
    entry: WordEntry,
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
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
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.senses.take(2).forEach { sense ->
                Row {
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
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                        color = wl.ink,
                    )
                }
            }
        }
        entry.senses.firstOrNull()?.examples?.firstOrNull()?.let { example ->
            Spacer(Modifier.height(14.dp))
            Text(
                text = example,
                fontFamily = SerifFamily,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = wl.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
        Spacer(Modifier.weight(1f))
        RevealActions(onNext = onNext, onWrong = onWrong)
    }
}

@Composable
private fun ChineseFace(
    entry: WordEntry,
    details: StudyDetails?,
    failed: Boolean,
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
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

        Spacer(Modifier.weight(1f))
        RevealActions(onNext = onNext, onWrong = onWrong)
    }
}

@Composable
private fun ExamplesFace(
    entry: WordEntry,
    details: StudyDetails?,
    onPlay: (String) -> Unit,
    onCollapse: () -> Unit,
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val examples: List<StudyExample> = details?.examples.orEmpty().ifEmpty {
        entry.senses.flatMap { sense -> sense.examples.map { StudyExample(it, "") } }.take(5)
    }
    val pager = rememberPagerState(pageCount = { examples.size.coerceAtLeast(1) })

    LaunchedEffect(pager.currentPage, examples.size) {
        examples.getOrNull(pager.currentPage)?.audioUrl
            ?.takeIf { it.isNotBlank() }
            ?.let(onPlay)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 22.dp),
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
            examples.getOrNull(pager.currentPage)?.audioUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { url -> SpeakerButton(url = url, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.weight(1f))
            Text(
                text = "收起",
                fontFamily = SerifFamily,
                fontSize = 12.5.sp,
                color = wl.inkMuted,
                modifier = Modifier
                    .clickable(onClick = onCollapse)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (examples.isEmpty()) {
            Text("还没有例句", fontSize = 12.sp, color = wl.inkMuted)
        } else {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val example = examples[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                ) {
                    HighlightedSentence(
                        sentence = example.en,
                        word = entry.word,
                        fontSize = 20.sp,
                        lineHeight = 29.sp,
                    )
                    if (example.zh.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = example.zh,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = wl.inkMuted,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        examples.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.5.dp)
                                    .size(if (i == page) 6.dp else 4.dp)
                                    .background(
                                        if (i == page) wl.ink else wl.outline,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        RevealActions(onNext = onNext, onWrong = onWrong)
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
private fun RevealActions(onNext: () -> Unit, onWrong: () -> Unit) {
    val wl = WordloomTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onNext),
        ) {
            Text(
                text = "下一词",
                fontFamily = SerifFamily,
                fontSize = 15.sp,
                color = wl.primary,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(22.dp).height(2.dp).background(wl.primary, CircleShape))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onWrong),
        ) {
            Text(
                text = "记错了",
                fontFamily = SerifFamily,
                fontSize = 15.sp,
                color = wl.coral,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(22.dp).height(2.dp).background(wl.coral, CircleShape))
        }
    }
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
                modifier = Modifier.clickable(onClick = onBack),
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
                    color = wl.primary,
                    modifier = Modifier
                        .width(180.dp)
                        .clickable(onClick = onStartSpelling),
                ) {
                    Text(
                        text = "拼写检查",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = wl.onPrimary,
                        modifier = Modifier.padding(vertical = 13.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = "返回",
                style = MaterialTheme.typography.labelLarge,
                color = wl.inkMuted,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
    }
}

@Composable
private fun SpellingRound(
    words: List<WordEntry>,
    onPlay: (String) -> Unit,
    onFinish: (Int) -> Unit,
) {
    if (words.isEmpty()) {
        LaunchedEffect(Unit) { onFinish(0) }
        return
    }
    val wl = WordloomTheme.colors
    var index by remember { mutableIntStateOf(0) }
    var typed by remember(index) { mutableStateOf("") }
    var checked by remember(index) { mutableStateOf<Boolean?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var details by remember(index) { mutableStateOf<StudyDetails?>(null) }
    val focus = remember { FocusRequester() }
    val entry = words[index]

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
        val ok = typed.equals(entry.word.drop(1), ignoreCase = true)
        checked = ok
        if (ok) {
            correctCount += 1
            onPlay(entry.audio.firstOrNull()?.url.orEmpty())
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
                            text = "首字母 ${entry.word.first().uppercase()} · ${entry.word.length} 个字母",
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
                                val fixed = !ch.isLetter()
                                val shown = when {
                                    fixed -> ch.toString()
                                    i == 0 -> ch.toString()
                                    i - 1 < typed.length -> typed[i - 1].toString()
                                    else -> ""
                                }
                                val wrong = !fixed && i > 0 && i - 1 < typed.length &&
                                    typed[i - 1].lowercaseChar() != ch.lowercaseChar()
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
                                        .take((entry.word.length - 1).coerceAtLeast(0))
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
                        true -> Text(
                            text = "写对了",
                            fontFamily = SerifFamily,
                            fontSize = 15.sp,
                            color = Color(0xFF0FA646),
                        )
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
                                    .clickable { advance() },
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
                        null -> Surface(
                            shape = RoundedCornerShape(50),
                            color = if (typed.length >= entry.word.length - 1) wl.primary else wl.surfaceAlt,
                            modifier = Modifier
                                .width(150.dp)
                                .clickable(enabled = typed.length >= entry.word.length - 1) { check() },
                        ) {
                            Text(
                                text = "检查",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (typed.length >= entry.word.length - 1) wl.onPrimary else wl.inkMuted,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
