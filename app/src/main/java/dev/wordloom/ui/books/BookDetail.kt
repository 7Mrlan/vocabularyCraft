package dev.wordloom.ui.books

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.model.WordEntry
import dev.wordloom.ui.theme.SerifFamily
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
    modifier: Modifier = Modifier,
) {
    val wl = WordloomTheme.colors
    val cardMap = remember(cards) { cards.associateBy { it.word.lowercase() } }
    val total = book.count.toIntOrNull() ?: words.size.coerceAtLeast(1)
    val learned = words.count { cardMap[it.word.lowercase()] != null }
    val units = (total / 100).coerceAtLeast(1)
    val currentUnit = (learned / 100 + 1).coerceAtMost(units)
    val accent = book.top

    var filter by remember { mutableStateOf(WordFilter.All) }

    val visible = remember(words, cards, filter) {
        words.filter { entry ->
            val card = cardMap[entry.word.lowercase()]
            when (filter) {
                WordFilter.All -> true
                WordFilter.New -> card == null
                WordFilter.Learning -> card != null && card.stability < 21.0
                WordFilter.Mastered -> card != null && card.stability >= 21.0
            }
        }
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
            MiniCover(book)
            Spacer(Modifier.width(8.dp))
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
                    text = learned.toString(),
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = wl.ink,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "/ $total 词",
                    fontSize = 11.sp,
                    color = wl.inkMuted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "第 $currentUnit / $units 单元",
                    fontSize = 10.5.sp,
                    color = wl.inkMuted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(wl.outline, CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((learned.toFloat() / total).coerceIn(0f, 1f))
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
            item {
                Text(
                    text = "第 $currentUnit 单元 · ${visible.size} 词",
                    fontSize = 10.5.sp,
                    color = wl.inkMuted,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
            }
            items(visible, key = { it.word }) { entry ->
                val card = cardMap[entry.word.lowercase()]
                WordRow(
                    entry = entry,
                    status = when {
                        card == null -> 0
                        card.stability >= 21.0 -> 2
                        else -> 1
                    },
                    accent = accent,
                )
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
        Surface(
            shape = RoundedCornerShape(50),
            color = wl.ink,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onStartStudy),
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

@Composable
private fun MiniCover(book: BookCoverSpec) {
    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 32.dp)
            .background(
                Brush.verticalGradient(listOf(book.top, book.bottom)),
                RoundedCornerShape(4.dp),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.82f)
                lineTo(size.width * 0.52f, size.height * 0.42f)
                lineTo(size.width * 0.72f, size.height * 0.62f)
                lineTo(size.width * 0.9f, size.height * 0.82f)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.9f))
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = size.width * 0.16f,
                center = Offset(size.width * 0.72f, size.height * 0.3f),
            )
        }
    }
}

@Composable
private fun WordRow(entry: WordEntry, status: Int, accent: Color) {
    val wl = WordloomTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
