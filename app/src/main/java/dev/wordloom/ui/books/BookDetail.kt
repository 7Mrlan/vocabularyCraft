package dev.wordloom.ui.books

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import kotlin.math.roundToInt
import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.model.WordEntry
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WlMotion
import dev.wordloom.ui.theme.WordloomTheme

private enum class WordFilter(val label: String) {
    All("全部"),
    New("未学习"),
    Learning("学习中"),
    Mastered("已掌握"),
}

/**
 * The inside of a book: one number for the whole volume, then the words in it.
 * Rows are deliberately quiet — a word, one line of meaning, a status dot — so
 * the list reads like a table of contents, not a feed.
 */
@Composable
fun BookDetail(
    book: BookCoverSpec,
    words: List<WordEntry>,
    cards: List<CardEntity>,
    onBack: () -> Unit,
    onStartStudy: () -> Unit,
    onOpenWord: (WordEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wl = WordloomTheme.colors
    val cardMap = remember(cards) { cards.associateBy { it.word.lowercase() } }
    val total = book.count.toIntOrNull() ?: words.size.coerceAtLeast(1)
    val learned = words.count { cardMap[it.word.lowercase()] != null }
    val units = (total / 100).coerceAtLeast(1)
    val currentUnit = (learned / 100 + 1).coerceAtMost(units)
    val accent = book.top
    val ratio = (learned.toFloat() / total).coerceIn(0f, 1f)
    // Whole-book progress reads as a number, not a bar you have to squint at.
    val percentText = when {
        learned == 0 -> "0%"
        ratio >= 0.1f -> "${(ratio * 100).roundToInt()}%"
        else -> String.format(java.util.Locale.US, "%.1f%%", ratio * 100f)
    }

    var filter by remember { mutableStateOf(WordFilter.All) }

    // Units are a property of the book's own order, not of the filter, so a
    // filtered list keeps its original unit numbers and just drops empty ones.
    val sections = remember(words, cards, filter) {
        words.withIndex()
            .filter { (_, entry) ->
                val card = cardMap[entry.word.lowercase()]
                when (filter) {
                    WordFilter.All -> true
                    WordFilter.New -> card == null
                    WordFilter.Learning -> card != null && card.stability < 21.0
                    WordFilter.Mastered -> card != null && card.stability >= 21.0
                }
            }
            .groupBy { it.index / 100 }
            .map { (index, rows) -> index + 1 to rows.map { it.value } }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The shelf is still mounted beneath this page while it animates;
            // swallow pointers here so a tap on empty paper can't spin a cover
            // hidden in the dark.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
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
            Text(
                text = book.title.replace("\n", ""),
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = wl.ink,
            )
            Spacer(Modifier.weight(1f))
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = percentText,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = wl.ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "第 $currentUnit / $units 单元",
                    fontSize = 10.5.sp,
                    color = wl.inkMuted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "已学 $learned · 共 $total 词",
                fontSize = 11.sp,
                color = wl.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(wl.outline, CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        // At 0.3% an honest bar is a sub-pixel sliver; keep a
                        // small cap so the first words still read as a start.
                        .fillMaxWidth(ratio.coerceAtLeast(if (learned > 0) 0.012f else 0f))
                        .height(3.dp)
                        .background(accent, CircleShape),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                WordFilter.entries.forEach { item ->
                    val active = item == filter
                    Column(
                        modifier = Modifier.clickable { filter = item },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) wl.ink else wl.inkMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(if (active) 18.dp else 0.dp)
                                .height(2.dp)
                                .background(if (active) wl.ink else Color.Transparent, CircleShape),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(wl.outline))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 90.dp),
        ) {
            if (sections.isEmpty()) {
                item { FilterEmpty(filter) }
            }
            sections.forEachIndexed { index, (unit, rows) ->
                item(key = "unit-$unit") {
                    val inUnit = rows.count { cardMap[it.word.lowercase()] != null }
                    UnitHeader(
                        unit = unit,
                        detail = if (filter == WordFilter.All) "$inUnit / ${rows.size} 词" else "${rows.size} 词",
                        first = index == 0,
                    )
                }
                items(rows, key = { it.word }) { entry ->
                    val card = cardMap[entry.word.lowercase()]
                    WordRow(
                        entry = entry,
                        status = when {
                            card == null -> 0
                            card.stability >= 21.0 -> 2
                            else -> 1
                        },
                        accent = accent,
                        onClick = { onOpenWord(entry) },
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to wl.surface,
                    1f to wl.surface,
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (pressed) 0.97f else 1f,
            animationSpec = WlMotion.press,
            label = "ctaPress",
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = wl.ink,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onStartStudy,
                ),
        ) {
            Text(
                text = "继续学习 · 第 $currentUnit 单元",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = wl.surface,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }
    }
}

/**
 * A unit marker, not a decoration: the number on the left and the count on the
 * right sit on the same line, with a hairline between them like a table of
 * contents, so a 3000-word list still reads as 30 volumes of 100.
 */
@Composable
private fun UnitHeader(unit: Int, detail: String, first: Boolean) {
    val wl = WordloomTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (first) 16.dp else 26.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第 $unit 单元",
            fontSize = 10.5.sp,
            color = wl.inkMuted,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(wl.outline.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = detail,
            fontSize = 10.5.sp,
            color = wl.inkMuted,
        )
    }
}

@Composable
private fun FilterEmpty(filter: WordFilter) {
    val wl = WordloomTheme.colors
    Text(
        text = when (filter) {
            WordFilter.All -> "这本词书还没有词"
            WordFilter.New -> "这册的词都翻过一遍了"
            WordFilter.Learning -> "还没有在学的词，先去「未学习」挑几个"
            WordFilter.Mastered -> "还没有完全掌握的词，答对几轮就会到这里"
        },
        fontSize = 12.sp,
        color = wl.inkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
    )
}

@Composable
private fun WordRow(
    entry: WordEntry,
    status: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) wl.ink.copy(alpha = 0.045f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.word,
            fontFamily = SerifFamily,
            fontSize = 15.sp,
            color = wl.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.senses.firstOrNull()?.definition.orEmpty(),
            fontSize = 11.sp,
            color = wl.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.size(9.dp), contentAlignment = Alignment.Center) {
            when (status) {
                2 -> Box(Modifier.fillMaxSize().background(accent, CircleShape))
                1 -> Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.45f), CircleShape))
                else -> Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = wl.outline,
                        radius = size.minDimension / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }
    }
}
