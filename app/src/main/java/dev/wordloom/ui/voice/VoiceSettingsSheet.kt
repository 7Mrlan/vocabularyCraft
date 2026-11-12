package dev.wordloom.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wordloom.data.audio.Sound
import dev.wordloom.data.audio.SoundService
import dev.wordloom.data.tts.TtsConfig
import dev.wordloom.data.tts.TtsDefaults
import dev.wordloom.data.tts.TtsProtocol
import dev.wordloom.data.tts.TtsStatus
import dev.wordloom.data.tts.TtsTemplate
import dev.wordloom.ui.components.WlSheet
import dev.wordloom.ui.theme.SansFamily
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DEFAULT_ENDPOINT = TtsDefaults.ENDPOINT
private const val DEFAULT_INSTRUCTIONS = TtsDefaults.INSTRUCTIONS
private const val PREVIEW_SENTENCE = TtsDefaults.PREVIEW

private val TEMPLATE_OPTIONS = listOf(
    TtsTemplate.Preset.name to ("预置英文音色" to "官方英文音色，单词更准"),
    TtsTemplate.Design.name to ("设计音色" to "按你的描述生成音色"),
)

/**
 * A quiet listening room: paper, serif labels, one ink action.
 *
 * The top of the sheet chooses how the voice is made — a preset English voice
 * (the default) or the reader's own description. Everything below adapts, and
 * the key is written only through the encrypted store.
 */
