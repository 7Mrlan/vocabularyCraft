package dev.wordloom.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

@Composable
internal fun BooksPage(
    visible: Boolean,
    ttsEnabled: Boolean,
    onSkip: () -> Unit,
    onOpenBooks: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OnboardingStepHeader(step = "04", onSkip = onSkip)
        Spacer(Modifier.height(18.dp))
        Text(
            text = "从一本书开始",
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = wl.ink,
        )
        Spacer(Modifier.height(20.dp))
        OnboardingStepRow(0, "书架 → 选一本词书", visible)
        OnboardingStepRow(1, "开始学习 → 每天 10 个新词", visible)
        OnboardingStepRow(2, "左右滑看例句，书签收藏", visible)
        Spacer(Modifier.height(22.dp))
        Text(
            text = "查词：搜索 / 长按选词翻译\n复习：到期卡自动进队列",
            style = MaterialTheme.typography.bodyMedium,
            color = wl.inkMuted,
        )
        Spacer(Modifier.height(24.dp))
        if (!ttsEnabled) {
            Text(
                text = "AI 朗读未配置 · 可在首页「收纳 → 声音」里设置",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = wl.coral,
            )
            Spacer(Modifier.height(10.dp))
        }
        OnboardingPrimaryButton(text = "去书架", onClick = onOpenBooks)
        Spacer(Modifier.height(54.dp))
    }
}

@Composable
private fun OnboardingStepRow(index: Int, text: String, visible: Boolean) {
    val wl = WordloomTheme.colors
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            alpha.animateTo(1f, tween(280, delayMillis = index * 50))
        } else {
            alpha.snapTo(0f)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = (1f - alpha.value) * 10.dp.toPx()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(wl.surfaceAlt, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                fontFamily = SerifFamily,
                fontSize = 12.sp,
                color = wl.ink,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp),
            color = wl.ink,
        )
    }
}
