package dev.wordloom.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress

@Composable
internal fun OnboardingStepHeader(step: String, onSkip: () -> Unit) {
    val wl = WordloomTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$step / 04",
            fontSize = 10.5.sp,
            letterSpacing = 1.4.sp,
            color = wl.inkMuted,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "跳过",
            fontFamily = SerifFamily,
            fontSize = 13.sp,
            color = wl.inkMuted,
            modifier = Modifier
                .wlPress(onClick = onSkip)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun OnboardingDots(page: Int, count: Int = 4, light: Boolean = false) {
    val wl = WordloomTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == page) 7.dp else 5.dp)
                    .background(
                        if (index == page) {
                            if (light) Color.White else wl.coral
                        } else {
                            if (light) Color.White.copy(alpha = 0.35f) else wl.inkMuted.copy(alpha = 0.35f)
                        },
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

@Composable
internal fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    light: Boolean = false,
) {
    val wl = WordloomTheme.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = if (light) Color.White else wl.ink,
        modifier = Modifier
            .fillMaxWidth()
            .wlPress(onClick = onClick),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            color = if (light) Color(0xFF061B45) else wl.surface,
            modifier = Modifier.padding(vertical = 15.dp),
        )
    }
}