@Composable
fun VoiceSettingsSheet(
    status: TtsStatus,
    onSave: (TtsConfig, (Result<Unit>) -> Unit) -> Unit,
    onPreview: (TtsConfig, String, (Result<Unit>) -> Unit) -> Unit,
    onClearKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val ink = wl.ink
    val muted = wl.inkMuted
    val scope = rememberCoroutineScope()

    var protocol by remember {
        mutableStateOf(
            runCatching { TtsProtocol.valueOf(status.protocol) }.getOrDefault(TtsProtocol.MIMO),
        )
    }
    var endpoint by remember { mutableStateOf(status.endpoint.ifBlank { DEFAULT_ENDPOINT }) }
    var template by remember {
        mutableStateOf(
            if (status.model == TtsConfig.VOICE_DESIGN_MODEL) TtsTemplate.Design else TtsTemplate.Preset,
        )
    }
    var presetVoice by remember {
        mutableStateOf(
            status.voice.takeIf { it.isNotBlank() && it != "voicedesign" }
                ?: TtsDefaults.VOICE,
        )
    }
    var designInstructions by remember {
        mutableStateOf(status.instructions.ifBlank { DEFAULT_INSTRUCTIONS })
    }
    // Only used by the OpenAI-compatible protocol.
    var openAiModel by remember { mutableStateOf(status.model.ifBlank { "tts-1" }) }
    var openAiVoice by remember { mutableStateOf(status.voice.ifBlank { "alloy" }) }
    var previewText by remember { mutableStateOf(PREVIEW_SENTENCE) }
    var fallback by remember { mutableStateOf(status.fallback) }
    var key by remember { mutableStateOf("") }
    var keyCleared by remember { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val hasKey = status.hasKey && !keyCleared

    fun draft(): TtsConfig = when {
        protocol == TtsProtocol.OPENAI -> TtsConfig(
            endpoint = endpoint.trim(),
            protocol = protocol,
            model = openAiModel.trim(),
            voice = openAiVoice.trim(),
            instructions = designInstructions.trim(),
            key = key.trim(),
            fallback = fallback,
        )
        template == TtsTemplate.Preset -> TtsConfig(
            endpoint = endpoint.trim(),
            protocol = TtsProtocol.MIMO,
            model = TtsDefaults.PRESET_MODEL,
            voice = presetVoice,
            instructions = "",
            key = key.trim(),
            fallback = fallback,
        )
        else -> TtsConfig(
            endpoint = endpoint.trim(),
            protocol = TtsProtocol.MIMO,
            model = TtsConfig.VOICE_DESIGN_MODEL,
            voice = "",
            instructions = designInstructions.trim(),
            key = key.trim(),
            fallback = fallback,
        )
    }

    fun validate(config: TtsConfig): String? {
        if (config.endpoint.isBlank()) return "请填写接口地址"
        if (config.model.isBlank()) return "请填写模型名称"
        if (config.key.isBlank() && !hasKey) return "请填写 API 密钥"
        if (config.protocol == TtsProtocol.OPENAI && config.voice.isBlank()) return "请填写声音名称"
        if (config.voiceDesign && config.instructions.isBlank()) return "请填写音色描述"
        return null
    }

    WlSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "声音",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "单词与例句都由 AI 朗读；密钥只保存在本机。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                color = muted,
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (status.enabled && !keyCleared) wl.action else wl.coral, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (status.enabled && !keyCleared) "AI 朗读已启用" else "AI 朗读未配置",
                    fontFamily = SerifFamily,
                    fontSize = 14.sp,
                    color = ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (hasKey) "已保存密钥" else "需要密钥",
                    fontSize = 10.5.sp,
                    color = if (hasKey) muted else wl.coral,
                )
            }

            // ---- how the voice is made -------------------------------------
            if (protocol == TtsProtocol.MIMO) {
                VoiceDropdown(
                    label = "朗读方式",
                    value = when (template) {
                        TtsTemplate.Preset -> "预置英文音色"
                        TtsTemplate.Design -> "设计音色"
                    },
                    options = TEMPLATE_OPTIONS.map { it.second },
                    expanded = openMenu == "template",
                    onToggle = { openMenu = if (openMenu == "template") null else "template" },
                    onSelect = {
                        openMenu = null
                        template = runCatching { TtsTemplate.valueOf(it) }.getOrDefault(TtsTemplate.Preset)
                    },
                    selectedKey = template.name,
                    optionKeys = TEMPLATE_OPTIONS.map { it.first },
                )

                when (template) {
                    TtsTemplate.Preset -> VoiceDropdown(
                        label = "音色",
                        value = presetVoice,
                        options = TtsDefaults.VOICES,
                        expanded = openMenu == "voice",
                        onToggle = { openMenu = if (openMenu == "voice") null else "voice" },
                        onSelect = { openMenu = null; presetVoice = it },
                        selectedKey = presetVoice,
                        optionKeys = TtsDefaults.VOICES.map { it.first },
                    )

                    TtsTemplate.Design -> VoiceField(
                        label = "音色描述（设计音色必填）",
                        value = designInstructions,
                        onValueChange = { designInstructions = it },
                        placeholder = "温润知性的成熟女声…",
                        minLines = 3,
                    )
                }
            }

            // ---- engine and endpoint --------------------------------------
            Spacer(Modifier.height(16.dp))
            Text("接口", fontSize = 11.sp, color = muted, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoicePill("MiMo", protocol == TtsProtocol.MIMO) {
                    protocol = TtsProtocol.MIMO
                    openMenu = null
                }
                VoicePill("OpenAI 兼容", protocol == TtsProtocol.OPENAI) {
                    protocol = TtsProtocol.OPENAI
                    openMenu = null
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "使用推荐配置",
                    fontFamily = SerifFamily,
                    fontSize = 12.sp,
                    color = ink,
                    modifier = Modifier
                        .wlPress {
                            endpoint = DEFAULT_ENDPOINT
                            protocol = TtsProtocol.MIMO
                            template = TtsTemplate.Preset
                            presetVoice = TtsDefaults.VOICE
                            openMenu = null
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            if (protocol == TtsProtocol.OPENAI) {
                VoiceField("模型", openAiModel, { openAiModel = it }, "tts-1")
                VoiceField("声音", openAiVoice, { openAiVoice = it }, "alloy")
                VoiceField(
                    label = "朗读风格（选填）",
                    value = designInstructions,
                    onValueChange = { designInstructions = it },
                    placeholder = "例如 softly, slowly",
                )
            }
            VoiceField("接口地址", endpoint, { endpoint = it }, "https://api.xiaomimimo.com/v1")

            // ---- key -------------------------------------------------------
            VoiceField(
                label = "API 密钥",
                value = key,
                onValueChange = {
                    key = it
                    if (it.isNotBlank()) keyCleared = false
                },
                placeholder = if (hasKey) "已保存，留空沿用" else "粘贴密钥",
                password = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (hasKey) wl.action else wl.outline, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (hasKey) "密钥已保存（不会显示）" else "未配置密钥",
                    fontSize = 10.5.sp,
                    color = if (hasKey) muted else wl.coral,
                )
                Spacer(Modifier.weight(1f))
                if (hasKey) {
                    Text(
                        text = "清除",
                        fontFamily = SerifFamily,
                        fontSize = 11.5.sp,
                        color = wl.coral,
                        modifier = Modifier
                            .wlPress {
                                onClearKey()
                                keyCleared = true
                                message = "已清除密钥"
                                failed = false
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }

            // ---- fallback --------------------------------------------------
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("合成失败时使用真人录音", fontSize = 13.sp, color = ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "关闭后只用 AI 声音；失败会提示并重试一次。",
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        color = muted,
                    )
                }
                SoundToggle(fallback) { fallback = it }
            }

            // ---- preview ---------------------------------------------------
            Spacer(Modifier.height(18.dp))
            Text("试听", fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink)
            Spacer(Modifier.height(6.dp))
            VoiceField("试听文本", previewText, { previewText = it }, PREVIEW_SENTENCE)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TtsDefaults.PREVIEW_WORDS.forEach { word ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = wl.surfaceAlt,
                        modifier = Modifier.wlPress { previewText = word },
                    ) {
                        Text(
                            text = word,
                            fontFamily = SerifFamily,
                            fontSize = 12.sp,
                            color = ink,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = if (failed) wl.coral else muted,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = wl.surfaceAlt,
                    modifier = Modifier
                        .weight(1f)
                        .wlPress(enabled = !busy) {
                            val candidate = draft()
                            validate(candidate)?.let {
                                failed = true
                                message = it
                                return@wlPress
                            }
                            busy = true
                            message = "正在合成试听…"
                            failed = false
                            onPreview(candidate, previewText) { result ->
                                busy = false
                                failed = result.isFailure
                                message = result.fold(
                                    onSuccess = { "已播放试听" },
                                    onFailure = { it.message ?: "试听失败" },
                                )
                            }
                        },
                ) {
                    Text(
                        text = if (busy) "试听中…" else "试听",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = ink,
                        modifier = Modifier.padding(vertical = 13.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = wl.ink,
                    modifier = Modifier
                        .weight(1.15f)
                        .wlPress(enabled = !busy && !saved) {
                            val candidate = draft()
                            validate(candidate)?.let {
                                failed = true
                                message = it
                                return@wlPress
                            }
                            busy = true
                            failed = false
                            message = null
                            onSave(candidate) { result ->
                                busy = false
                                result.fold(
                                    onSuccess = {
                                        saved = true
                                        message = "已保存"
                                        scope.launch {
                                            delay(450)
                                            onDismiss()
                                        }
                                    },
                                    onFailure = {
                                        failed = true
                                        message = it.message ?: "保存失败"
                                    },
                                )
                            }
                        },
                ) {
                    Text(
                        text = if (saved) "已保存" else "保存",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = wl.surface,
                        modifier = Modifier.padding(vertical = 13.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "密钥用 Android Keystore 加密保存在本机，不进入备份、日志或 APK。" +
                    "AI 朗读会把单词或例句文本发送到你配置的服务。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
                color = muted.copy(alpha = 0.82f),
            )

            // ---- sound effects --------------------------------------------
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(wl.outline))
            Spacer(Modifier.height(18.dp))
            Text(
                text = "音效",
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "纸、木、毡的轻响；默认不响书架过卡声。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = muted,
            )
            Spacer(Modifier.height(12.dp))
            SoundSettingsSection()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * A compact select: one field showing the current choice, and a paper list that
 * folds out under it. Used for both the template and the voice.
 */
@Composable
private fun VoiceDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    selectedKey: String,
    optionKeys: List<String>,
) {
    val wl = WordloomTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(14.dp))
        Text(label, fontSize = 11.sp, color = wl.inkMuted, letterSpacing = 0.3.sp)
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = wl.surfaceAlt,
            modifier = Modifier
                .fillMaxWidth()
                .wlPress(onClick = onToggle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    fontFamily = SerifFamily,
                    fontSize = 14.5.sp,
                    color = wl.ink,
                    modifier = Modifier.weight(1f),
                )
                Canvas(modifier = Modifier.size(14.dp)) {
                    val stroke = 1.6.dp.toPx()
                    val d = if (expanded) -1f else 1f
                    drawLine(
                        color = wl.inkMuted,
                        start = Offset(size.width * 0.2f, size.height * (0.42f - 0.12f * d)),
                        end = Offset(size.width * 0.5f, size.height * (0.62f + 0.12f * d)),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = wl.inkMuted,
                        start = Offset(size.width * 0.5f, size.height * (0.62f + 0.12f * d)),
                        end = Offset(size.width * 0.8f, size.height * (0.42f - 0.12f * d)),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(wl.surfaceAlt.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp),
            ) {
                options.forEachIndexed { index, (title, note) ->
                    val selected = optionKeys.getOrNull(index) == selectedKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(optionKeys.getOrNull(index) ?: title) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (selected) wl.ink else wl.outline,
                                    RoundedCornerShape(50),
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontFamily = SerifFamily,
                                fontSize = 13.5.sp,
                                color = if (selected) wl.ink else wl.ink.copy(alpha = 0.82f),
                            )
                            Text(text = note, fontSize = 10.5.sp, color = wl.inkMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundSettingsSection() {
    val wl = WordloomTheme.colors
    val context = LocalContext.current
    val sound = remember(context) { SoundService.shared(context) }
    val settings by sound.settings.collectAsStateWithLifecycle()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("启用音效", fontSize = 13.sp, color = wl.ink, modifier = Modifier.weight(1f))
        SoundToggle(settings.enabled) { sound.setEnabled(it) }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("评分音", fontSize = 13.sp, color = wl.ink, modifier = Modifier.weight(1f))
        SoundToggle(settings.ratings) { sound.setRatings(it) }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("拼写音", fontSize = 13.sp, color = wl.ink, modifier = Modifier.weight(1f))
        SoundToggle(settings.spelling) { sound.setSpelling(it) }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("练习答对音", fontSize = 13.sp, color = wl.ink, modifier = Modifier.weight(1f))
        SoundToggle(settings.quiz) { sound.setQuiz(it) }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("书架过卡声", fontSize = 13.sp, color = wl.ink, modifier = Modifier.weight(1f))
        SoundToggle(settings.pageTurn) { sound.setPageTurn(it) }
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("试听", fontSize = 11.sp, color = wl.inkMuted)
        Spacer(Modifier.width(10.dp))
        SoundAudition("评分") { sound.preview(Sound.Easy) }
        Spacer(Modifier.width(6.dp))
        SoundAudition("拼写") { sound.preview(Sound.SpellRight) }
        Spacer(Modifier.width(6.dp))
        SoundAudition("练习") { sound.preview(Sound.QuizCorrect) }
        Spacer(Modifier.width(6.dp))
        SoundAudition("过卡") { sound.preview(Sound.PageTurn) }
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("音量", fontSize = 11.sp, color = wl.inkMuted)
        Spacer(Modifier.width(10.dp))
        Slider(
            value = settings.volume,
            onValueChange = sound::setVolume,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = wl.ink,
                activeTrackColor = wl.ink,
                inactiveTrackColor = wl.outline,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${(settings.volume * 100).roundToInt()}%",
            fontSize = 10.5.sp,
            color = wl.inkMuted,
            modifier = Modifier.width(34.dp),
        )
    }
    Text(
        text = "恢复默认",
        fontFamily = SerifFamily,
        fontSize = 12.sp,
        color = wl.ink,
        modifier = Modifier
            .wlPress { sound.reset() }
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun SoundAudition(label: String, onClick: () -> Unit) {
    val wl = WordloomTheme.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = wl.surfaceAlt,
        modifier = Modifier.wlPress(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = SerifFamily,
            fontSize = 11.sp,
            color = wl.ink,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SoundToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val wl = WordloomTheme.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = if (on) wl.ink else wl.surfaceAlt,
        modifier = Modifier.wlPress { onChange(!on) },
    ) {
        Text(
            text = if (on) "开" else "关",
            fontFamily = SerifFamily,
            fontSize = 11.sp,
            color = if (on) wl.surface else wl.inkMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun VoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val wl = WordloomTheme.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) wl.ink else wl.surfaceAlt,
        modifier = Modifier.wlPress(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = SerifFamily,
            fontSize = 12.5.sp,
            color = if (selected) wl.surface else wl.inkMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun VoiceField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    minLines: Int = 1,
) {
    val wl = WordloomTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 11.sp, color = wl.inkMuted, letterSpacing = 0.3.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = TextStyle(
                fontFamily = SansFamily,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = wl.ink,
            ),
            cursorBrush = SolidColor(wl.primary),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(wl.surfaceAlt, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 14.sp,
                            color = wl.inkMuted.copy(alpha = 0.75f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}
