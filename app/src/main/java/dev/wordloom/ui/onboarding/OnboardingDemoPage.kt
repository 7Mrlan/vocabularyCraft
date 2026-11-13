package dev.wordloom.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import dev.wordloom.ui.components.immersiveTopPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress
import dev.wordloom.ui.voice.SpeechButton

@Composable
internal fun DemoPage(onNext: () -> Unit, onSkip: () -> Unit) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .immersiveTopPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OnboardingStepHeader(step = "03", onSkip = onSkip)
        Spacer(Modifier.height(18.dp))
        Text(
            text = "一张卡，三种看法",
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = wl.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点四档评分 → 揭晓；左滑看例句，右滑回主卡。",
            style = MaterialTheme.typography.bodyMedium,
            color = wl.inkMuted,
        )
        Spacer(Modifier.height(18.dp))
        DemoCard()
        Spacer(Modifier.height(18.dp))
        Text("左滑看例句 · 右滑回主卡", style = MaterialTheme.typography.bodySmall, color = wl.inkMuted)
        Spacer(Modifier.height(4.dp))
        Text("「中文」看释义 · 「记错了」重排", style = MaterialTheme.typography.bodySmall, color = wl.inkMuted)
        Spacer(Modifier.height(24.dp))
        OnboardingPrimaryButton(text = "下一步", onClick = onNext)
        Spacer(Modifier.height(54.dp))
    }
}

@Composable
private fun DemoCard() {
    val wl = WordloomTheme.colors
    var showExample by rememberSaveable { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = { drag = 0f },
                    onDragEnd = {
                        if (drag <= -60f) showExample = true
                        if (drag >= 60f) showExample = false
                        drag = 0f
                    },
                    onHorizontalDrag = { _, amount ->
                        drag += amount
                    },
                )
            },
    ) {
        AnimatedContent(
            targetState = showExample,
            transitionSpec = {
                (slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 2 } + fadeOut(tween(160)))
            },
            label = "demoCard",
        ) { back ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = wl.surface,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!back) {
                    DemoFront(onRate = { showExample = true })
                } else {
                    DemoBack()
                }
            }
        }
    }
}

@Composable
private fun DemoFront(onRate: () -> Unit) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "make",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = wl.ink,
            )
            Spacer(Modifier.weight(1f))
            SpeechButton(text = "make", fallbackUrl = null)
        }
        Column {
            Text(
                text = "凭第一反应评分",
                style = MaterialTheme.typography.bodySmall,
                color = wl.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("忘记", "模糊", "认识", "秒懂").forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = wl.surfaceAlt,
                        modifier = Modifier
                            .weight(1f)
                            .wlPress(onClick = onRate),
                    ) {
                        Text(
                            text = label,
                            fontFamily = SerifFamily,
                            fontSize = 11.sp,
                            color = wl.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 9.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "点任意评分 → 揭晓例句",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = wl.inkMuted,
            )
        }
    }
}

@Composable
private fun DemoBack() {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "例句",
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = wl.inkMuted,
        )
        Text(
            text = "She made a cake for the party.",
            fontFamily = SerifFamily,
            fontSize = 21.sp,
            lineHeight = 29.sp,
            color = wl.ink,
        )
        Text(
            text = "她为聚会做了一个蛋糕。",
            style = MaterialTheme.typography.bodyMedium,
            color = wl.inkMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeechButton(text = "She made a cake for the party.", fallbackUrl = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "右滑回主卡",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = wl.inkMuted,
            )
        }
    }
}
