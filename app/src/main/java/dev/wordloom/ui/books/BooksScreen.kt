package dev.wordloom.ui.books

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import kotlin.math.absoluteValue

/**
 * The shelf: the middle cover faces the reader, the neighbours turn away a
 * little and recede, so the four books read as one deck rather than a strip.
 * Each cover carries its own shadow — nothing is painted under the deck, so the
 * shadows move with the cards.
 *
 * A snap is a ratchet: crossing a card fires a gear click and a tick; picking
 * one is the only heavier haptic.
 */
@Composable
fun BooksScreen(onBack: () -> Unit) {
    val wl = WordloomTheme.colors
    val books = SampleBooks
    val pager = rememberPagerState(pageCount = { books.size })
    var currentId by remember { mutableStateOf(books.first().id) }

    val context = LocalContext.current
    val view = LocalView.current
    val soundPool = remember { SoundPool.Builder().setMaxStreams(2).build() }
    var click by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        click = soundPool.load(context, R.raw.gear_click, 1)
        onDispose { soundPool.release() }
    }

    var lastPage by remember { mutableIntStateOf(pager.currentPage) }
    LaunchedEffect(pager.currentPage) {
        val page = pager.currentPage
        if (page != lastPage) {
            val rate = if (page > lastPage) 1.045f else 0.955f
            lastPage = page
            if (click != 0) soundPool.play(click, 0.24f, 0.24f, 1, 0, rate)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    var lastSettled by remember { mutableIntStateOf(pager.settledPage) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != lastSettled) {
            lastSettled = pager.settledPage
            if (click != 0) soundPool.play(click, 0.15f, 0.15f, 1, 0, 0.92f)
        }
    }

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
            Spacer(Modifier.width(2.dp))
            Text(
                text = "词书",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = wl.ink,
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 56.dp),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    pagerSnapDistance = PagerSnapDistance.atMost(3),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            ) { page ->
                val spec = books[page]
                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    BookCover(
                        spec = spec,
                        inReading = spec.id == currentId,
                        artParallax = offset,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.78f)
                            .graphicsLayer {
                                rotationY = (offset * -12f).coerceIn(-24f, 24f)
                                cameraDistance = 16f * density
                                translationY = 6f * density * offset.absoluteValue
                                val shrink = 1f - (offset.absoluteValue * 0.06f).coerceAtMost(0.12f)
                                scaleX = shrink
                                scaleY = shrink
                                alpha = 1f - (offset.absoluteValue * 0.20f).coerceAtMost(0.40f)
                            }
                            .clickable {
                                if (page != pager.currentPage) {
                                    if (click != 0) soundPool.play(click, 0.20f, 0.20f, 1, 0, 1f)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else if (spec.id != currentId) {
                                    currentId = spec.id
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    if (click != 0) soundPool.play(click, 0.34f, 0.34f, 1, 0, 0.9f)
                                }
                            },
                    )
                }
            }

            // Four thin rules; the active one grows instead of lighting up.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                books.forEachIndexed { index, _ ->
                    val active = index == pager.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(2.dp)
                            .width(if (active) 18.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (active) wl.ink else wl.outline),
                    )
                }
            }

            val selected = books[pager.currentPage]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = selected.title.replace("\n", ""),
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = wl.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${selected.count} 个词族 · ${selected.countNote.substringAfter("·").trim()}",
                    fontSize = 11.sp,
                    color = wl.inkMuted,
                )
            }
        }
    }
}
