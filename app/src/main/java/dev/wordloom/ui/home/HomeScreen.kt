package dev.wordloom.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.wordloom.R
import dev.wordloom.ui.components.drawSearchRipples
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.wordloom.data.model.WordEntry
import dev.wordloom.ui.components.SearchPill
import dev.wordloom.ui.components.WaveDivider
import dev.wordloom.ui.theme.SerifFamily
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import dev.wordloom.ui.backup.BackupSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import dev.wordloom.ui.theme.wlPress
import dev.wordloom.ui.theme.WordloomTheme

private val HeroTop = Color(0xFF061B45)
private val HeroMid = Color(0xFF0A2E7A)
private val HeroBottom = Color(0xFF0C3B9E)

@Composable
fun HomeScreen(
    dueCount: Int,
    totalCards: Int,
    recent: List<WordEntry>,
    dailyWord: WordEntry?,
    dailyInDeck: Boolean,
    onAddDaily: (WordEntry) -> Unit,
    onSearch: () -> Unit,
    onRandom: () -> Unit,
    onReview: () -> Unit,
    onWord: (String) -> Unit,
    onOpenMap: () -> Unit,
    onOpenLists: () -> Unit,
    onExportBackup: suspend () -> String,
    onImportBackup: suspend (String) -> Result<Int>,
    onQuiz: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenVoice: () -> Unit = {},
    ttsEnabled: Boolean = false,
    onReplayOnboarding: () -> Unit = {},
) {
    val wl = WordloomTheme.colors
    val band = wl.primaryBright

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackup by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupBusy = true
            try {
                val text = attempt { onExportBackup() }.getOrNull()
                // "wt" truncates: some providers hand back a stream that ignores a
                // plain "w" and leaves the document at zero bytes.
                val ok = text != null && attempt {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("无法写入")
                    stream.use { it.write(text.toByteArray()) }
                }.isSuccess
                showBackup = false
                Toast.makeText(
                    context,
                    if (ok) "已导出备份" else "导出失败",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                backupBusy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupBusy = true
            try {
                val text = attempt {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                val result = if (text == null) Result.failure(IllegalStateException("读不到文件"))
                else onImportBackup(text)
                showBackup = false
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { "已合并 $it 条记录" },
                        onFailure = { "恢复失败：${it.message}" },
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                backupBusy = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(wl.surface)
            // The feed runs edge to edge; keep the last card clear of the
            // gesture bar and give it room to breathe.
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        item {
            var pillCenterY by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(HeroTop, HeroMid, HeroBottom)))
                    .statusBarsPadding()
                    .drawBehind {
                        if (pillCenterY > 0f) {
                            drawSearchRipples(Offset(x = 40.dp.toPx(), y = pillCenterY))
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 44.dp),
                ) {
                    BrandRow(
                        onOpenMap = onOpenMap,
                        onOpenLists = onOpenLists,
                        onOpenBackup = { showBackup = true },
                        onToggleTheme = onToggleTheme,
                        onOpenBooks = onOpenBooks,
                        onOpenVoice = onOpenVoice,
                        ttsEnabled = ttsEnabled,
                        onReplayOnboarding = onReplayOnboarding,
                    )
                    Spacer(Modifier.height(22.dp))
                    SearchPill(
                        placeholder = "搜索单词、释义或用法",
                        onClick = onSearch,
                        modifier = Modifier.onGloballyPositioned {
                            pillCenterY = it.positionInRoot().y + it.size.height / 2f
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onRandom),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.wl_spark_knockout),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "随机一个词",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        Text(
                            text = "·",
                            color = Color.White.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.width(18.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onQuiz),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.wl_book_knockout),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "四选一练习",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                    }
                }

                WaveDivider(
                    color = band,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(52.dp),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(band)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp),
            ) {
                // Three equal cards get cramped under 400dp. One primary action
                // carries the hierarchy, and the two quiet ones sit below it.
                PrimaryActionCard(
                    background = Color.White,
                    // The card is white in both themes; the dark-mode primary is
                    // tuned for dark surfaces and would wash out on it.
                    contentColor = if (wl.isDark) wl.primaryDeep else wl.primary,
                    iconRes = R.drawable.wl_book_light,
                    title = "查一个词",
                    subtitle = "英英释义 · 音标 · 发音",
                    onClick = onSearch,
                )
                Spacer(Modifier.height(10.dp))
                // Both cards are statistics, not buttons with a label: the right
                // side carries the number or word that makes the card worth
                // tapping, so no half of it sits empty.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val lastWord = recent.firstOrNull()?.word
                    ArrowCard(
                        modifier = Modifier.weight(1f),
                        background = wl.coralSoft,
                        contentColor = wl.coral,
                        title = "复习",
                        subtitle = if (dueCount > 0) "$dueCount 个待复习" else "今日已清空",
                        onClick = onReview,
                    )
                    ArrowCard(
                        modifier = Modifier.weight(1f),
                        background = wl.mint,
                        contentColor = wl.mintInk,
                        title = "最近",
                        subtitle = lastWord ?: "遇见生词",
                        onClick = if (lastWord == null) onRandom else ({ onWord(lastWord) }),
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(wl.surface),
            ) {
                WaveDivider(
                    color = band,
                    flipped = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = wl.surfaceAlt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp)
                        .clickable(onClick = onReview),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (dueCount > 0) "今天有 $dueCount 个词等你" else "今天没有待复习的词",
                                style = MaterialTheme.typography.titleMedium,
                                color = wl.ink,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "复习队列里共 $totalCards 个词 · FSRS 间隔重复",
                                style = MaterialTheme.typography.bodySmall,
                                color = wl.inkMuted,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(wl.action, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.wl_forward),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        dailyWord?.let { entry ->
            item {
                DailyWordCard(
                    entry = entry,
                    inDeck = dailyInDeck,
                    onAdd = { onAddDaily(entry) },
                    onOpen = { onWord(entry.word) },
                )
            }
        }

        // CC BY-SA attribution for the word lists that ship inside the APK.
        // Definitions themselves are fetched at runtime and never bundled.
        item {
            Text(
                text = "词表：NGSL 1.2（CC BY-SA 4.0，Browne / Culligan / Phillips）" +
                    " · CET / 考研大纲仅取词形\n词典正文在运行时抓取，不随应用分发。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 16.sp),
                color = wl.inkMuted.copy(alpha = 0.8f),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 26.dp, bottom = 4.dp),
            )
        }
    }

    if (showBackup) {
        BackupSheet(
            busy = backupBusy,
            onDismiss = { showBackup = false },
            onExport = { exportLauncher.launch("wordloom-backup.json") },
            onImport = { importLauncher.launch(arrayOf("application/json")) },
        )
    }
}

/** [runCatching] that never swallows coroutine cancellation. */
private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

@Composable
private fun BrandRow(
    onOpenMap: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenBackup: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenVoice: () -> Unit,
    ttsEnabled: Boolean,
    onReplayOnboarding: () -> Unit,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    val handleRotation by animateFloatAsState(
        targetValue = if (drawerOpen) 45f else 0f,
        animationSpec = tween(180),
        label = "drawerHandle",
    )
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Box(modifier = Modifier.fillMaxWidth().zIndex(10f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The mark itself flips the palette: same icon, one more job.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "切换深浅色", onClick = onToggleTheme)
                    .padding(end = 6.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    val stroke = Stroke(width = 2.2f * density)
                    drawCircle(color = Color.White, radius = size.minDimension / 2.6f, style = stroke)
                    drawCircle(
                        color = Color.White,
                        radius = 2.4f * density,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 1.2f * density,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Wordloom",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                    // Three actions share this row; never let the wordmark wrap.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            BrandCircle(R.drawable.wl_book_knockout, "词书", onOpenBooks)
            Spacer(Modifier.width(8.dp))
            BrandCircle(R.drawable.wl_list_knockout, "词表", onOpenLists)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                    .clickable(
                        onClickLabel = if (drawerOpen) "收起工具" else "展开工具",
                        onClick = { drawerOpen = !drawerOpen },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = handleRotation },
                ) {
                    val stroke = 1.6f * density
                    drawLine(
                        Color.White,
                        Offset(0f, size.height * 0.38f),
                        Offset(size.width, size.height * 0.38f),
                        stroke,
                        StrokeCap.Round,
                    )
                    drawLine(
                        Color.White,
                        Offset(0f, size.height * 0.62f),
                        Offset(size.width, size.height * 0.62f),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = 50.dp)
                .zIndex(11f),
            enter = fadeIn(tween(150)) +
                scaleIn(
                    tween(200),
                    initialScale = 0.94f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ) +
                slideInVertically(tween(200)) { -8 },
            exit = fadeOut(tween(120)) +
                scaleOut(
                    tween(160),
                    targetScale = 0.97f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFDFCF9),
                shadowElevation = 14.dp,
                modifier = Modifier.width(218.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "工具",
                        fontSize = 9.5.sp,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFF8A97A8),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    DrawerRow(
                        R.drawable.wl_pin,
                        "地图",
                        "打开学习地图",
                        onClick = { drawerOpen = false; onOpenMap() },
                    )
                    DrawerDivider()
                    DrawerRow(
                        R.drawable.wl_archive,
                        "备份与恢复",
                        "备份与恢复",
                        onClick = { drawerOpen = false; onOpenBackup() },
                    )
                    DrawerDivider()
                    DrawerRow(
                        R.drawable.wl_volume,
                        "声音",
                        "声音设置",
                        trailing = if (ttsEnabled) null else "未配置",
                        onClick = { drawerOpen = false; onOpenVoice() },
                    )
                    DrawerDivider()
                    Text(
                        text = "重新看引导",
                        fontFamily = SerifFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF8A97A8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "重新看引导") {
                                drawerOpen = false
                                onReplayOnboarding()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandCircle(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DrawerRow(
    @DrawableRes icon: Int,
    label: String,
    description: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color(0xFF162941),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = SerifFamily,
            fontSize = 15.sp,
            color = Color(0xFF162941),
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(it, fontSize = 10.sp, color = Color(0xFFF06989))
        }
    }
}

@Composable
private fun DrawerDivider() {
    Box(
        Modifier
            .padding(start = 46.dp, end = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE8ECF2)),
    )
}

@Composable
private fun PrimaryActionCard(
    background: Color,
    contentColor: Color,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wlPress(scale = 0.985f, dim = false, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(contentColor.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                )
            }
            Icon(
                painter = painterResource(R.drawable.wl_forward),
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ArrowCard(
    modifier: Modifier = Modifier,
    background: Color,
    contentColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(76.dp)
            .wlPress(scale = 0.985f, dim = false, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = contentColor,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(contentColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.wl_forward),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}


@Composable
private fun DailyWordCard(
    entry: WordEntry,
    inDeck: Boolean,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val sense = entry.senses.firstOrNull()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
            .wlPress(scale = 0.985f, dim = false, onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = wl.surface,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "每日一词",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = wl.inkMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.word,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = wl.ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                entry.audio.firstOrNull()?.let { clip ->
                    dev.wordloom.ui.voice.SpeechButton(
                        text = entry.word,
                        fallbackUrl = clip.url,
                    )
                }
            }

            entry.ipa.firstOrNull()?.let { p ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = p.text,
                    style = MaterialTheme.typography.bodyMedium,
                    // primaryDeep is a light-mode-only shade; on #0B1320 it vanishes.
                    color = if (wl.isDark) wl.primary else wl.primaryDeep,
                )
            }

            val blurb = entry.shortBlurb?.takeIf { it.isNotBlank() } ?: sense?.definition
            if (blurb != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = blurb,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = SerifFamily,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                    ),
                    color = wl.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (inDeck) wl.surfaceAlt else wl.primary,
                ) {
                    Text(
                        text = if (inDeck) "已在复习队列" else "加入复习",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (inDeck) wl.inkMuted else wl.onPrimary,
                        modifier = Modifier
                            .clickable(enabled = !inDeck, onClick = onAdd)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "查看词条",
                    style = MaterialTheme.typography.labelLarge,
                    color = wl.primary,
                    modifier = Modifier
                        .clickable(onClick = onOpen)
                        .padding(vertical = 9.dp),
                )
            }
        }
    }
}
