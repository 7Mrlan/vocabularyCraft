package dev.wordloom.ui.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.local.CardEntity
import dev.wordloom.srs.Rating
import dev.wordloom.ui.components.PoolHeader
import dev.wordloom.ui.feedback.rememberAppFeedback
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

@Composable
fun ReviewScreen(
    dueCards: List<CardEntity>,
    onBack: () -> Unit,
    onRate: (CardEntity, Rating) -> Unit,
    onOpenWord: (String) -> Unit,
    onQuiz: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val feedback = rememberAppFeedback()
    var queue by remember { mutableStateOf<List<CardEntity>>(emptyList()) }
    var reviewed by remember { mutableIntStateOf(0) }

    LaunchedEffect(dueCards) {
        if (queue.isEmpty() && dueCards.isNotEmpty()) queue = dueCards
    }

    val current = queue.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .navigationBarsPadding(),
    ) {
        PoolHeader(height = 172.dp) {
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
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .clickable(onClick = onQuiz)
                        .padding(end = 10.dp),
                ) {
                    Text(
                        text = "练习",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    )
                }
                Text(
                    text = "已复习 $reviewed",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "复习",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (queue.isEmpty()) "今天清空" else "${queue.size} 个待复习",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
        }

        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "今天的复习完成了",
                        style = MaterialTheme.typography.headlineSmall,
                        color = wl.ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "去查几个新词，或者回来再看一遍收藏夹。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = wl.inkMuted,
                    )
                }
            }
            return@Column
        }

        // Revealed state is per word, so a card that was flipped stays flipped
        // while it slides out after rating.
        val revealedWords = remember { mutableStateListOf<String>() }
        val revealed = current.word in revealedWords

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            val view = LocalView.current
            val card = current
            val cardRevealed = card.word in revealedWords
            val rotation by animateFloatAsState(
                targetValue = if (cardRevealed) 180f else 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 260f),
                label = "cardFlip",
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable {
                        if (!cardRevealed) {
                            revealedWords.add(card.word)
                            feedback.reveal(view)
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                color = wl.surfaceAlt,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 14f * density
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (rotation <= 90f) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = card.word,
                                fontFamily = SerifFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 40.sp,
                                color = wl.ink,
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "点击卡片显示释义",
                                style = MaterialTheme.typography.bodyMedium,
                                color = wl.inkMuted,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = 180f }
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = card.word,
                                fontFamily = SerifFamily,
                                fontSize = 20.sp,
                                color = wl.inkMuted,
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = card.preview.ifBlank { "（这个条目没有缓存释义，点下方查看词条）" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = wl.ink,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { onOpenWord(card.word) }) {
                                Text("查看完整词条", color = wl.primary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = "重来",
                    hint = "忘了",
                    background = wl.coralSoft,
                    contentColor = wl.coral,
                    enabled = revealed,
                    onClick = {
                        feedback.commit(view)
                        onRate(current, Rating.Again)
                        queue = queue.drop(1); reviewed += 1
                    },
                )
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = "困难",
                    hint = "勉强",
                    background = wl.surfaceAlt,
                    contentColor = wl.ink,
                    enabled = revealed,
                    onClick = {
                        feedback.commit(view)
                        onRate(current, Rating.Hard)
                        queue = queue.drop(1); reviewed += 1
                    },
                )
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = "良好",
                    hint = "记得",
                    background = wl.primary,
                    contentColor = Color.White,
                    enabled = revealed,
                    onClick = {
                        onRate(current, Rating.Good)
                        queue = queue.drop(1); reviewed += 1
                    },
                )
                RatingButton(
                    modifier = Modifier.weight(1f),
                    label = "容易",
                    hint = "秒答",
                    background = wl.mint,
                    contentColor = wl.mintInk,
                    enabled = revealed,
                    onClick = {
                        onRate(current, Rating.Easy)
                        queue = queue.drop(1); reviewed += 1
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RatingButton(
    modifier: Modifier = Modifier,
    label: String,
    hint: String,
    background: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = contentColor,
            disabledContainerColor = background.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
        }
    }
}
