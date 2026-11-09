package dev.wordloom.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.R
import dev.wordloom.data.ListWord
import dev.wordloom.ui.components.PoolHeader
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

/**
 * One list, opened. Same pool header as the lists shelf; rows carry the gloss we
 * already have cached, so a list reads like a small wordbook rather than a set of
 * bare strings.
 */
@Composable
fun ListDetailScreen(
    name: String,
    words: List<ListWord>,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val wl = WordloomTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface),
    ) {
        PoolHeader(height = 196.dp) {
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
            }
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = name,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (words.isEmpty()) "空词表" else "${words.size} 个词",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(46.dp))
        }

        if (words.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .background(wl.surfaceAlt, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.wl_list),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "这个词表还是空的",
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = wl.ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "查词时点「Add to list」，词就会收进来。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = wl.inkMuted,
                        modifier = Modifier.width(260.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 28.dp),
            ) {
                items(words, key = { it.word }) { item ->
                    ListWordRow(
                        item = item,
                        onClick = { onWord(item.word) },
                        onRemove = { onRemove(item.word) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListWordRow(
    item: ListWord,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val wordInk = if (wl.isDark) Color(0xFF7FCBEF) else Color(0xFF00578A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.word,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = wordInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.gloss.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.gloss,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = wl.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.wl_close),
                contentDescription = "从词表移除",
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    HorizontalDivider(color = wl.outline.copy(alpha = 0.6f))
}
