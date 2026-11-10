package dev.wordloom.ui.study

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

    fun commit(item: StudyItem, rating: Rating, requeue: Boolean) {
        val key = item.entry.word.lowercase()
        if (seenToday.add(key)) onRate(item.entry, rating, item.isNew)
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
    val progress = if (total == 0) 0f else (total - queue.size).toFloat() / total

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
            .navigationBarsPadding(),
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

            current == null -> DoneState(total = total, onBack = onBack)

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

    LaunchedEffect(entry.word) {
        details = StudyContent.details(entry.word)
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
                    },
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
                            onPlay = onPlay,
                            onNext = {
                                onCommit(pending ?: Rating.Good, false)
                            },
                            onWrong = {
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )

                        Layer.Chinese -> ChineseFace(
                            entry = entry,
                            details = details,
                            onNext = { onCommit(pending ?: Rating.Good, false) },
                            onWrong = {
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )

                        Layer.Examples -> ExamplesFace(
                            entry = entry,
                            details = details,
                            onNext = { onCommit(pending ?: Rating.Good, false) },
                            onWrong = {
                                onWrong()
                                onCommit(Rating.Again, true)
                            },
                        )
                    }
                }
            }

            // The ring sits on the card's edge, outside its clip.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = 1.dp.toPx()
                val radius = 24.dp.toPx()
                val rect = Rect(inset, inset, size.width - inset, size.height - inset)
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect,
                            CornerRadius(radius, radius),
                        ),
                    )
                }
                drawPath(path, color = wl.outline, style = Stroke(width = 1.6.dp.toPx()))
                if (progress > 0f) {
                    val measure = PathMeasure()
                    measure.setPath(path, false)
                    val segment = Path()
                    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), segment, true)
                    drawPath(
                        path = segment,
                        color = wl.primary,
                        style = Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
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
    onPlay: (String) -> Unit,
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
                text = "正在取中文…",
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
    onNext: () -> Unit,
    onWrong: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val examples: List<StudyExample> = details?.examples.orEmpty().ifEmpty {
        entry.senses.flatMap { sense -> sense.examples.map { StudyExample(it, "") } }.take(5)
    }
    val pager = rememberPagerState(pageCount = { examples.size.coerceAtLeast(1) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Text(
            text = entry.word,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = wl.ink,
        )
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
private fun DoneState(total: Int, onBack: () -> Unit) {
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
