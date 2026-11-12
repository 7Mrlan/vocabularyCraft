package dev.wordloom.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wordloom.data.tts.TtsConfig
import dev.wordloom.data.tts.TtsDefaults
import dev.wordloom.data.tts.TtsProtocol
import dev.wordloom.data.tts.TtsResult
import dev.wordloom.data.tts.TtsStatus
import dev.wordloom.ui.theme.SerifFamily
import dev.wordloom.ui.theme.WordloomTheme
import dev.wordloom.ui.theme.wlPress
import dev.wordloom.ui.voice.VoiceField
import java.util.Locale

/**
 * The only onboarding page that asks for anything. The reader sees one field
 * (the key); endpoint, model, voice and instructions stay behind "高级设置".
 */
@Composable
internal fun TtsSetupPage(
    status: TtsStatus,
    onSave: (TtsConfig, (Result<Unit>) -> Unit) -> Unit,
    onTest: (TtsConfig, String, (Result<TtsResult>) -> Unit) -> Unit,
    onStopAudio: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val wl = WordloomTheme.colors
    var protocolName by rememberSaveable {
        mutableStateOf(status.protocol.ifBlank { TtsProtocol.MIMO.name })
    }
    val protocol = remember(protocolName) {
        runCatching { TtsProtocol.valueOf(protocolName) }.getOrDefault(TtsProtocol.MIMO)
    }
    var endpoint by rememberSaveable {
        mutableStateOf(status.endpoint.ifBlank { TtsDefaults.ENDPOINT })
    }
    var model by rememberSaveable {
        mutableStateOf(status.model.ifBlank { TtsDefaults.MODEL })
    }
    var voice by rememberSaveable {
        mutableStateOf(if (status.voice == "voicedesign") "" else status.voice)
    }
    var instructions by rememberSaveable {
        mutableStateOf(status.instructions.ifBlank { TtsDefaults.INSTRUCTIONS })
    }
    // Deliberately not rememberSaveable: a secret must not enter saved state.
    var key by remember { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wl.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OnboardingStepHeader(
            step = "02",
            onSkip = {
                onStopAudio()
                onSkip()
            },
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "让单词和例句会说话",
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = wl.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "使用你自己的 AI 语音服务（MiMo / OpenAI 兼容）。",
            style = MaterialTheme.typography.bodyMedium,
            color = wl.inkMuted,
        )

        Spacer(Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = wl.surfaceAlt,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("接口", fontSize = 11.sp, color = wl.inkMuted, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetupPill("MiMo（推荐）", protocol == TtsProtocol.MIMO) {
                        protocolName = TtsProtocol.MIMO.name
                        if (model.isBlank() || model == TtsDefaults.MODEL) {
                            endpoint = TtsDefaults.ENDPOINT
                            model = TtsDefaults.MODEL
                        }
                    }
                    SetupPill("OpenAI 兼容", protocol == TtsProtocol.OPENAI) {
                        protocolName = TtsProtocol.OPENAI.name
                        advanced = true
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("API 密钥", fontSize = 11.sp, color = wl.inkMuted)
                Spacer(Modifier.height(6.dp))
                VoiceField(
                    label = "",
                    value = key,
                    onValueChange = { key = it },
                    placeholder = if (status.hasKey) "已保存，留空沿用" else "粘贴你的 API 密钥",
                    password = true,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (protocol == TtsProtocol.MIMO) {
                        "默认使用 MiMo voicedesign 音色；密钥只保存在本机。"
                    } else {
                        "按 OpenAI /audio/speech 兼容格式请求；密钥只保存在本机。"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                    color = wl.inkMuted,
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (advanced) "收起高级设置" else "高级设置",
                    fontFamily = SerifFamily,
                    fontSize = 12.sp,
                    color = wl.primary,
                    modifier = Modifier
                        .wlPress {
                            advanced = !advanced
                        }
                        .padding(vertical = 6.dp),
                )
                if (advanced) {
                    VoiceField("接口地址", endpoint, { endpoint = it }, TtsDefaults.ENDPOINT)
                    VoiceField("模型", model, { model = it }, TtsDefaults.MODEL)
                    if (protocol == TtsProtocol.OPENAI || model != TtsDefaults.MODEL) {
                        VoiceField("声音", voice, { voice = it }, "例如 Chloe / Mia")
                    }
                    VoiceField(
                        label = if (model == TtsDefaults.MODEL) "音色描述（voicedesign 必填）" else "朗读风格（选填）",
                        value = instructions,
                        onValueChange = { instructions = it },
                        placeholder = "温润知性的成熟女声…",
                        minLines = 3,
                    )
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = if (failed) wl.coral else wl.mintInk,
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupAction(
                text = if (busy) "处理中…" else "测试发音",
                primary = false,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    val candidate = draft()
                    validate(candidate)?.let {
                        failed = true
                        message = it
                        return@SetupAction
                    }
                    busy = true
                    failed = false
                    message = "正在合成试听…"
                    onTest(candidate, TtsDefaults.PREVIEW) { result ->
                        busy = false
                        failed = result.isFailure
                        message = result.fold(
                            onSuccess = {
                                val seconds = String.format(Locale.US, "%.2f", it.elapsedMillis / 1000f)
                                "已播放 · 合成 $seconds s"
                            },
                            onFailure = { it.message ?: "试听失败" },
                        )
                    }
                },
            )
            SetupAction(
                text = "保存并继续",
                primary = true,
                enabled = !busy,
                modifier = Modifier.weight(1.15f),
                onClick = {
                    val candidate = draft()
                    validate(candidate)?.let {
                        failed = true
                        message = it
                        return@SetupAction
                    }
                    busy = true
                    failed = false
                    message = null
                    onSave(candidate) { result ->
                        busy = false
                        result.fold(
                            onSuccess = {
                                onStopAudio()
                                onNext()
                            },
                            onFailure = {
                                failed = true
                                message = it.message ?: "保存失败"
                            },
                        )
                    }
                },
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "稍后配置",
            fontFamily = SerifFamily,
            fontSize = 13.sp,
            color = wl.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .wlPress {
                    onStopAudio()
                    onNext()
                }
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SetupPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val wl = WordloomTheme.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) wl.ink else wl.surface,
        modifier = Modifier.wlPress(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = SerifFamily,
            fontSize = 12.sp,
            color = if (selected) wl.surface else wl.inkMuted,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SetupAction(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val wl = WordloomTheme.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = if (primary) wl.ink else wl.surface,
        modifier = modifier.wlPress(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) wl.surface else wl.ink,
            modifier = Modifier.padding(vertical = 13.dp),
        )
    }
}
