package dev.wordloom.ui.quiz

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.QuizQuestion
import dev.wordloom.ui.components.PoolHeader
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.components.pressable
import dev.wordloom.ui.theme.WordloomTheme

/**
 * Four-choice practice over the local cache. It never touches the FSRS queue —
 * review decides what comes back and when; this screen only checks whether the
 * meaning sticks.
 */
@Composable
fun QuizScreen(
    questions: List<QuizQuestion>,
    onBack: () -> Unit,
    onRestart: () -> Unit,
) {
    val wl = WordloomTheme.colors
    var queue by remember(questions) { mutableStateOf(questions) }
    var index by remember(questions) { mutableIntStateOf(0) }
    var selected by remember(questions, index) { mutableStateOf<Int?>(null) }
    var correctCount by remember(questions) { mutableIntStateOf(0) }
    var wrong by remember(questions) { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var finished by remember(questions) { mutableStateOf(false) }

    val current = queue.getOrNull(index)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .navigationBarsPadding(),
    ) {
        PoolHeader(height = 156.dp) {
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
                Spacer(Modifier.weight(1f))
                if (!finished && current != null) {
                    Text(
                        text = "第 ${index + 1} / ${queue.size} 题",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(end = 20.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "练习",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                if (!finished && queue.isNotEmpty()) {
                    Text(
                        text = "答对 $correctCount",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(34.dp))
        }

        when {
            questions.isEmpty() -> InsufficientPool()
            finished -> Summary(
                correct = correctCount,
                total = queue.size,
                wrong = wrong,
                onRetryWrong = {
                    if (wrong.isNotEmpty()) {
                        queue = wrong
                        index = 0
                        selected = null
                        correctCount = 0
                        wrong = emptyList()
                        finished = false
                    }
                },
                onRestart = {
                    onRestart()
                    queue = emptyList()
                    index = 0
                    selected = null
                    correctCount = 0
                    wrong = emptyList()
                    finished = false
                },
                onBack = onBack,
            )

            current != null -> {
                val answered = selected != null
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = wl.surfaceAlt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = current.word,
                                fontFamily = SerifFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 36.sp,
                                color = if (wl.isDark) Color(0xFF7FCBEF) else Color(0xFF00578A),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "选一个正确的释义",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = wl.inkMuted,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    current.options.forEachIndexed { i, option ->
                        OptionRow(
                            text = option,
                            state = optionState(i, current.answerIndex, selected),
                            onClick = { if (selected == null) selected = i },
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    if (answered) {
                        val isCorrect = selected == current.answerIndex
                        Surface(
                            shape = CircleShape,
                            color = wl.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isCorrect) correctCount += 1 else wrong = wrong + current
                                    if (index + 1 >= queue.size) finished = true else {
                                        index += 1
                                        selected = null
                                    }
                                },
                        ) {
                            Text(
                                text = if (index + 1 >= queue.size) "看结果" else "下一题",
                                style = MaterialTheme.typography.labelLarge,
                                color = wl.onPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 14.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    } else {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

private enum class OptionState { Idle, Correct, Wrong, Muted }

private fun optionState(index: Int, answerIndex: Int, selected: Int?): OptionState = when {
    selected == null -> OptionState.Idle
    index == answerIndex -> OptionState.Correct
    index == selected -> OptionState.Wrong
    else -> OptionState.Muted
}

@Composable
private fun OptionRow(text: String, state: OptionState, onClick: () -> Unit) {
    val wl = WordloomTheme.colors
    val green = if (wl.isDark) Color(0xFF4BD58A) else Color(0xFF0FA646)
    val container = when (state) {
        OptionState.Idle -> wl.surfaceAlt
        OptionState.Correct -> green.copy(alpha = 0.12f)
        OptionState.Wrong -> wl.coral.copy(alpha = 0.12f)
        OptionState.Muted -> wl.surfaceAlt.copy(alpha = 0.55f)
    }
    val border = when (state) {
        OptionState.Correct -> green.copy(alpha = 0.7f)
        OptionState.Wrong -> wl.coral.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val ink = when (state) {
        OptionState.Correct -> green
        OptionState.Wrong -> wl.coral
        OptionState.Muted -> wl.inkMuted.copy(alpha = 0.7f)
        OptionState.Idle -> wl.ink
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun InsufficientPool() {
    val wl = WordloomTheme.colors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "词还不够",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = wl.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "至少查过 4 个词才能出题。先去查几个，再回来练。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = wl.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(260.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(
    correct: Int,
    total: Int,
    wrong: List<QuizQuestion>,
    onRetryWrong: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    val wl = WordloomTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(wl.surfaceAlt, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.wl_spark),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "答对 $correct / $total",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = wl.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (wrong.isEmpty()) "这一轮全对。" else "错的词留在下面，可以只练它们。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = wl.inkMuted,
            )
        }

        if (wrong.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    wrong.forEach { q ->
                        Surface(shape = CircleShape, color = wl.surfaceAlt) {
                            Text(
                                text = q.word,
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
            if (wrong.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = wl.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRetryWrong),
                ) {
                    Text(
                        text = "再练这 ${wrong.size} 个",
                        style = MaterialTheme.typography.labelLarge,
                        color = wl.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Surface(
                shape = CircleShape,
                color = wl.surfaceAlt,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRestart),
            ) {
                Text(
                    text = "再来一轮",
                    style = MaterialTheme.typography.labelLarge,
                    color = wl.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "返回复习",
                style = MaterialTheme.typography.labelLarge,
                color = wl.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
