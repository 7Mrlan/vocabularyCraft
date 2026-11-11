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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import dev.wordloom.ui.theme.WlMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.wordloom.data.audio.AudioCache
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
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
                // The pill stays white in both themes, so this is the light-mode
                // muted ink: the dark token would be nearly invisible here.
                color = Color(0xFF5B6B82),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun SpeakerButton(url: String, modifier: Modifier = Modifier) {
    val player = rememberAudioPlayer()
    val wl = WordloomTheme.colors
    var playing by remember { mutableStateOf(false) }
    // Pull the clip while the card is being read, so the first tap has nothing
    // to download.
    LaunchedEffect(url) { player.prefetch(url) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = WlMotion.press,
        label = "speakerPress",
    )
    val pulse = rememberInfiniteTransition(label = "speakerPlaying")
    val wave by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "speakerWave",
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .graphicsLayer {
                val s = pressScale * if (playing) wave else 1f
                scaleX = s
                scaleY = s
            }
            .background(
                if (playing) wl.ink.copy(alpha = 0.10f) else wl.surfaceAlt,
                CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                playing = true
                player.play(url) { playing = false }
            },
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
    val player = remember(context) { AudioPlayer(AudioCache(context)) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

class AudioPlayer(private val cache: AudioCache) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var job: Job? = null

    /** Warm the on-disk cache without playing anything. */
    fun prefetch(url: String) {
        if (url.isBlank()) return
        scope.launch { cache.fetch(url) }
    }

    fun play(url: String, onDone: () -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            playOne(url)
            onDone()
        }
    }

    /** Plays the clips in order, one after the other (the "play all" button). */
    fun playAll(urls: List<String>) {
        if (urls.isEmpty()) return
        job?.cancel()
        job = scope.launch { urls.forEach { playOne(it) } }
    }

    /**
     * MediaPlayer cannot open an http source on the main thread — setDataSource
     * throws NetworkOnMainThreadException, which used to be swallowed and made the
     * button look dead. Fetch to the cache first, then play a local file.
     */
    private suspend fun playOne(url: String) {
        val file = cache.fetch(url) ?: return
        suspendCancellableCoroutine { continuation ->
            releasePlayer()
            val player = MediaPlayer()
            mediaPlayer = player
            val finish = {
                runCatching { player.release() }
                if (mediaPlayer === player) mediaPlayer = null
                if (continuation.isActive) continuation.resume(Unit)
            }
            runCatching {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { finish() }
                player.setOnErrorListener { _, _, _ -> finish(); true }
                player.prepareAsync()
            }.onFailure { finish() }
            continuation.invokeOnCancellation {
                runCatching { player.release() }
                if (mediaPlayer === player) mediaPlayer = null
            }
        }
    }

    fun release() {
        job?.cancel()
        releasePlayer()
    }

    private fun releasePlayer() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}
