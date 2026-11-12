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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wordloom.data.audio.Sound
import dev.wordloom.data.audio.SoundService
import dev.wordloom.data.tts.TtsConfig
import dev.wordloom.data.tts.TtsDefaults
import dev.wordloom.data.tts.TtsDraft
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

// A small, printed-card type scale: nothing in this sheet is louder than 16sp.
private val TitleSize = 16.sp
private val SectionSize = 10.sp
private val LabelSize = 10.sp
private val ValueSize = 13.sp
private val NoteSize = 10.sp
private val ToggleLabelSize = 12.5.sp
private val ActionSize = 12.5.sp

/**
 * A listening card, set like a printed form: small type, hairline rules, ink
 * controls. The top chooses how the voice is made (a preset English voice by
 * default, or the reader's own description); the rest is a quiet ledger.
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
            status.voice.takeIf { voice -> TtsDefaults.VOICES.any { it.first == voice } }
                ?: TtsDefaults.VOICE,
        )
    }
    var designInstructions by remember {
        mutableStateOf(status.instructions.ifBlank { DEFAULT_INSTRUCTIONS })
    }
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

    fun draft(): TtsDraft = when {
        protocol == TtsProtocol.OPENAI -> TtsDraft(
            endpoint = endpoint,
            protocol = protocol,
            model = openAiModel,
            voice = openAiVoice,
            instructions = designInstructions,
            key = key,
            fallback = fallback,
        )
        template == TtsTemplate.Preset -> TtsDraft(
            endpoint = endpoint,
            protocol = TtsProtocol.MIMO,
            model = TtsDefaults.PRESET_MODEL,
            voice = presetVoice,
            // Kept even though the preset request ignores it, so switching back
            // to Design restores the description instead of the default.
            instructions = designInstructions,
            key = key,
            fallback = fallback,
        )
        else -> TtsDraft(
            endpoint = endpoint,
            protocol = TtsProtocol.MIMO,
            model = TtsConfig.VOICE_DESIGN_MODEL,
            voice = "",
            instructions = designInstructions,
            key = key,
            fallback = fallback,
        )
    }

    fun runPreview() {
        val candidate = draft()
        candidate.validate(hasKey)?.let {
            failed = true
            message = it
            return
        }
        if (previewText.isBlank()) {
            failed = true
            message = "请填写试听文本"
            return
        }
        busy = true
        message = "正在合成试听…"
        failed = false
        onPreview(candidate.toConfig(), previewText) { result ->
            busy = false
            failed = result.isFailure
            message = result.fold(
                onSuccess = { "已播放试听" },
                onFailure = { it.message ?: "试听失败" },
            )
        }
    }

    fun runSave() {
        val candidate = draft()
        candidate.validate(hasKey)?.let {
            failed = true
            message = it
            return
        }
        busy = true
        failed = false
        message = null
        onSave(candidate.toConfig()) { result ->
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
    }

    WlSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 18.dp),
        ) {
            // ---- header ----------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "声音",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                    color = ink,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(
                            if (status.enabled && !keyCleared) wl.action else wl.coral,
                            RoundedCornerShape(50),
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (status.enabled && !keyCleared) "AI 朗读已启用" else "未配置密钥",
                    fontSize = NoteSize,
                    color = muted,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "单词与例句由 AI 朗读，密钥只保存在本机。",
                fontSize = NoteSize,
                lineHeight = 15.sp,
                color = muted.copy(alpha = 0.9f),
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("朗读")

            if (protocol == TtsProtocol.MIMO) {
                UnderlineSelect(
                    label = "朗读方式",
                    value = when (template) {
                        TtsTemplate.Preset -> "预置英文音色"
                        TtsTemplate.Design -> "设计音色"
                    },
                    expanded = openMenu == "template",
                    onToggle = { openMenu = if (openMenu == "template") null else "template" },
                ) {
                    TEMPLATE_OPTIONS.forEach { (name, pair) ->
                        MenuRow(
                            title = pair.first,
                            note = pair.second,
                            selected = template.name == name,
                        ) {
                            openMenu = null
                            template = runCatching { TtsTemplate.valueOf(name) }
                                .getOrDefault(TtsTemplate.Preset)
                        }
                    }
                }

                when (template) {
                    TtsTemplate.Preset -> UnderlineSelect(
                        label = "音色",
                        value = presetVoice,
                        expanded = openMenu == "voice",
                        onToggle = { openMenu = if (openMenu == "voice") null else "voice" },
                    ) {
                        TtsDefaults.VOICES.forEach { (name, note) ->
                            MenuRow(title = name, note = note, selected = presetVoice == name) {
                                openMenu = null
                                presetVoice = name
                            }
                        }
                    }

                    TtsTemplate.Design -> UnderlineInput(
                        label = "音色描述",
                        value = designInstructions,
                        onValueChange = { designInstructions = it },
                        placeholder = "温润知性的成熟女声…",
                        minLines = 2,
                    )
                }
            }

            UnderlineInput("接口地址", endpoint, { endpoint = it }, DEFAULT_ENDPOINT)
            if (protocol == TtsProtocol.OPENAI) {
                UnderlineInput("模型", openAiModel, { openAiModel = it }, "tts-1")
                UnderlineInput("声音", openAiVoice, { openAiVoice = it }, "alloy")
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("接口", fontSize = LabelSize, color = muted, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (protocol == TtsProtocol.MIMO) {
                        "MiMo · 切换到 OpenAI 兼容"
                    } else {
                        "OpenAI 兼容 · 切换到 MiMo"
                    },
                    fontSize = NoteSize,
                    color = ink,
                    modifier = Modifier
                        .wlPress {
                            protocol = if (protocol == TtsProtocol.MIMO) {
                                TtsProtocol.OPENAI
                            } else {
                                TtsProtocol.MIMO
                            }
                            openMenu = null
                        }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            UnderlineInput(
                label = "API 密钥",
                value = key,
                onValueChange = {
                    key = it
                    if (it.isNotBlank()) keyCleared = false
                },
                placeholder = if (hasKey) "已保存，留空沿用" else "粘贴密钥",
                password = true,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(if (hasKey) wl.action else wl.outline, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (hasKey) "密钥已保存（不会显示）" else "未配置密钥",
                    fontSize = NoteSize,
                    color = if (hasKey) muted else wl.coral,
                )
                Spacer(Modifier.weight(1f))
                if (hasKey) {
                    Text(
                        text = "清除",
                        fontFamily = SerifFamily,
                        fontSize = NoteSize,
                        color = wl.coral,
                        modifier = Modifier
                            .wlPress {
                                // Clearing cancels any running preview; without
                                // this the sheet would stay "busy" forever.
                                busy = false
                                onClearKey()
                                keyCleared = true
                                message = "已清除密钥"
                                failed = false
                            }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                    )
                }
            }

            // ---- fallback --------------------------------------------------
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                label = "合成失败时使用真人录音",
                on = fallback,
                onChange = { fallback = it },
            )
            Text(
                text = "关闭后只用 AI 声音；失败会提示并重试一次。",
                fontSize = NoteSize,
                lineHeight = 14.sp,
                color = muted.copy(alpha = 0.9f),
            )

            // ---- preview ---------------------------------------------------
            Spacer(Modifier.height(18.dp))
            SectionLabel("试听")
            UnderlineInput("文本", previewText, { previewText = it }, PREVIEW_SENTENCE)
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TtsDefaults.PREVIEW_WORDS.forEach { word ->
                    Text(
                        text = word,
                        fontFamily = SerifFamily,
                        fontSize = 10.5.sp,
                        color = ink,
                        modifier = Modifier
                            .background(wl.surfaceAlt, RoundedCornerShape(6.dp))
                            .wlPress { previewText = word }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }

            message?.let {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = it,
                    fontSize = NoteSize,
                    lineHeight = 14.sp,
                    color = if (failed) wl.coral else muted,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (busy) "合成中…" else "试听",
                    fontFamily = SerifFamily,
                    fontSize = ActionSize,
                    color = if (busy) muted else ink,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier
                        .wlPress(enabled = !busy) { runPreview() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = wl.ink,
                    modifier = Modifier.wlPress(enabled = !busy && !saved) { runSave() },
                ) {
                    Text(
                        text = if (saved) "已保存" else "保存",
                        textAlign = TextAlign.Center,
                        fontFamily = SerifFamily,
                        fontSize = ActionSize,
                        color = wl.surface,
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "密钥用 Android Keystore 加密保存，不进入备份、日志或 APK。",
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = muted.copy(alpha = 0.8f),
            )

            // ---- sound effects --------------------------------------------
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(wl.outline.copy(alpha = 0.7f)))
            Spacer(Modifier.height(14.dp))
            SectionLabel("音效")
            Text(
                text = "纸、木、毡的轻响；默认不响书架过卡声。",
                fontSize = NoteSize,
                color = muted.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(6.dp))
            SoundSettingsSection()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val wl = WordloomTheme.colors
    Text(
        text = text,
        fontSize = SectionSize,
        letterSpacing = 1.2.sp,
        color = wl.inkMuted,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * One field on the printed card: a small label, the value at reading size, and
 * a hairline instead of a filled box.
 */
