package dev.wordloom.ui.books

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.wordloom.ui.theme.WlMotion
import dev.wordloom.ui.theme.WordloomTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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
fun BooksScreen(
    onBack: () -> Unit,
    onStartStudy: () -> Unit,
    onOpenWord: (String) -> Unit = {},
    onSelectBook: (String) -> Unit = {},
    selectedBookId: String = dev.wordloom.data.wordbook.WordBook.Core.name,
    words: List<String> = emptyList(),
    entries: Map<String, dev.wordloom.data.model.WordEntry> = emptyMap(),
    cards: List<dev.wordloom.data.local.CardEntity> = emptyList(),
) {
    val wl = WordloomTheme.colors
    val books = Bookshelf
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

    val currentId = selectedBookId
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var position by rememberSaveable {
        mutableFloatStateOf(books.indexOfFirst { it.id == selectedBookId }.coerceAtLeast(0).toFloat())
    }
    val settle = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    val shown = if (settling) settle.value else position
    val nearest = ((shown.roundToInt() % count) + count) % count
    var detailReady by remember { mutableStateOf(false) }
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(220)
        detailReady = true
    }
    val openProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (detailOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "bookOpen",
    )
    // A flick, not a turn: the cover whips a full circle in a third of a second
    // and the gust it throws is what covers the screen.
    val spinAngle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (spinning) 360f else 0f,
        animationSpec = tween(
            durationMillis = 320,
            easing = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1f),
        ),
        label = "bookSpin",
    )
    val gust = remember { Animatable(0f) }
    var cardCenter by remember { mutableStateOf(Offset.Zero) }
    val leafPath = remember {
        Path().apply {
            moveTo(0f, -1f)
            quadraticTo(0.62f, 0f, 0f, 1f)
            quadraticTo(-0.62f, 0f, 0f, -1f)
            close()
        }
    }

    // Opening is a two-beat gesture: the cover turns, the gust leaves with it,
    // and the volume grows out of that turn. Closing runs both beats at once so
    // going back takes one motion, not a queue.
    fun openBook() {
        if (detailOpen) return
        // The book is chosen at the tap, not while the deck is still settling.
        openedId = books[nearest].id
        scope.launch {
            gust.snapTo(0f)
            gust.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = LinearEasing),
            )
        }
        scope.launch {
            spinning = true
            kotlinx.coroutines.delay(180)
            detailOpen = true
        }
    }

    fun closeBook() {
        if (!detailOpen) return
        detailOpen = false
        // The page folds back first; the cover only turns once it can be seen,
        // otherwise the spin happens behind an opaque page and the close reads
        // as a cut.
        scope.launch {
            kotlinx.coroutines.delay(280)
            // A quick reopen in between owns the spin now.
            if (!detailOpen) spinning = false
        }
    }

    // System back closes the book before it leaves the shelf.
    BackHandler(enabled = detailOpen) { closeBook() }

    var lastNearest by remember { mutableIntStateOf(0) }
    LaunchedEffect(nearest) {
        if (nearest != lastNearest) {
            val rate = if (nearest > lastNearest) 1.05f else 0.95f
            lastNearest = nearest
            if (click != 0) soundPool.play(click, 0.26f, 0.26f, 1, 0, rate)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .graphicsLayer {
                // No fade: the page is opaque and simply grows over the shelf,
                // so the shelf never has to be composited half-lit underneath.
                val s = 1f - openProgress * 0.06f
                scaleX = s
                scaleY = s
            },
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
                    .height(286.dp)
                    .onGloballyPositioned { coords ->
                        val origin = coords.positionInRoot()
                        cardCenter = Offset(
                            origin.x + coords.size.width / 2f,
                            origin.y + coords.size.height / 2f,
                        )
                    },
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
                                        rotationY = (d * -34f).coerceIn(-40f, 40f) +
                                            (if (d.absoluteValue < 0.01f) spinAngle else 0f)
                                        cameraDistance = 15f * density
                                        // A single swell at the halfway point of the turn, so the
                                        // cover feels like it is throwing the gust.
                                        val swell = if (d.absoluteValue < 0.01f) {
                                            1f + 0.05f * sin((spinAngle / 360f) * Math.PI.toFloat())
                                        } else {
                                            1f
                                        }
                                        val shrink = (1f - d.absoluteValue * 0.14f).coerceAtLeast(0.70f)
                                        scaleX = shrink * swell
                                        scaleY = shrink * swell
                                    }
                                    .clickable {
                                        if (d.absoluteValue > 0.2f) {
                                            val target = (shown + d).roundToInt()
                                            settling = true
                                            scope.launch {
                                                // Take the deck where it is now, not where
                                                // the last drag ended, or a tap mid-settle
                                                // snaps it backwards.
                                                settle.snapTo(settle.value)
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
                                            onSelectBook(spec.id)
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        } else {
                                            openBook()
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
                    text = "${selected.count} 个词 · ${selected.countNote.substringAfter("·").trim()}",
                    fontSize = 11.sp,
                    color = wl.inkMuted,
                )

                Spacer(Modifier.height(24.dp))
                val ctaInteraction = remember { MutableInteractionSource() }
                val ctaPressed by ctaInteraction.collectIsPressedAsState()
                val ctaScale by animateFloatAsState(
                    targetValue = if (ctaPressed) 0.97f else 1f,
                    animationSpec = WlMotion.press,
                    label = "ctaPress",
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = wl.ink,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .graphicsLayer {
                            scaleX = ctaScale
                            scaleY = ctaScale
                        }
                        .clickable(
                            interactionSource = ctaInteraction,
                            indication = null,
                            onClick = { openBook() },
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "开始学习",
                            style = MaterialTheme.typography.labelLarge,
                            color = wl.surface,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "→",
                            fontSize = 15.sp,
                            color = wl.surface.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }

    if (detailReady || detailOpen) {
        BookDetail(
            book = books.firstOrNull { it.id == openedId } ?: books[nearest],
            words = words,
            entries = entries,
            cards = cards,
            interactive = detailOpen,
            onBack = { closeBook() },
            onStartStudy = { onStartStudy() },
            onOpenWord = onOpenWord,
            modifier = Modifier
                // Composed behind the shelf before anyone can tap, so the first
                // frame of the opening animation is already laid out.
                // Stay on top until the fold is finished, then drop behind the
                // shelf so the pre-composed page can never be hit while closed.
                .zIndex(if (openProgress > 0.01f) 1f else -1f)
                .graphicsLayer {
                // Opening is opaque from the first frame. Closing stays opaque
                // while the page folds onto the cover and only dissolves in its
                // last few frames, so the two layers never ghost each other.
                alpha = if (detailOpen) 1f else ((openProgress - 0.12f) / 0.14f).coerceIn(0f, 1f)
                val s = 0.48f + 0.52f * openProgress
                scaleX = s
                scaleY = s
                // Grows out of the cover, not from the middle of the screen.
                transformOrigin = TransformOrigin(0.5f, 0.45f)
            },
        )
    }

    // Leaves thrown off the cover by the turn. Drawn above the page so the
    // gust survives the cover: for half a second they drift over the paper and
    // are gone by the time the eye reaches the word list.
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f),
    ) {
        val g = gust.value
        if (g <= 0f || g >= 1f) return@Canvas
        val c = cardCenter
        if (c == Offset.Zero) return@Canvas
        val base = 66.dp.toPx()
        val travel = 152.dp.toPx()
        GustLeaves.forEach { leaf ->
            val local = ((g - leaf.delay) / (1f - leaf.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val eased = local.pow(0.62f)
            val angle = leaf.angle + leaf.swirl * eased
            val dist = base + travel * leaf.reach * eased
            // A wind that leans right and lifts, not a symmetric confetti burst.
            val x = c.x + cos(angle) * dist + 64.dp.toPx() * eased
            val y = c.y + sin(angle) * dist * 0.7f - 22.dp.toPx() * eased +
                leaf.fall.dp.toPx() * eased * eased
            val alpha = 0.8f * (1f - local).pow(1.5f) * (local / 0.1f).coerceAtMost(1f)
            if (alpha <= 0.01f) return@forEach
            val size = leaf.size.dp.toPx() * (1f - 0.22f * local)
            rotate(
                degrees = Math.toDegrees(angle.toDouble()).toFloat() + eased * 220f,
                pivot = Offset(x, y),
            ) {
                translate(left = x, top = y) {
                    scale(scaleX = size, scaleY = size, pivot = Offset.Zero) {
                        // Coral is the one hue that reads on the green cover, the
                        // dark shelf and the paper page alike.
                        drawPath(leafPath, color = wl.coral.copy(alpha = alpha))
                    }
                }
            }
        }
    }
    }
}

private data class GustLeaf(
    val angle: Float,
    val reach: Float,
    val size: Float,
    val delay: Float,
    val swirl: Float,
    val fall: Float,
)

/** Fixed scatter — the same gust every time, so it reads as the book's own. */
private val GustLeaves: List<GustLeaf> = run {
    val random = java.util.Random(29)
    List(12) {
        GustLeaf(
            angle = random.nextFloat() * (Math.PI * 2).toFloat(),
            reach = 0.4f + random.nextFloat() * 1.2f,
            size = 5.6f + random.nextFloat() * 4.4f,
            delay = random.nextFloat() * 0.22f,
            swirl = 0.45f + random.nextFloat() * 0.8f,
            fall = random.nextFloat() * 18f,
        )
    }
}
