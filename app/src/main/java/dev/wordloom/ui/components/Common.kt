package dev.wordloom.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.ui.theme.WordloomTheme

@Composable
fun SearchPill(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.wl_search_light),
                contentDescription = null,
                tint = Color.Unspecified,
            )
            Text(
                text = placeholder,
                color = WordloomTheme.colors.inkMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun PosChip(text: String) {
    val wl = WordloomTheme.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = wl.surfaceAlt,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = wl.primaryDeep,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = WordloomTheme.colors.ink,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

@Composable
fun DefinitionText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontStyle = FontStyle.Italic,
            fontSize = 17.sp,
            lineHeight = 27.sp,
        ),
        color = WordloomTheme.colors.ink,
        modifier = modifier,
    )
}

@Composable
fun ExampleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WordloomTheme.colors.inkMuted,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun SpeakerButton(url: String, modifier: Modifier = Modifier) {
    val player = rememberAudioPlayer()
    val wl = WordloomTheme.colors
    Box(
        modifier = modifier
            .size(34.dp)
            .background(wl.surfaceAlt, CircleShape)
            .clickable { player.play(url) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.wl_volume),
            contentDescription = "播放发音",
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Small outlined pill that plays every pronunciation clip in sequence. */
@Composable
fun PlayAllChip(
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.wl_volume),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "全部",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = color,
        )
    }
}

@Composable
fun rememberAudioPlayer(): AudioPlayer {
    val context = LocalContext.current
    val player = remember { AudioPlayer() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var queue: List<String> = emptyList()
    private var queueIndex = 0

    fun play(url: String) {
        queue = emptyList()
        start(url)
    }

    /** Plays the clips in order, one after the other (the "play all" button). */
    fun playAll(urls: List<String>) {
        if (urls.isEmpty()) return
        queue = urls
        queueIndex = 0
        start(urls.first())
    }

    private fun start(url: String) {
        release()
        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release()
                    if (mediaPlayer == it) mediaPlayer = null
                    val next = queue.getOrNull(queueIndex + 1)
                    if (next != null) {
                        queueIndex += 1
                        start(next)
                    } else {
                        queue = emptyList()
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    true
                }
                prepareAsync()
            }
        }.getOrNull()
    }

    fun release() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}
