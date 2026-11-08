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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = WordloomTheme.colors.primary,
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
            imageVector = Icons.Filled.VolumeUp,
            contentDescription = "播放发音",
            tint = wl.primary,
            modifier = Modifier.size(18.dp),
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

    fun play(url: String) {
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
