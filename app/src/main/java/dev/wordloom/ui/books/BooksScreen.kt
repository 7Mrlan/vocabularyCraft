package dev.wordloom.ui.books

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * One card at a time, on an endless wheel.
 *
 * The reader only ever sees the face in front of them; the next card turns in
 * from the edge while the current one turns away, and both fade before they
 * would sit side by side. A flick keeps its velocity into a damped spring, so a
 * hard swipe crosses several books and rocks to a stop; the fourth book wraps
 * back to the first without a jump.
 *
 * Each crossing is a tooth of a ratchet: a gear click (pitch drifting with
 * direction) and a light tick; settling adds one softer click, and choosing the
 * book is the only heavier haptic.
 */
@Composable
fun BooksScreen(onBack: () -> Unit, onStartStudy: () -> Unit) {
    val wl = WordloomTheme.colors
    val books = SampleBooks
    val count = books.size
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val view = LocalView.current
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

    var lastNearest by remember { mutableIntStateOf(0) }
    LaunchedEffect(nearest) {
        if (nearest != lastNearest) {
            val rate = if (nearest > lastNearest) 1.05f else 0.95f
            lastNearest = nearest
            if (click != 0) soundPool.play(click, 0.26f, 0.26f, 1, 0, rate)
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
                    .height(286.dp),
            ) {
                val cardHeight = 250.dp
                val cardWidth = cardHeight * 0.76f
                val pxPerPage = maxWidth.value * density / 2f + cardWidth.value * density / 2f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                if (settling) {
                                    scope.launch { settle.stop() }
                                    position = settle.value
                                    settling = false
                                }
                                position -= delta / pxPerPage
                            },
                            onDragStopped = { velocity ->
                                // Velocity carries into the spring; a hard flick
                                // crosses a couple of books and rocks to rest.
                                val pagesPerSecond = velocity / pxPerPage
                                val target = (position - pagesPerSecond * 0.20f).roundToInt()
                                settling = true
                                scope.launch {
                                    settle.snapTo(position)
                                    settle.animateTo(
                                        targetValue = target.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.90f,
                                            stiffness = 190f,
                                        ),
                                        initialVelocity = -pagesPerSecond,
                                    )
                                    position = ((target % count) + count) % count.toFloat()
                                    settling = false
                                }
                            },
                        ),
                ) {
                    books.forEachIndexed { index, spec ->
                        var d = index - shown
                        d -= count * (d / count).roundToInt()
                        if (d.absoluteValue < 1.42f) {
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
                                        // The ring passes just outside both screen edges:
                                        // at rest the neighbours are off-canvas, during a
                                        // drag they swing in, turn inward and lose a little
                                        // size. Same height, fully opaque — the circle is
                                        // read from the motion, not from a static row.
                                        translationX = d * pxPerPage
                                        rotationY = (d * -34f).coerceIn(-40f, 40f)
                                        cameraDistance = 15f * density
                                        val shrink = (1f - d.absoluteValue * 0.14f).coerceAtLeast(0.70f)
                                        scaleX = shrink
                                        scaleY = shrink
                                    }
                                    .clickable {
                                        if (d.absoluteValue > 0.2f) {
                                            val target = (shown + d).roundToInt()
                                            settling = true
                                            scope.launch {
                                                settle.snapTo(position)
                                                settle.animateTo(
                                                    targetValue = target.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = 0.90f,
                                                        stiffness = 240f,
                                                    ),
                                                )
                                                position = ((target % count) + count) % count.toFloat()
                                                settling = false
                                            }
                                        } else if (spec.id != currentId) {
                                            currentId = spec.id
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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

                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = wl.primary,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .clickable(onClick = onStartStudy),
                ) {
                    Text(
                        text = "开始学习",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = wl.onPrimary,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}
