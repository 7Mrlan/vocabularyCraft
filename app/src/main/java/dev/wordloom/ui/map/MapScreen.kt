package dev.wordloom.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import org.maplibre.android.geometry.LatLng

@Composable
fun MapScreen(
    counters: MapCounters,
    words: List<Pair<String, String>>,
    onBack: () -> Unit,
    onRandomWord: () -> Unit,
    onOpenWord: (String) -> Unit,
) {
    val wl = WordloomTheme.colors
    var index by remember(words.size) { mutableIntStateOf(0) }

    // The card advances with the camera, not on its own clock.
    fun nextWord() {
        if (words.size > 1) index = (index + 1) % words.size
    }

    Box(modifier = Modifier.fillMaxSize().background(wl.primaryDeep)) {
        WorldMap(
            modifier = Modifier.fillMaxSize(),
            styleUrl = BasemapStyle.POSITRON,
            zoom = 1.2,
            target = LatLng(28.0, 18.0),
            tour = true,
            tourZoom = 3.2,
            onTourStop = { nextWord() },
        )
        MapTint(modifier = Modifier.fillMaxSize())

        // Bottom fade so the attribution sits on a calm edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, wl.primaryDeep.copy(alpha = 0.85f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.wl_back),
                        contentDescription = "返回",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.28f)) {
                    Text(
                        text = "拖动看看 · 双指缩放",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "你的学习地图",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))

            // Ink, not blue: the numbers are the only bright thing on a pale map,
            // and the card reads as a slate laid over it.
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF102438).copy(alpha = 0.90f),
                shadowElevation = 14.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "你的进度",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(10.dp))
                    MapCountersRow(counters = counters, contentColor = Color.White)
                }
            }
        }

        // Callout card, cycling through saved words. The whole card opens the
        // entry; the word is already in the local cache, so it opens at once.
        words.getOrNull(index)?.let { (word, preview) ->
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .widthIn(max = 236.dp)
                    .clickable(onClick = { onOpenWord(word) }),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFDFCF9),
                shadowElevation = 16.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = word,
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        color = wl.ink,
                    )
                    if (preview.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = preview.take(80) + if (preview.length > 80) "…" else "",
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = wl.inkMuted,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "点开词条",
                            fontSize = 11.5.sp,
                            color = wl.primary,
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            painter = painterResource(R.drawable.wl_chevron),
                            contentDescription = null,
                            tint = wl.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "底图 © OpenStreetMap contributors · OpenFreeMap",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = RoundedCornerShape(50), color = wl.action) {
                    Text(
                        text = "随机一个词",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clickable(onClick = onRandomWord)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
