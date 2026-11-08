package dev.wordloom.ui.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.data.model.WordEntry
import dev.wordloom.ui.WordUiState
import dev.wordloom.ui.components.SpeakerButton
import dev.wordloom.ui.theme.SansFamily
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme

// Word-page palette from the site's own stylesheet and CSS variables
// (dictionary-kd1ket.css + --pos-color-* in main.css).
//
// The light values are the reference site's own. Dark values are ours: the same
// hues lifted in lightness so the page keeps its identity on #0B1320 instead of
// turning into dark-on-dark. Everything here is read through LocalWordLook so
// the two sets never get mixed.
@Immutable
private data class WordLook(
    val isDark: Boolean,
    val headline: Color,
    val link: Color,
    val lead: Color,
    val body: Color,
    val forms: Color,
    val example: Color,
    val synLabel: Color,
    val panel: Color,
    val banner: Color,
    /** Background of the outlined "Add to list" chip. */
    val chipSurface: Color,
)

private val LightLook = WordLook(
    isDark = false,
    headline = Color(0xFF00578A),   // .word-area h1 / .span-replace-h3
    link = Color(0xFF0090E3),       // .button outline, links
    lead = Color(0xFF222222),       // .blurb.short
    body = Color(0xFF444444),       // .blurb.long
    forms = Color(0xFF545454),      // p.word-forms
    example = Color(0xFF666666),    // .definition .pos .examples
    synLabel = Color(0xFF888888),
    panel = Color(0xFFEDF7FC),      // pale blue panel
    banner = Color(0xFFBE6DCF),     // "Definitions of …" banner
    chipSurface = Color.White,
)

private val DarkLook = WordLook(
    isDark = true,
    headline = Color(0xFF6FC3EA),
    link = Color(0xFF4CB8F5),
    lead = Color(0xFFE6EBF1),
    body = Color(0xFFBFCAD8),
    forms = Color(0xFF9AA9BE),
    example = Color(0xFF93A2B5),
    synLabel = Color(0xFF7D8CA0),
    panel = Color(0xFF16293A),
    banner = Color(0xFFBE6DCF),
    chipSurface = Color(0xFF0F1E2B),
)

// --pos-color-* variables: each part of speech keeps its own hue.
private fun posColor(pos: String, dark: Boolean = false): Color {
    val key = pos.lowercase().trim()
    return if (dark) when (key) {
        "noun" -> Color(0xFF4BD58A)
        "verb" -> Color(0xFFFF9350)
        "adjective" -> Color(0xFF56B6F0)
        "adverb" -> Color(0xFFA894FF)
        "preposition" -> Color(0xFFD79AE6)
        "pronoun", "conjunction" -> Color(0xFF85D24F)
        "interjection" -> Color(0xFFFF87CB)
        else -> Color(0xFF9AA9BE)
    } else when (key) {
        "noun" -> Color(0xFF0FA646)
        "verb" -> Color(0xFFCD4D03)
        "adjective" -> Color(0xFF007BC4)
        "adverb" -> Color(0xFF7B61FF)
        "preposition" -> Color(0xFFBE6DCF)
        "pronoun", "conjunction" -> Color(0xFF44AA02)
        "interjection" -> Color(0xFFFA52B7)
        else -> Color(0xFF545454)
    }
}