@Composable
private fun UnderlineInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    minLines: Int = 1,
) {
    val wl = WordloomTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = LabelSize, color = wl.inkMuted, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = TextStyle(
                fontFamily = SansFamily,
                fontSize = ValueSize,
                lineHeight = 19.sp,
                color = wl.ink,
            ),
            cursorBrush = SolidColor(wl.ink),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = ValueSize,
                            color = wl.inkMuted.copy(alpha = 0.7f),
                        )
                    }
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(wl.outline))
    }
}

@Composable
private fun UnderlineSelect(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    menu: @Composable () -> Unit,
) {
    val wl = WordloomTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = LabelSize, color = wl.inkMuted, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wlPress(onClick = onToggle)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                fontFamily = SerifFamily,
                fontSize = ValueSize,
                color = wl.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Canvas(modifier = Modifier.size(12.dp)) {
                val stroke = 1.3.dp.toPx()
                val d = if (expanded) -1f else 1f
                drawLine(
                    color = wl.inkMuted,
                    start = Offset(size.width * 0.2f, size.height * (0.42f - 0.1f * d)),
                    end = Offset(size.width * 0.5f, size.height * (0.6f + 0.1f * d)),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = wl.inkMuted,
                    start = Offset(size.width * 0.5f, size.height * (0.6f + 0.1f * d)),
                    end = Offset(size.width * 0.8f, size.height * (0.42f - 0.1f * d)),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(wl.outline))
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(wl.surfaceAlt.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(vertical = 3.dp),
            ) {
                menu()
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    note: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(4.dp)
                .background(if (selected) wl.ink else wl.outline, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontFamily = SerifFamily,
            fontSize = 12.sp,
            color = if (selected) wl.ink else wl.ink.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = note, fontSize = 9.5.sp, color = wl.inkMuted)
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val wl = WordloomTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = ToggleLabelSize, color = wl.ink, modifier = Modifier.weight(1f))
        Text(
            text = if (on) "开" else "关",
            fontFamily = SerifFamily,
            fontSize = 11.sp,
            color = if (on) wl.ink else wl.inkMuted,
            modifier = Modifier
                .wlPress { onChange(!on) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SoundSettingsSection() {
    val wl = WordloomTheme.colors
    val context = LocalContext.current
    val sound = remember(context) { SoundService.shared(context) }
    val settings by sound.settings.collectAsStateWithLifecycle()

    ToggleRow("启用音效", settings.enabled) { sound.setEnabled(it) }
    ToggleRow("评分音", settings.ratings) { sound.setRatings(it) }
    ToggleRow("拼写音", settings.spelling) { sound.setSpelling(it) }
    ToggleRow("练习答对音", settings.quiz) { sound.setQuiz(it) }
    ToggleRow("书架过卡声", settings.pageTurn) { sound.setPageTurn(it) }

    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("音量", fontSize = LabelSize, color = wl.inkMuted)
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
            fontSize = 10.sp,
            color = wl.inkMuted,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("试听", fontSize = LabelSize, color = wl.inkMuted)
        Spacer(Modifier.width(12.dp))
        Audition("评分") { sound.preview(Sound.Easy) }
        Spacer(Modifier.width(10.dp))
        Audition("拼写") { sound.preview(Sound.SpellRight) }
        Spacer(Modifier.width(10.dp))
        Audition("练习") { sound.preview(Sound.QuizCorrect) }
        Spacer(Modifier.width(10.dp))
        Audition("过卡") { sound.preview(Sound.PageTurn) }
        Spacer(Modifier.weight(1f))
        Text(
            text = "恢复默认",
            fontFamily = SerifFamily,
            fontSize = 10.sp,
            color = wl.inkMuted,
            modifier = Modifier
                .wlPress { sound.reset() }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )
    }
}

@Composable
private fun Audition(label: String, onClick: () -> Unit) {
    val wl = WordloomTheme.colors
    Text(
        text = label,
        fontFamily = SerifFamily,
        fontSize = 10.5.sp,
        color = wl.ink,
        modifier = Modifier
            .wlPress(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    )
}

@Composable
internal fun VoiceField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    minLines: Int = 1,
) = UnderlineInput(label, value, onValueChange, placeholder, password, minLines)
