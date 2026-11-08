package dev.wordloom.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

@Composable
fun ListDetailScreen(
    name: String,
    words: List<String>,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val wl = WordloomTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = wl.ink)
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = wl.ink,
                )
                Text(
                    text = "${words.size} 个词",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = wl.inkMuted,
                )
            }
        }

        if (words.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "这个词表还是空的。查词时点「加入词表」就能放进来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = wl.inkMuted,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 12.dp, top = 8.dp, bottom = 28.dp,
                ),
            ) {
                items(words, key = { it }) { word ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onWord(word) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = word,
                            fontFamily = SerifFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = wl.ink,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemove(word) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "从词表移除",
                                tint = wl.inkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
