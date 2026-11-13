package dev.wordloom.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import dev.wordloom.data.tts.forTemplate
import dev.wordloom.data.tts.model
import dev.wordloom.ui.components.WlSheet
import dev.wordloom.ui.theme.SansFamily
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PaperLight = Color(0xFFF7F4ED)
private val CardLight = Color(0xFFFFFDF8)
private val HairLight = Color(0xFFE3DDCF)
private val WashLight = Color(0xFFEFE9DB)

// The whole sheet speaks in this one scale. Nothing exceeds 14.5sp.
private val TitleSize = 14.5.sp
private val SectionSize = 9.5.sp
private val BodySize = 11.5.sp
private val CardNameSize = 12.sp
private val CardNoteSize = 8.5.sp
private val NoteSize = 9.5.sp
private val ActionSize = 12.5.sp

/** The five bars are the voice's signature; each voice gets its own shape. */
private val VoiceWaves = mapOf(
    "Mia" to listOf(2f, 5f, 8f, 3f, 6f, 9f, 4f, 7f, 3f, 8f, 5f, 2f, 6f, 3f, 1f),
    "Chloe" to listOf(2f, 4f, 7f, 3f, 6f, 2f, 8f, 4f, 6f, 2f, 5f, 3f),
    "Milo" to listOf(3f, 5f, 4f, 6f, 3f, 5f, 4f, 6f, 3f, 4f),
    "Dean" to listOf(4f, 6f, 3f, 7f, 4f, 6f, 3f, 5f),
)

private enum class Glyph { Wave, Play, Key, Speaker, Reset }

