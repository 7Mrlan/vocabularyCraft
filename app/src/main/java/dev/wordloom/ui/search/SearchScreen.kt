package dev.wordloom.ui.search

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.model.Suggestion
import dev.wordloom.ui.SuggestPhase
import dev.wordloom.ui.SuggestUiState
import dev.wordloom.ui.components.PoolHeader
import dev.wordloom.ui.theme.SansFamily
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Search sits inside the same navy "pool" as the home hero and hands the eye a
 * sheet of paper below it: recent lookups first, then matches grouped into words
 * and phrases, so the page has something to read before anything is typed.
 */
@Composable
fun SearchScreen(
    suggest: SuggestUiState,
    recent: List<String>,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRetrySuggest: () -> Unit,
    onBack: () -> Unit,
    onRandom: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    // Put the keyboard away before the page changes, otherwise the viewport
    // shrinks mid-transition and reads as a jump.
    val submit: (String) -> Unit = { term ->
        keyboard?.hide()
        onSubmit(term)
    }
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // One source of truth for what the page is doing. "No match" is a finished
    // answer for this exact text, never a placeholder while a request is out.
    val waiting = suggest.phase == SuggestPhase.Typing || suggest.phase == SuggestPhase.Loading
    val empty = suggest.phase == SuggestPhase.Empty
    val failed = suggest.phase == SuggestPhase.Error

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // The field survives process death via rememberSaveable, but the
        // ViewModel's suggestion state does not: re-ask for the restored text
        // instead of leaving an empty results area.
        if (query.isNotBlank() && suggest.phase == SuggestPhase.Idle) onQueryChange(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .navigationBarsPadding(),
    ) {
        PoolHeader(height = 196.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.wl_back_knockout),
                        contentDescription = "返回",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(focusRequester),
                    placeholder = {
                        // The pill is white in both themes, so its ink is fixed too.
                        Text(
                            "搜索单词、释义或用法",
                            color = Color(0xFF6B7C93),
                            fontFamily = SansFamily,
                            fontSize = 15.sp,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = SansFamily,
                        fontSize = 16.sp,
                        color = Color(0xFF162941),
                    ),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.wl_search_light),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = wl.primary,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) submit(query.trim())
                    }),
                )
            }
            Spacer(Modifier.height(12.dp))
            // The water is the status: it keeps moving while a lookup is out,
            // and settles flat when the answer is "nothing here".
            Waterline(
                active = waiting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp)
                    .height(10.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        // Two rows with the same word would collide in the LazyColumn key.
        val words = suggest.items.filter { !it.word.contains(' ') }.distinctBy { it.word }
        val phrases = suggest.items.filter { it.word.contains(' ') }.distinctBy { it.word }

        if (query.isBlank()) {
            EmptyQuery(recent = recent, onPick = submit, onRandom = onRandom)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 2.dp, bottom = 28.dp,
                ),
            ) {
                if (words.isNotEmpty()) {
                    item { SectionLabel("匹配的词") }
                    itemsIndexed(words, key = { _, item -> item.word }) { index, item ->
                        Surfaced(index, Modifier.animateItem()) {
                            ResultRow(item, Modifier.fillMaxWidth()) { submit(item.word) }
                        }
                    }
                }
                if (phrases.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionLabel("短语")
                    }
                    itemsIndexed(phrases, key = { _, item -> item.word }) { index, item ->
                        Surfaced(words.size + index, Modifier.animateItem()) {
                            ResultRow(item, Modifier.fillMaxWidth()) { submit(item.word) }
                        }
                    }
                }
                if (suggest.items.isEmpty()) {
                    when {
                        waiting -> item {
                            Text(
                                text = "正在查…",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = wl.inkMuted,
                                modifier = Modifier.padding(top = 22.dp),
                            )
                        }
                        failed -> item {
                            Text(
                                text = "网络不给力，点这里重试",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = wl.coral,
                                modifier = Modifier
                                    .padding(top = 22.dp)
                                    .clickable(onClick = onRetrySuggest),
                            )
                        }
                        empty -> item { NoResults(suggest.query) { submit(suggest.query) } }
                    }
                }
            }
        }
    }
}