@Composable
fun WordScreen(
    state: WordUiState,
    lists: List<dev.wordloom.data.local.ListWithCount>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleDeck: (WordEntry, Boolean) -> Unit,
    onAddToList: (Long, String) -> Unit,
    onCreateList: (String, (Long) -> Unit) -> Unit,
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = wl.ink)
            }
            val appBarWord = (state as? WordUiState.Loaded)?.entry?.word
            if (appBarWord != null) {
                Text(
                    text = appBarWord,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = wl.inkMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (state is WordUiState.Loaded) {
                val inDeck = state.inDeck
                IconButton(onClick = { onToggleDeck(state.entry, inDeck) }) {
                    Icon(
                        imageVector = if (inDeck) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (inDeck) "取消收藏" else "加入复习",
                        // When active, use the vibrant primary blue from the home screen (#005AFB)
                        tint = if (inDeck) wl.primary else wl.inkMuted,
                    )
                }
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = wl.inkMuted)
            }
        }

        when (state) {
            is WordUiState.Loading -> CenterBox {
                CircularProgressIndicator(color = wl.primary)
            }

            is WordUiState.NotFound -> CenterBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有找到 “${state.term}”", style = MaterialTheme.typography.titleMedium, color = wl.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "检查拼写，或换一个词形试试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = wl.inkMuted,
                    )
                }
            }

            is WordUiState.Failed -> CenterBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("查询失败", style = MaterialTheme.typography.titleMedium, color = wl.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = wl.inkMuted)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("重试") }
                }
            }

            is WordUiState.Loaded -> WordContent(
                entry = state.entry,
                inDeck = state.inDeck,
                lists = lists,
                onToggleDeck = { onToggleDeck(state.entry, state.inDeck) },
                onAddToList = onAddToList,
                onCreateList = onCreateList,
            )

            WordUiState.Idle -> CenterBox {
                Text("输入一个词开始", color = wl.inkMuted)
            }
        }
    }
}

