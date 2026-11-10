package dev.wordloom.ui.books

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.wordloom.R
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Four covers on a ring, not a strip. The centre card faces the reader; the
 * neighbours swing inward and slip down the curve, overlapping so the deck
 * reads as a circle seen slightly from above. Small on purpose.
 *
 * The drag is a ratchet: every time a card becomes the nearest one, a gear
 * click and a tick fire; releasing settles to the nearest card with a spring.
 */
@Composable
fun BooksScreen(onBack: () -> Unit) {
    val wl = WordloomTheme.colors
    val books = SampleBooks
    val count = books.size

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    val soundPool = remember { SoundPool.Builder().setMaxStreams(2).build() }
    var click by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        click = soundPool.load(context, R.raw.gear_click, 1)
        onDispose { soundPool.release() }
    }

    var currentId by remember { mutableStateOf(books.first().id) }
    var position by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    val shown = if (settling) settle.value else position
    val nearest = ((shown.roundToInt() % count) + count) % count

    // One tooth of the gear per card crossed.
    var lastNearest by remember { mutableIntStateOf(0) }
    LaunchedEffect(nearest) {
        if (nearest != lastNearest) {
            val rate = if (nearest > lastNearest) 1.045f else 0.955f
            lastNearest = nearest
            if (click != 0) soundPool.play(click, 0.24f, 0.24f, 1, 0, rate)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(318.dp),
            ) {
                val cardWidth = maxWidth * 0.60f
                val cardHeight = cardWidth / 0.78f
                val pxPerPage = cardWidth.value * density * 0.50f

                // A wash of the current cover's colour, so the air belongs to it.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tint = books[nearest].top
                    // A wide wash of the current cover's colour: the air around
                    // the deck belongs to the book, instead of reading as blank.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(tint.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.46f),
                            radius = size.width * 0.62f,
                        ),
                        radius = size.width * 0.62f,
                        center = Offset(size.width / 2f, size.height * 0.46f),
                    )
                }

                // Contact shadow under the ring.
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.64f)
                        .height(46.dp),
                ) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(wl.ink.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.52f,
                        ),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                if (settling) {
                                    // Grabbing mid-flight takes over from where it is.
                                    scope.launch { settle.stop() }
                                    position = settle.value
                                    settling = false
                                }
                                position -= delta / pxPerPage
                            },
                            onDragStopped = { velocity ->
                                val projected = position - velocity / pxPerPage * 0.10f
                                val target = projected.roundToInt()
                                settling = true
                                scope.launch {
                                    settle.snapTo(position)
                                    settle.animateTo(
                                        target.toFloat(),
                                        spring(dampingRatio = 0.86f, stiffness = 320f),
                                    )
                                    // The ring is endless; re-centre without a jump.
                                    position = ((target % count) + count) % count.toFloat()
                                    settling = false
                                }
                            },
                        ),
                ) {
                    books.forEachIndexed { index, spec ->
                        // Shortest way round the ring, so the left side is never empty.
                        var d = index - shown
                        d -= count * (d / count).roundToInt()
                        if (d.absoluteValue <= 1.95f) {
                            val turn = (d * -36f).coerceIn(-80f, 80f)
                            BookCover(
                                spec = spec,
                                inReading = spec.id == currentId,
                                artParallax = d,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(cardWidth)
                                    .height(cardHeight)
                                    .zIndex(10f - d.absoluteValue)
                                    .graphicsLayer {
                                        translationX = d * pxPerPage
                                        translationY = 20f * density * d * d
                                        rotationY = turn
                                        cameraDistance = 13f * density
                                        val shrink = 1f - (d.absoluteValue * 0.12f).coerceAtMost(0.24f)
                                        scaleX = shrink
                                        scaleY = shrink
                                        alpha = 1f - (d.absoluteValue * 0.34f).coerceAtMost(0.68f)
                                    }
                                    .clickable {
                                        if (d.absoluteValue < 0.4f) {
                                            if (spec.id != currentId) {
                                                currentId = spec.id
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                if (click != 0) {
                                                    soundPool.play(click, 0.34f, 0.34f, 1, 0, 0.9f)
                                                }
                                            }
                                        } else {
                                            val target = (shown + d).roundToInt()
                                            settling = true
                                            scope.launch {
                                                settle.snapTo(position)
                                                settle.animateTo(
                                                    target.toFloat(),
                                                    spring(dampingRatio = 0.86f, stiffness = 320f),
                                                )
                                                position = ((target % count) + count) % count.toFloat()
                                                settling = false
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                books.forEachIndexed { index, _ ->
                    val active = index == nearest
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

            val selected = books[nearest]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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

                Spacer(Modifier.height(26.dp))
                val units = selected.units
                if (units != null) {
                    // The book's shape at a glance: one tick per unit, two rows.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        (0 until units).chunked(10).forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEachIndexed { columnIndex, _ ->
                                    val next = rowIndex == 0 && columnIndex == 0
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (next) wl.primary.copy(alpha = 0.45f) else wl.outline,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = "$units 个单元 · 0 已完成",
                        fontSize = 10.5.sp,
                        color = wl.inkMuted.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