/**
 * A thread of water under the search pill. It moves only while a lookup is out;
 * when the answer is empty it flattens into a still line, so the page says
 * "nothing" with motion before the copy does.
 */
@Composable
private fun Waterline(active: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val moving = active && !reduceMotion

    // Keep the wave where it was when motion stopped. Dropping the phase back to
    // zero makes the line jump sideways while the amplitude is still fading out.
    val lastPhase = remember { PhaseMemory() }
    val phase = if (moving) {
        val transition = rememberInfiniteTransition(label = "waterline")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI.toFloat()),
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
            label = "waterlinePhase",
        )
        SideEffect { lastPhase.value = value }
        value
    } else {
        lastPhase.value
    }
    val amplitude by animateFloatAsState(
        targetValue = if (moving) 1f else 0f,
        animationSpec = tween(420),
        label = "waterlineAmplitude",
    )
    val ink = Color.White.copy(alpha = 0.55f)
    Canvas(modifier = modifier) {
        val mid = size.height / 2f
        val amp = 2.dp.toPx() * amplitude
        if (amp < 0.05f) {
            drawLine(
                color = ink,
                start = Offset(0f, mid),
                end = Offset(size.width, mid),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val y = mid + sin(t * 2f * PI.toFloat() + phase) * amp
            if (i == 0) path.moveTo(0f, y) else path.lineTo(size.width * t, y)
        }
        drawPath(path, ink, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Plain holder: the wave position must outlive the transition without being snapshot state. */
private class PhaseMemory(var value: Float = 0f)

/** Results rise out of that waterline, a beat apart, instead of popping in. */
@Composable
private fun Surfaced(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    // rememberSaveable rides the LazyColumn item's saveable state, so a row that
    // scrolls out and back in does not replay its entrance.
    var visible by rememberSaveable { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!visible) {
            delay(30L * index.coerceAtMost(8))
            visible = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "surface",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 14.dp.toPx()
        },
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    val wl = WordloomTheme.colors
    Text(
        text = text,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = SansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
        ),
        color = wl.inkMuted,
    )
}

@Composable
private fun ResultRow(
    item: Suggestion,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val wordInk = if (wl.isDark) Color(0xFF7FCBEF) else Color(0xFF00578A)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.word,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = wordInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.definition.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.definition,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = wl.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            painter = painterResource(R.drawable.wl_chevron),
            contentDescription = null,
            tint = wl.inkMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
    HorizontalDivider(color = wl.outline.copy(alpha = 0.6f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyQuery(
    recent: List<String>,
    onPick: (String) -> Unit,
    onRandom: () -> Unit,
) {
    val wl = WordloomTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, top = 2.dp, bottom = 28.dp,
        ),
    ) {
        if (recent.isNotEmpty()) {
            item { SectionLabel("最近查过") }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recent.forEach { word ->
                        Surface(
                            shape = CircleShape,
                            color = wl.surfaceAlt,
                            modifier = Modifier.clickable { onPick(word) },
                        ) {
                            Text(
                                text = word,
                                fontFamily = SerifFamily,
                                fontSize = 14.sp,
                                color = wl.ink,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(26.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = wl.surfaceAlt,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRandom),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(wl.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.wl_spark),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "想不起拼写？",
                            fontFamily = SerifFamily,
                            fontSize = 16.sp,
                            color = wl.ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "随机抽一个词看看",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = wl.inkMuted,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.wl_chevron),
                        contentDescription = null,
                        tint = wl.inkMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "输入英文单词开始查询。查过的词会缓存在本机，可离线再看。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = wl.inkMuted,
            )
        }
    }
}

@Composable
private fun NoResults(query: String, onSearchAnyway: () -> Unit) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "没有匹配 “$query”",
            fontFamily = SerifFamily,
            fontSize = 18.sp,
            color = wl.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "换个拼写，或直接用原词查询。",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = wl.inkMuted,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = CircleShape,
            color = wl.primary,
            modifier = Modifier.clickable(onClick = onSearchAnyway),
        ) {
            Text(
                text = "直接查询 “$query”",
                style = MaterialTheme.typography.labelLarge,
                color = wl.onPrimary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