@Composable
private fun WordContent(
    entry: WordEntry,
    inDeck: Boolean,
    lists: List<dev.wordloom.data.local.ListWithCount>,
    onToggleDeck: () -> Unit,
    onAddToList: (Long, String) -> Unit,
    onCreateList: (String, (Long) -> Unit) -> Unit,
) {
    var showLists by remember { mutableStateOf(false) }
    var showAllSenses by remember { mutableStateOf(false) }
    val visibleSenses = if (showAllSenses) entry.senses else entry.senses.take(4)
    val wl = WordloomTheme.colors
    val look = if (wl.isDark) DarkLook else LightLook
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp, end = 24.dp, top = 6.dp, bottom = 40.dp,
        ),
    ) {
        // 1. Header: Word Title + "+ Add to list" button
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = entry.word,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = look.headline,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = look.chipSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, look.link),
                    modifier = Modifier.clickable { showLists = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = look.link,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Add to list",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = look.link,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 2. Pronunciations: US Flag + Audio + IPA / UK Flag + Audio + IPA
        if (entry.ipa.isNotEmpty() || entry.audio.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val usAudio = entry.audio.firstOrNull { it.label.equals("US", true) } ?: entry.audio.firstOrNull()
                    val usIpa = entry.ipa.firstOrNull { it.label.equals("US", true) }?.text ?: entry.ipa.firstOrNull()?.text
                    if (usAudio != null || usIpa != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "🇺🇸", fontSize = 16.sp)
                            if (usAudio != null) {
                                SpeakerButton(url = usAudio.url)
                            }
                            if (usIpa != null) {
                                Text(
                                    text = usIpa,
                                    fontFamily = SerifFamily,
                                    fontSize = 17.sp,
                                    color = look.headline,
                                )
                            }
                        }
                    }

                    val ukAudio = entry.audio.firstOrNull { it.label.equals("UK", true) }
                    val ukIpa = entry.ipa.firstOrNull { it.label.equals("UK", true) }?.text
                    if (ukAudio != null || ukIpa != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "🇬🇧", fontSize = 16.sp)
                            if (ukAudio != null) {
                                SpeakerButton(url = ukAudio.url)
                            }
                            if (ukIpa != null) {
                                Text(
                                    text = ukIpa,
                                    fontFamily = SerifFamily,
                                    fontSize = 17.sp,
                                    color = look.headline,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // 3. Other forms: quicker; quickest; quicks
        if (!entry.forms.isNullOrBlank()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Other forms: ",
                        fontFamily = SansFamily,
                        fontSize = 15.sp,
                        color = look.forms,
                    )
                    Text(
                        text = entry.forms,
                        fontFamily = SansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = wl.ink,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // 4. The Blurb (Short & Long story-like paragraphs)
        if (!entry.shortBlurb.isNullOrBlank()) {
            item {
                Text(
                    text = entry.shortBlurb,
                    fontFamily = SerifFamily,
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    color = look.lead,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (!entry.longBlurb.isNullOrBlank()) {
            item {
                Text(
                    text = entry.longBlurb,
                    fontFamily = SansFamily,
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    color = look.body,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // Fallback blurb if neither short nor long blurb is provided
        if (entry.shortBlurb.isNullOrBlank() && entry.longBlurb.isNullOrBlank()) {
            val fallback = entry.etymology ?: entry.senses.firstOrNull()?.definition
            if (!fallback.isNullOrBlank()) {
                item {
                    Text(
                        text = fallback,
                        fontFamily = SerifFamily,
                        fontSize = 17.sp,
                        lineHeight = 28.sp,
                        color = look.lead,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // 5. Definitions of [word] purple banner
        if (entry.senses.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .background(
                            look.banner,
                            RoundedCornerShape(topStart = 4.dp, bottomEnd = 4.dp, topEnd = 0.dp, bottomStart = 0.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Definitions of ",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = Color.White,
                        )
                        Text(
                            text = entry.word,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 6. Numbered definitions list
            itemsIndexed(visibleSenses) { index, sense ->
                val pc = posColor(sense.partOfSpeech, look.isDark)
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            color = wl.inkMuted,
                            modifier = Modifier.width(24.dp),
                        )
                        // (pos) capsule
                        Box(
                            modifier = Modifier
                                .background(pc.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                                .border(1.dp, pc.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = sense.partOfSpeech.ifBlank { "definition" }.lowercase(),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = pc,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = sense.definition,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            ),
                            color = look.lead,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Example
                    sense.examples.forEach { example ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            color = look.example,
                            modifier = Modifier.padding(start = 34.dp),
                        )
                    }

                    // Synonyms list. One annotated Text so a long list wraps
                    // under the words, not around the "synonyms:" label.
                    val syns = sense.synonymsList.ifEmpty { sense.synonyms.flatMap { it.words } }
                    if (syns.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 13.sp,
                                        color = look.synLabel,
                                    ),
                                ) { append("synonyms: ") }
                                withStyle(
                                    SpanStyle(fontSize = 14.sp, color = look.headline),
                                ) { append(syns.joinToString(", ")) }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 34.dp),
                        )
                    }
                }
            }

            if (entry.senses.size > 4) {
                item {
                    TextButton(onClick = { showAllSenses = !showAllSenses }) {
                        Text(
                            text = if (showAllSenses) "收起" else "显示全部 ${entry.senses.size} 个义项",
                            color = look.link,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // 7. Bottom action buttons
        item {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onToggleDeck,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (inDeck) look.panel else wl.primary,
                        contentColor = if (inDeck) look.headline else wl.onPrimary,
                    ),
                ) {
                    Text(if (inDeck) "已在复习队列" else "加入复习", maxLines = 1)
                }
                Button(
                    onClick = { showLists = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        // Light: the reference site's navy. Dark: the app's own
                        // primary + onPrimary, since navy on #0B1320 disappears.
                        containerColor = if (look.isDark) wl.primary else look.headline,
                        contentColor = if (look.isDark) wl.onPrimary else Color.White,
                    ),
                ) {
                    Text("加入词表", maxLines = 1)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "来源：${entry.sourceName}",
                style = MaterialTheme.typography.bodySmall,
                color = wl.inkMuted,
            )
            if (entry.sourceUrl.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = look.link,
                    modifier = Modifier.clickable {
                        runCatching { uriHandler.openUri(entry.sourceUrl) }
                    },
                )
            }
        }
    }

    if (showLists) {
        WordListsDialog(
            entry = entry,
            lists = lists,
            onDismiss = { showLists = false },
            onAddToList = onAddToList,
            onCreateList = onCreateList,
        )
    }
}

@Composable
private fun WordListsDialog(
    entry: WordEntry,
    lists: List<dev.wordloom.data.local.ListWithCount>,
    onDismiss: () -> Unit,
    onAddToList: (Long, String) -> Unit,
    onCreateList: (String, (Long) -> Unit) -> Unit,
) {
    dev.wordloom.ui.lists.AddToListDialog(
        word = entry.word,
        lists = lists,
        onDismiss = onDismiss,
        onPick = { id ->
            onAddToList(id, entry.word)
            onDismiss()
        },
        onCreate = { name, onCreated ->
            onCreateList(name) { id ->
                onCreated(id)
                onAddToList(id, entry.word)
                onDismiss()
            }
        },
    )
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}
