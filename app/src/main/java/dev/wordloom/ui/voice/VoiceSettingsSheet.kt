package dev.wordloom.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.data.audio.SoundService
import dev.wordloom.data.tts.TtsConfig
import dev.wordloom.data.tts.TtsDefaults
import dev.wordloom.data.tts.TtsProtocol
import dev.wordloom.data.tts.TtsStatus
import dev.wordloom.ui.components.WlSheet
import dev.wordloom.ui.theme.SansFamily
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress
import kotlin.math.roundToInt

private const val DEFAULT_ENDPOINT = TtsDefaults.ENDPOINT
private const val DEFAULT_MODEL = TtsDefaults.MODEL
private const val DEFAULT_INSTRUCTIONS = TtsDefaults.INSTRUCTIONS
private const val PREVIEW_SENTENCE = TtsDefaults.PREVIEW

/**
 * A quiet listening-room sheet: paper, serif labels, one ink action. This is a
 * real settings surface (not a test screen); the key is written only through
 * the encrypted TtsConfigStore.
 */
@Composable
fun VoiceSettingsSheet(
    status: TtsStatus,
    onSave: (TtsConfig, (Result<Unit>) -> Unit) -> Unit,
    onPreview: (TtsConfig, String, (Result<Unit>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val wl = WordloomTheme.colors
    val ink = wl.ink
    val muted = wl.inkMuted
    var protocol by remember { mutableStateOf(runCatching { TtsProtocol.valueOf(status.protocol) }.getOrDefault(TtsProtocol.MIMO)) }
    var endpoint by remember { mutableStateOf(status.endpoint.ifBlank { DEFAULT_ENDPOINT }) }
    var model by remember { mutableStateOf(status.model.ifBlank { DEFAULT_MODEL }) }
    var voice by remember { mutableStateOf(if (status.voice == "voicedesign") "" else status.voice) }
    var instructions by remember { mutableStateOf(status.instructions.ifBlank { DEFAULT_INSTRUCTIONS }) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun draft(): TtsConfig = TtsConfig(
        endpoint = endpoint.trim(),
        protocol = protocol,
        model = model.trim(),
        voice = voice.trim(),
        instructions = instructions.trim(),
        key = key.trim(),
    )

    fun validate(config: TtsConfig): String? {
        if (config.endpoint.isBlank()) return "请填写接口地址"
        if (config.model.isBlank()) return "请填写模型名称"
        if (config.key.isBlank() && !status.hasKey) return "请填写 API 密钥"
        if (config.protocol == TtsProtocol.OPENAI && config.voice.isBlank()) return "请填写声音名称"
        if (config.voiceDesign && config.instructions.isBlank()) return "请填写音色描述"
        return null
    }

    WlSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
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
                        .background(if (status.enabled) wl.action else wl.coral, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (status.enabled) "AI 朗读已启用" else "AI 朗读未配置",
                    fontFamily = SerifFamily,
                    fontSize = 14.sp,
                    color = ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (status.hasKey) "已保存密钥" else "需要密钥",
                    fontSize = 10.5.sp,
                    color = if (status.hasKey) muted else wl.coral,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("引擎", fontSize = 11.sp, color = muted, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoicePill("MiMo", protocol == TtsProtocol.MIMO) { protocol = TtsProtocol.MIMO }
                VoicePill("OpenAI 兼容", protocol == TtsProtocol.OPENAI) { protocol = TtsProtocol.OPENAI }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "使用推荐配置",
                    fontFamily = SerifFamily,
                    fontSize = 12.sp,
                    color = wl.primary,
                    modifier = Modifier
                        .wlPress {
                            endpoint = DEFAULT_ENDPOINT
                            protocol = TtsProtocol.MIMO
                            model = DEFAULT_MODEL
                            voice = ""
                            instructions = DEFAULT_INSTRUCTIONS
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            VoiceField("接口地址", endpoint, { endpoint = it }, "https://api.xiaomimimo.com/v1")
            VoiceField("模型", model, { model = it }, "mimo-v2.5-tts-voicedesign")
            if (protocol == TtsProtocol.OPENAI || model != DEFAULT_MODEL) {
                VoiceField("声音", voice, { voice = it }, "例如 Chloe / Mia")
            }
            VoiceField(
                label = if (model == DEFAULT_MODEL) "音色描述（voicedesign 必填）" else "朗读风格（选填）",
                value = instructions,
                onValueChange = { instructions = it },
                placeholder = "温润知性的成熟女声…",
                minLines = 3,
            )
            VoiceField(
                label = "API 密钥",
                value = key,
                onValueChange = { key = it },
                placeholder = if (status.hasKey) "已保存，留空沿用" else "粘贴密钥",
                password = true,
            )

            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = if (failed) wl.coral else wl.inkMuted,
                )
            }

            Spacer(Modifier.height(18.dp))
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
                            onPreview(candidate, PREVIEW_SENTENCE) { result ->
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
                        .wlPress(enabled = !busy) {
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
                                    onSuccess = { onDismiss() },
                                    onFailure = {
                                        failed = true
                                        message = it.message ?: "保存失败"
                                    },
                                )
                            }
                        },
                ) {
                    Text(
                        text = "保存",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = wl.surface,
                        modifier = Modifier.padding(vertical = 13.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "密钥用 Android Keystore 加密保存在本机，不进入备份、日志或 APK。" +
                    "AI 朗读会把单词或例句文本发送到你配置的服务。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
                color = muted.copy(alpha = 0.82f),
            )

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
        Text("书架过卡声", fontSize = 13.sp, color = wl.ink, modifier = Modifier.weight(1f))
        SoundToggle(settings.pageTurn) { sound.setPageTurn(it) }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("音量", fontSize = 11.sp, color = wl.inkMuted)
        Spacer(Modifier.width(10.dp))
        Slider(
            value = settings.volume,
            onValueChange = sound::setVolume,
            valueRange = 0f..1f,
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
        color = wl.primary,
        modifier = Modifier
            .wlPress { sound.reset() }
            .padding(vertical = 8.dp),
    )
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