/**
 * The listening page: a preset English voice by default, a described voice one
 * switch away, and the sound effects in the same ledger. The whole screen fits
 * without scrolling; only an expanded connection or a small phone scrolls.
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
    val paper = if (wl.isDark) wl.surface else PaperLight
    val card = if (wl.isDark) wl.surfaceAlt else CardLight
    val hair = if (wl.isDark) wl.outline else HairLight
    val wash = if (wl.isDark) Color(0xFF101E31) else WashLight
    val shape = RoundedCornerShape(7.dp)
    val scope = rememberCoroutineScope()

    val storedProtocol = remember(status.protocol) {
        runCatching { TtsProtocol.valueOf(status.protocol) }.getOrDefault(TtsProtocol.MIMO)
    }
    // A stored model that is neither template belongs to the reader. Until they
    // pick a card or switch template, save it back verbatim instead of silently
    // rewriting it to the recommended one.
    val customModel = status.model.takeIf {
        it.isNotBlank() && it != TtsDefaults.PRESET_MODEL && it != TtsConfig.VOICE_DESIGN_MODEL
    }
    val customVoice = status.voice.takeIf { it.isNotBlank() && TtsDefaults.VOICES.none { v -> v.first == it } }
    var voiceTouched by remember { mutableStateOf(false) }

    var engine by remember { mutableStateOf(storedProtocol) }
    var openAiModel by remember {
        mutableStateOf(
            if (storedProtocol == TtsProtocol.OPENAI) status.model.ifBlank { "tts-1" } else "tts-1",
        )
    }
    var openAiVoice by remember {
        mutableStateOf(
            if (storedProtocol == TtsProtocol.OPENAI) status.voice.ifBlank { "alloy" } else "alloy",
        )
    }
    var openAiInstructions by remember {
        mutableStateOf(if (storedProtocol == TtsProtocol.OPENAI) status.instructions else "")
    }

    var endpoint by remember { mutableStateOf(status.endpoint.ifBlank { TtsDefaults.ENDPOINT }) }
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
        mutableStateOf(status.instructions.ifBlank { TtsDefaults.INSTRUCTIONS })
    }
    var previewText by remember { mutableStateOf(TtsDefaults.PREVIEW) }
    var fallback by remember { mutableStateOf(status.fallback) }
    var key by remember { mutableStateOf("") }
    var keyCleared by remember { mutableStateOf(false) }
    var connectionOpen by remember { mutableStateOf(storedProtocol == TtsProtocol.OPENAI) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val hasKey = status.hasKey && !keyCleared
    val connectionReady = !keyCleared && endpoint.isNotBlank() && (hasKey || key.isNotBlank())
    val engineReady = if (engine == TtsProtocol.OPENAI) {
        openAiModel.isNotBlank() && openAiVoice.isNotBlank()
    } else {
        template != TtsTemplate.Design || designInstructions.isNotBlank()
    }
    val configured = connectionReady && engineReady

    fun draft(): TtsDraft {
        val base = TtsDraft(
            endpoint = endpoint,
            protocol = TtsProtocol.MIMO,
            model = TtsTemplate.Preset.model,
            voice = presetVoice,
            // Kept on the preset too, so a switch back to Design restores the
            // reader's own words instead of the default.
            instructions = designInstructions,
            key = key,
            fallback = fallback,
        )
        if (engine == TtsProtocol.OPENAI) {
            return base.copy(
                protocol = TtsProtocol.OPENAI,
                model = openAiModel,
                voice = openAiVoice,
                instructions = openAiInstructions,
            )
        }
        return base.forTemplate(
            template = template,
            keepCustom = storedProtocol == TtsProtocol.MIMO && customModel != null && !voiceTouched,
            customProtocol = storedProtocol,
            customModel = status.model,
            customVoice = status.voice,
            presetVoice = presetVoice,
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
        failed = false
        message = "正在合成试听…"
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

    WlSheet(onDismiss = onDismiss, containerColor = paper, handleColor = hair) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).imePadding()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "声音",
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = TitleSize,
                        color = ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (configured) wl.action else wl.coral,
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            configured -> "AI 朗读已启用"
                            connectionReady && engine == TtsProtocol.OPENAI -> "还差模型或声音"
                            connectionReady -> "还差音色描述"
                            hasKey || key.isNotBlank() -> "未完成配置"
                            else -> "未配置密钥"
                        },
                        fontSize = NoteSize,
                        color = muted,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "单词与例句由 AI 朗读，密钥只保存在本机。",
                    fontSize = NoteSize,
                    lineHeight = 14.sp,
                    color = muted.copy(alpha = 0.9f),
                )

                SectionHeader(
                    text = "朗读",
                    glyph = Glyph.Wave,
                    modifier = Modifier.padding(top = 12.dp),
                    trailing = {
                        if (engine == TtsProtocol.MIMO) {
                            TemplateSwitch(template, wash, card, ink, muted) {
                                template = it
                                voiceTouched = true
                            }
                        }
                    },
                )

                if (engine == TtsProtocol.OPENAI) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "OpenAI 兼容接口：模型与声音在「连接」里设置。",
                        fontSize = NoteSize,
                        color = muted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${openAiModel.ifBlank { "未设置模型" }} · ${openAiVoice.ifBlank { "未设置声音" }}",
                        fontSize = NoteSize,
                        color = muted.copy(alpha = 0.8f),
                    )
                } else {
                    if (storedProtocol == TtsProtocol.MIMO && customModel != null && !voiceTouched) {
                        val who = customVoice?.let { " · $it" } ?: ""
                        Text(
                            text = "当前为自定义配置：${customModel}$who",
                            fontSize = NoteSize,
                            color = muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    if (template == TtsTemplate.Design) {
                        Text("音色描述", fontSize = NoteSize, color = muted)
                        Spacer(Modifier.height(4.dp))
                        DesignField(
                            value = designInstructions,
                            onValueChange = { designInstructions = it },
                            placeholder = "温润知性的成熟女声…",
                            ink = ink,
                            muted = muted,
                            card = card,
                            hair = hair,
                            shape = shape,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            TtsDefaults.VOICES.forEach { (name, note) ->
                                VoiceCard(
                                    name = name,
                                    note = note,
                                    selected = name == presetVoice,
                                    ink = ink,
                                    muted = muted,
                                    card = card,
                                    hair = hair,
                                    shape = shape,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        presetVoice = name
                                        voiceTouched = true
                                    },
                                )
                            }
                        }
                    }
                }

                SectionHeader("试听", Glyph.Play, Modifier.padding(top = 18.dp))
                PreviewField(
                    value = previewText,
                    onValueChange = { previewText = it },
                    ink = ink,
                    muted = muted,
                    hair = hair,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TtsDefaults.PREVIEW_WORDS.forEach { word ->
                        PreviewWord(word, ink, muted) {
                            previewText = word
                            runPreview()
                        }
                    }
                }

                SectionHeader("连接", Glyph.Key, Modifier.padding(top = 18.dp))
                val protocolLabel = if (engine == TtsProtocol.OPENAI) "OpenAI 兼容" else "MiMo"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wlPress(scale = 0.99f, dim = false) { connectionOpen = !connectionOpen }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(protocolLabel, fontSize = 11.sp, color = muted)
                    Text(" · ", fontSize = 11.sp, color = muted)
                    Text(
                        text = endpoint
                            .removePrefix("https://")
                            .removePrefix("http://"),
                        fontSize = 11.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (connectionOpen) "收起" else "查看",
                        fontFamily = SerifFamily,
                        fontSize = 10.5.sp,
                        color = ink,
                        textDecoration = TextDecoration.Underline,
                    )
                }
                AnimatedVisibility(
                    visible = connectionOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        EngineSwitch(engine, ink, muted) { next ->
                            engine = next
                            connectionOpen = true
                        }
                        SheetField("接口地址", endpoint, { endpoint = it }, TtsDefaults.ENDPOINT, ink, muted, hair)
                        if (engine == TtsProtocol.OPENAI) {
                            SheetField(
                                label = "模型",
                                value = openAiModel,
                                onValueChange = { openAiModel = it },
                                placeholder = "tts-1",
                                ink = ink,
                                muted = muted,
                                hair = hair,
                            )
                            SheetField(
                                label = "声音",
                                value = openAiVoice,
                                onValueChange = { openAiVoice = it },
                                placeholder = "alloy",
                                ink = ink,
                                muted = muted,
                                hair = hair,
                            )
                        }
                        SheetField(
                            label = "API 密钥",
                            value = key,
                            onValueChange = {
                                key = it
                                if (it.isNotBlank()) keyCleared = false
                            },
                            placeholder = if (hasKey) "已保存，留空沿用" else "粘贴密钥",
                            ink = ink,
                            muted = muted,
                            hair = hair,
                            password = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .background(
                                        when {
                                            key.isNotBlank() -> wl.inkMuted
                                            hasKey -> wl.action
                                            else -> wl.coral
                                        },
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    key.isNotBlank() -> "将保存新密钥"
                                    hasKey -> "密钥已保存（不会显示）"
                                    else -> "未配置密钥"
                                },
                                fontSize = NoteSize,
                                color = if (hasKey || key.isNotBlank()) muted else wl.coral,
                            )
                            Spacer(Modifier.weight(1f))
                            if (hasKey && key.isBlank()) {
                                Text(
                                    text = "清除",
                                    fontFamily = SerifFamily,
                                    fontSize = 10.sp,
                                    color = wl.coral,
                                    modifier = Modifier
                                        .wlPress {
                                            key = ""
                                            busy = false
                                            onClearKey()
                                            keyCleared = true
                                            message = "已清除密钥"
                                            failed = false
                                        }
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ToggleCell(
                            label = "合成失败时使用真人录音",
                            checked = fallback,
                            labelColor = ink,
                            ink = ink,
                            muted = muted,
                            hair = hair,
                            paper = paper,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            fallback = it
                        }
                        Text(
                            text = "关闭后只用 AI 声音；失败会提示并重试一次。",
                            fontSize = NoteSize,
                            lineHeight = 14.sp,
                            color = muted.copy(alpha = 0.9f),
                        )
                    }
                }

                SectionHeader("音效", Glyph.Speaker, Modifier.padding(top = 18.dp))
                SoundSection(ink, muted, hair, paper)

                Spacer(Modifier.height(18.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to paper.copy(alpha = 0f),
                            1f to paper,
                        ),
                    )
                    .padding(horizontal = 22.dp)
                    .padding(top = 10.dp, bottom = 12.dp),
            ) {
                Column {
                    message?.let {
                        Text(
                            text = it,
                            fontSize = NoteSize,
                            lineHeight = 14.sp,
                            color = if (failed) wl.coral else muted,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .wlPress(enabled = !busy, scale = 0.97f) { runPreview() }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlyphIcon(Glyph.Play, ink.copy(alpha = 0.8f), 12.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (busy) "合成中…" else "试听",
                                fontFamily = SerifFamily,
                                fontSize = ActionSize,
                                color = if (busy) muted else ink,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = ink,
                            modifier = Modifier
                                .alpha(if (busy || saved) 0.5f else 1f)
                                .wlPress(enabled = !busy && !saved, scale = 0.97f, dim = false) { runSave() },
                        ) {
                            Text(
                                text = if (saved) "已保存" else "保存",
                                fontFamily = SerifFamily,
                                fontSize = ActionSize,
                                color = paper,
                                modifier = Modifier.padding(horizontal = 26.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val wl = WordloomTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(glyph, wl.ink.copy(alpha = 0.5f), 13.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = SectionSize,
            letterSpacing = 1.2.sp,
            color = wl.inkMuted,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun EngineSwitch(
    engine: TtsProtocol,
    ink: Color,
    muted: Color,
    onChange: (TtsProtocol) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("引擎", fontSize = NoteSize, color = muted, letterSpacing = 0.4.sp)
        Spacer(Modifier.weight(1f))
        EngineOption("MiMo", engine == TtsProtocol.MIMO, ink, muted) {
            onChange(TtsProtocol.MIMO)
        }
        Spacer(Modifier.width(12.dp))
        EngineOption("OpenAI 兼容", engine == TtsProtocol.OPENAI, ink, muted) {
            onChange(TtsProtocol.OPENAI)
        }
    }
}

@Composable
private fun EngineOption(
    label: String,
    selected: Boolean,
    ink: Color,
    muted: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        fontSize = 11.sp,
        color = if (selected) ink else muted,
        textDecoration = if (selected) TextDecoration.Underline else null,
        modifier = Modifier
            .wlPress(scale = 0.96f) { onClick() }
            .padding(horizontal = 2.dp, vertical = 6.dp),
    )
}

@Composable
private fun TemplateSwitch(
    template: TtsTemplate,
    wash: Color,
    card: Color,
    ink: Color,
    muted: Color,
    onChange: (TtsTemplate) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(wash)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        TtsTemplate.entries.forEach { option ->
            val selected = option == template
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) card else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { if (!selected) onChange(option) },
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = when (option) {
                        TtsTemplate.Preset -> "预置音色"
                        TtsTemplate.Design -> "设计音色"
                    },
                    fontSize = 10.5.sp,
                    color = if (selected) ink else muted,
                )
            }
        }
    }
}

@Composable
private fun VoiceCard(
    name: String,
    note: String,
    selected: Boolean,
    ink: Color,
    muted: Color,
    card: Color,
    hair: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val lift = animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = tween(180),
        label = "voiceLift",
    )
    Column(
        modifier = modifier
            .graphicsLayer { translationY = lift.value.dp.toPx() }
            .shadow(if (selected) 6.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(if (selected) card else Color.Transparent)
            .border(1.dp, if (selected) ink else hair, shape)
            .heightIn(min = 64.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Text(
            text = name,
            fontFamily = SerifFamily,
            fontSize = CardNameSize,
            color = if (selected) ink else ink.copy(alpha = 0.8f),
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = note,
            fontSize = CardNoteSize,
            lineHeight = 10.sp,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(Modifier.weight(1f))
        VoiceWave(
            heights = VoiceWaves[name] ?: VoiceWaves.getValue(TtsDefaults.VOICE),
            color = if (selected) ink else muted,
            alpha = if (selected) 1f else 0.55f,
        )
    }
}

@Composable
private fun VoiceWave(heights: List<Float>, color: Color, alpha: Float) {
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val stroke = 1.dp.toPx()
        val maxHalf = heights.maxOrNull() ?: 1f
        val usable = size.width - stroke
        val step = if (heights.size > 1) usable / (heights.size - 1) else 0f
        val halfSpace = size.height / 2f - stroke / 2f
        val edge = 0.14f
        heights.forEachIndexed { index, value ->
            val t = if (heights.size > 1) index.toFloat() / (heights.size - 1) else 0.5f
            val fade = when {
                t < edge -> t / edge
                t > 1f - edge -> (1f - t) / edge
                else -> 1f
            }
            val x = stroke / 2f + step * index
            val half = (value / maxHalf) * halfSpace
            drawLine(
                color = color.copy(alpha = alpha * fade),
                start = Offset(x, size.height / 2f - half),
                end = Offset(x, size.height / 2f + half),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun DesignField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    ink: Color,
    muted: Color,
    card: Color,
    hair: Color,
    shape: RoundedCornerShape,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontFamily = SansFamily,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = ink,
        ),
        cursorBrush = SolidColor(ink),
        minLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(card)
            .border(1.dp, hair, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 12.sp, lineHeight = 18.sp, color = muted)
                }
                inner()
            }
        },
    )
}

@Composable
private fun PreviewField(
    value: String,
    onValueChange: (String) -> Unit,
    ink: Color,
    muted: Color,
    hair: Color,
) {
    Column {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = SerifFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ink,
            ),
            cursorBrush = SolidColor(ink),
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("输入单词或句子", fontSize = 13.sp, color = muted.copy(alpha = 0.7f))
                    }
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
    }
}

@Composable
private fun PreviewWord(word: String, ink: Color, rule: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .wlPress(scale = 0.95f, dim = false) { onClick() }
            .padding(top = 4.dp, bottom = 2.dp)
            // The word row scrolls horizontally, so a child cannot fillMaxWidth
            // (the max width is infinite). Draw the dotted rule against the
            // column's own measured width instead.
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = rule.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx()),
                    ),
                )
            },
    ) {
        Text(word, fontFamily = SerifFamily, fontSize = 11.5.sp, color = ink)
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun SheetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    ink: Color,
    muted: Color,
    hair: Color,
    password: Boolean = false,
) = UnderlineField(
    label = label,
    value = value,
    onValueChange = onValueChange,
    placeholder = placeholder,
    labelSize = NoteSize,
    valueSize = 12.sp,
    labelColor = muted,
    textColor = ink,
    lineColor = hair,
    lineHeight = 18.sp,
    password = password,
)

@Composable
private fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    labelSize: TextUnit,
    valueSize: TextUnit,
    labelColor: Color,
    textColor: Color,
    lineColor: Color,
    lineHeight: TextUnit = valueSize * 1.5f,
    password: Boolean = false,
    minLines: Int = 1,
) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = labelSize, color = labelColor, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = TextStyle(
                fontFamily = SansFamily,
                fontSize = valueSize,
                lineHeight = lineHeight,
                color = textColor,
            ),
            cursorBrush = SolidColor(textColor),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = valueSize,
                            color = textColor.copy(alpha = 0.7f),
                        )
                    }
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(lineColor))
    }
}

@Composable
private fun ToggleCell(
    label: String,
    checked: Boolean,
    labelColor: Color,
    ink: Color,
    muted: Color,
    hair: Color,
    paper: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 34.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
                interactionSource = interaction,
                indication = null,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = BodySize,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(Modifier.weight(1f))
        InkSwitch(checked, ink, hair, muted, paper)
    }
}

@Composable
private fun InkSwitch(on: Boolean, ink: Color, hair: Color, muted: Color, paper: Color) {
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(15.dp)
            .clip(CircleShape)
            .background(if (on) ink else Color.Transparent)
            .border(1.dp, if (on) ink else hair, CircleShape),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(9.dp)
                .background(if (on) paper else muted, CircleShape),
        )
    }
}

@Composable
private fun SoundSection(
    ink: Color,
    muted: Color,
    hair: Color,
    paper: Color,
) {
    val context = LocalContext.current
    val sound = remember(context) { SoundService.shared(context) }
    val settings by sound.settings.collectAsStateWithLifecycle()
    val master = settings.enabled

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SoundToggleCell("启用音效", master, ink, muted, hair, paper, Modifier.weight(1f)) {
                sound.setEnabled(it)
            }
            SoundToggleCell(
                "评分音",
                master && settings.ratings,
                ink,
                muted,
                hair,
                paper,
                Modifier.weight(1f),
                enabled = master,
            ) {
                sound.setRatings(it)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SoundToggleCell(
                "拼写音",
                master && settings.spelling,
                ink,
                muted,
                hair,
                paper,
                Modifier.weight(1f),
                enabled = master,
            ) {
                sound.setSpelling(it)
            }
            SoundToggleCell(
                "练习答对音",
                master && settings.quiz,
                ink,
                muted,
                hair,
                paper,
                Modifier.weight(1f),
                enabled = master,
            ) {
                sound.setQuiz(it)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SoundToggleCell(
                "书架过卡声",
                master && settings.pageTurn,
                ink,
                muted,
                hair,
                paper,
                Modifier.weight(1f),
                enabled = master,
            ) {
                sound.setPageTurn(it)
            }
            Spacer(Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("音量", fontSize = NoteSize, letterSpacing = 1.2.sp, color = muted)
            Spacer(Modifier.width(10.dp))
            InkSlider(
                value = settings.volume,
                onChange = sound::previewVolume,
                onFinished = sound::commitVolume,
                ink = ink,
                hair = hair,
                paper = paper,
                enabled = master,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(settings.volume * 100).roundToInt()}%",
                fontSize = NoteSize,
                color = muted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(30.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("试听", fontSize = NoteSize, letterSpacing = 1.2.sp, color = muted)
            Spacer(Modifier.width(12.dp))
            Audition("评分", ink, enabled = master) { sound.preview(Sound.Easy) }
            Spacer(Modifier.width(10.dp))
            Audition("拼写", ink, enabled = master) { sound.preview(Sound.SpellRight) }
            Spacer(Modifier.width(10.dp))
            Audition("练习", ink, enabled = master) { sound.preview(Sound.QuizCorrect) }
            Spacer(Modifier.width(10.dp))
            Audition("过卡", ink, enabled = master) { sound.preview(Sound.PageTurn) }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .wlPress { sound.reset() }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Reset, muted, 11.dp)
                Spacer(Modifier.width(4.dp))
                Text("恢复默认", fontFamily = SerifFamily, fontSize = 10.sp, color = muted)
            }
        }
    }
}

@Composable
private fun SoundToggleCell(
    label: String,
    checked: Boolean,
    ink: Color,
    muted: Color,
    hair: Color,
    paper: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) = ToggleCell(
    label = label,
    checked = checked,
    labelColor = muted,
    ink = ink,
    muted = muted,
    hair = hair,
    paper = paper,
    modifier = modifier,
    enabled = enabled,
    onChange = onChange,
)

@Composable
private fun Audition(label: String, ink: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = SerifFamily,
        fontSize = 10.5.sp,
        color = if (enabled) ink else ink.copy(alpha = 0.4f),
        modifier = Modifier
            .wlPress(enabled = enabled, scale = 0.94f) { onClick() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
    )
}

@Composable
private fun InkSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    ink: Color,
    hair: Color,
    paper: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .progressSemantics(value, 0f..1f)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    if (!enabled) {
                        false
                    } else {
                        onChange(target)
                        onFinished()
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                detectTapGestures { offset ->
                    if (enabled) {
                        onChange((offset.x / size.width).coerceIn(0f, 1f))
                        onFinished()
                    }
                }
            }
            .pointerInput(enabled) {
                detectHorizontalDragGestures(
                    onDragEnd = onFinished,
                    onDragCancel = onFinished,
                ) { change, _ ->
                    if (enabled) {
                        change.consume()
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f
            drawLine(
                color = hair,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ink,
                start = Offset(0f, y),
                end = Offset(size.width * value, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val x = (size.width * value).coerceIn(5.5.dp.toPx(), size.width - 5.5.dp.toPx())
            drawCircle(paper, radius = 8.5.dp.toPx(), center = Offset(x, y))
            drawCircle(ink, radius = 5.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = 1.3.dp.toPx()
        val s = this.size.minDimension
        when (glyph) {
            Glyph.Wave -> {
                val xs = listOf(0.16f, 0.34f, 0.5f, 0.66f, 0.84f)
                val hs = listOf(0.28f, 0.62f, 0.9f, 0.62f, 0.28f)
                xs.forEachIndexed { i, x ->
                    val h = s * hs[i]
                    drawLine(
                        color = color,
                        start = Offset(s * x, s / 2f - h / 2f),
                        end = Offset(s * x, s / 2f + h / 2f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Glyph.Play -> {
                val path = Path().apply {
                    moveTo(s * 0.28f, s * 0.22f)
                    lineTo(s * 0.28f, s * 0.78f)
                    lineTo(s * 0.78f, s * 0.5f)
                    close()
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round),
                )
            }
            Glyph.Key -> {
                drawCircle(color, radius = s * 0.13f, center = Offset(s * 0.34f, s * 0.66f), style = Stroke(stroke))
                drawLine(color, Offset(s * 0.44f, s * 0.56f), Offset(s * 0.82f, s * 0.18f), stroke, StrokeCap.Round)
                drawLine(color, Offset(s * 0.67f, s * 0.33f), Offset(s * 0.77f, s * 0.43f), stroke, StrokeCap.Round)
                drawLine(color, Offset(s * 0.75f, s * 0.25f), Offset(s * 0.84f, s * 0.33f), stroke, StrokeCap.Round)
            }
            Glyph.Speaker -> {
                val path = Path().apply {
                    moveTo(s * 0.16f, s * 0.4f)
                    lineTo(s * 0.34f, s * 0.4f)
                    lineTo(s * 0.5f, s * 0.24f)
                    lineTo(s * 0.5f, s * 0.76f)
                    lineTo(s * 0.34f, s * 0.6f)
                    lineTo(s * 0.16f, s * 0.6f)
                    close()
                }
                drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
                drawArc(
                    color = color,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(s * 0.46f, s * 0.3f),
                    size = Size(s * 0.34f, s * 0.4f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Glyph.Reset -> {
                drawArc(
                    color = color,
                    startAngle = -60f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = Offset(s * 0.22f, s * 0.22f),
                    size = Size(s * 0.56f, s * 0.56f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color = color,
                    start = Offset(s * 0.68f, s * 0.14f),
                    end = Offset(s * 0.72f, s * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(s * 0.68f, s * 0.14f),
                    end = Offset(s * 0.55f, s * 0.2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * The onboarding field, unchanged: it is shared by [dev.wordloom.ui.onboarding.TtsSetupPage]
 * and keeps its own 10/13sp scale so that page is not restyled from here.
 */
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
    UnderlineField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        labelSize = 10.sp,
        valueSize = 13.sp,
        labelColor = wl.inkMuted,
        textColor = wl.ink,
        lineColor = wl.outline,
        lineHeight = 19.sp,
        password = password,
        minLines = minLines,
    )
}
