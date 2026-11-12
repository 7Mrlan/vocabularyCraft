package dev.wordloom.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.data.tts.TtsConfig
import dev.wordloom.data.tts.TtsResult
import dev.wordloom.data.tts.TtsStatus
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val HeroTop = Color(0xFF061B45)
private val HeroMid = Color(0xFF0A2E7A)
private val HeroBottom = Color(0xFF0C3B9E)

/**
 * The first-run guide. Four pages, one idea each, and no dependency on the
 * reader's data: it can finish offline and without a TTS key.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    status: TtsStatus,
    onSaveTts: (TtsConfig, (Result<Unit>) -> Unit) -> Unit,
    onTestTts: (TtsConfig, String, (Result<TtsResult>) -> Unit) -> Unit,
    onStopAudio: () -> Unit = {},
    onFinish: (skipped: Boolean, openBooks: Boolean) -> Unit,
) {
    val wl = WordloomTheme.colors
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val next: () -> Unit = {
        scope.launch {
            pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(3))
        }
    }
    val skip: () -> Unit = { onFinish(!status.enabled, false) }

    Box(Modifier.fillMaxSize().background(wl.surface)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> WelcomePage(onStart = next, onSkip = skip)
                1 -> TtsSetupPage(
                    status = status,
                    onSave = onSaveTts,
                    onTest = onTestTts,
                    onStopAudio = onStopAudio,
                    onNext = next,
                    onSkip = skip,
                )
                2 -> DemoPage(onNext = next, onSkip = skip)
                else -> BooksPage(
                    visible = pager.currentPage == 3,
                    ttsEnabled = status.enabled,
                    onSkip = skip,
                    onOpenBooks = { onFinish(!status.enabled, true) },
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            OnboardingDots(page = pager.currentPage, light = pager.currentPage == 0)
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit, onSkip: () -> Unit) {
    val wave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        wave.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HeroTop, HeroMid, HeroBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.055f),
                radius = size.minDimension * 0.58f,
                center = Offset(w * 0.92f, h * 0.16f),
                style = Stroke(1.2f * density),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.035f),
                radius = size.minDimension * 0.78f,
                center = Offset(w * 0.92f, h * 0.16f),
                style = Stroke(1.2f * density),
            )
            val path = Path()
            val steps = 180
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = w * t
                val y = h * 0.78f + sin(t * 2f * PI.toFloat() + wave.value * 0.8f) * h * 0.018f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            drawPath(path, Color.White.copy(alpha = 0.10f))
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.16f),
                style = Stroke(width = 1.2f * density),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .padding(bottom = 54.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(24.dp)) {
                    val stroke = 2f * density
                    drawCircle(Color.White, size.minDimension / 2.6f, style = Stroke(stroke))
                    drawCircle(Color.White, 2.2f * density, center = center)
                    drawLine(
                        Color.White,
                        Offset(size.width / 2f, 0f),
                        Offset(size.width / 2f, size.height),
                        1.1f * density,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Wordloom",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    color = Color.White,
                )
            }
            Column {
                Text(
                    text = "查词 · 背词\n都在这张卡上",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "本地优先，不登录，不上传。",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            Column {
                OnboardingPrimaryButton(
                    text = "开始",
                    light = true,
                    onClick = onStart,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "跳过",
                    fontFamily = SerifFamily,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wlPress(onClick = onSkip)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
