package dev.wordloom.ui.word

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.data.model.WordEntry
import dev.wordloom.ui.theme.SansFamily
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

/**
 * Every synonym of a word, grouped by the sense it belongs to. Tapping a chip
 * opens that word, which is how the reference site's synonym pages behave.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SynonymsScreen(
    entry: WordEntry?,
    onBack: () -> Unit,
    onWord: (String) -> Unit,
) {
    val wl = WordloomTheme.colors
    val headline = if (wl.isDark) androidx.compose.ui.graphics.Color(0xFF6FC3EA) else wl.primaryDeep
    val panel = if (wl.isDark) androidx.compose.ui.graphics.Color(0xFF16293A) else androidx.compose.ui.graphics.Color(0xFFEDF7FC)

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
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = wl.ink)
            }
            Text(
                text = "Synonyms",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = wl.inkMuted,
            )
        }

        val groups = entry?.senses?.mapNotNull { sense ->
            val words = sense.synonymsList.ifEmpty { sense.synonyms.flatMap { it.words } }.distinct()
            if (words.isEmpty()) null else sense to words
        }.orEmpty()

        val loaded = entry
        if (loaded == null || groups.isEmpty()) {
            Text(
                text = if (loaded == null) "正在读取词条…" else "这个词暂时没有收录同义词。",
                style = MaterialTheme.typography.bodyMedium,
                color = wl.inkMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp, end = 24.dp, top = 6.dp, bottom = 40.dp,
            ),
        ) {
            item {
                Text(
                    text = loaded.word,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = headline,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${groups.sumOf { it.second.size }} 个同义词 · 按义项分组",
                    style = MaterialTheme.typography.bodySmall,
                    color = wl.inkMuted,
                )
                Spacer(Modifier.height(20.dp))
            }

            items(groups.size) { index ->
                val (sense, words) = groups[index]
                Column(modifier = Modifier.padding(bottom = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                            color = wl.inkMuted,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = sense.partOfSpeech.lowercase().ifBlank { "definition" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = wl.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sense.definition,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = wl.ink,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        words.forEach { word ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = panel,
                                modifier = Modifier.clickable { onWord(word) },
                            ) {
                                Text(
                                    text = word,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = SansFamily,
                                        fontSize = 14.sp,
                                    ),
                                    color = headline,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "同义词来自 ${loaded.sourceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = wl.inkMuted,
                )
            }
        }
    }
}
