package dev.wordloom.ui.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.ui.theme.SerifFamily

data class MapCounters(
    val saved: Int = 0,
    val due: Int = 0,
    val reviewed: Int = 0,
)

@Composable
fun MapCountersRow(
    counters: MapCounters,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Counter("收藏词", counters.saved, contentColor)
        Counter("待复习", counters.due, contentColor)
        Counter("近 7 天复习", counters.reviewed, contentColor)
    }
}

@Composable
private fun Counter(label: String, value: Int, contentColor: Color) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "counter-$label",
    )
    Column {
        Text(
            text = animated.toString(),
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            color = contentColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.8f),
        )
    }
}
