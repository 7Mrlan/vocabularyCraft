# AI TTS：决策、现状与目标架构

> 状态时间：2026-11-12。分支：`animation`。
> 本文是 TTS 的唯一事实来源；压缩对话后按本文继续。

## 1. 已定决策

- **单词和例句都走 AI TTS**；旧 Vocabulary.com / Wiktionary 真人录音与 Youdao TTS 只作为兜底。
- **两套模板**（声音页顶部「预置音色 / 设计音色」切换）：预置英文音色（默认 `mimo-v2.5-tts` + Chloe，另有 Mia / Milo / Dean 四张音色卡）与设计音色（`mimo-v2.5-tts-voicedesign` + 原有中文描述）。
- **MiMo 单词补句号**：单个词在发送前加 `.`（`record` → `record.`），给预置/设计两套语音一个句子边界；句子与短语不动，OpenAI 兼容接口按原文发送。
- **失败回退是配置项**：默认开（合成失败用真人录音）；关闭后只用 AI 声音，失败提示并重试一次；未配置 key 时行为与以前一致。
- **设计描述始终保留**：预置模板保存时也把描述存进配置，但预置请求不发送它（预置音色不接受描述）；切回设计模板时原样恢复。
- **design 音色保留为第二套模板**：`mimo-v2.5-tts-voicedesign` + 用户原来的中文 instructions
  （“温润知性的成熟女声，中低音，音色干净，吐字清晰，语速从容，句末自然下落，不过分甜。”），
  但不再是默认；默认是预置英文音色。
- **预取 + 磁盘缓存**优先于流式；合成失败重试一次，然后按回退配置处理。
- **最终 release 不含 key**；可配置 TTS 必须在应用内完成（加密存储），不能把 key 编进 APK。
- 试听只在「声音」页与首启引导里，没有独立的测试抽屉；引导页的 `testTts` 仅用于配置时试听。

## 2. MiMo 官方模型与音色

来源：`https://mimo.mi.com/docs/zh-CN/usage-guide/speech-synthesis-v2.5`、
`https://mimo.xiaomi.com/mimo-v2-5-tts/`。

### 模型

| Model ID | 用途 | 说明 |
|---|---|---|
| `mimo-v2.5-tts` | 预置音色 | 官方精品音色，支持低延迟流式（`pcm16`） |
| `mimo-v2.5-tts-voicedesign` | 文本描述生成音色 | 用 `instructions` 生成新音色；不是为稳定英文朗读优化 |
| `mimo-v2.5-tts-voiceclone` | 音频样本复刻 | 本 App 不使用 |

### 预置音色

| 音色名 | Voice ID | 语言 | 性别 |
|---|---|---|---|
| MiMo-默认 | `mimo_default` | 因集群而异（中国集群默认冰糖） | — |
| 冰糖 | `冰糖` | 中文 | 女 |
| 茉莉 | `茉莉` | 中文 | 女 |
| 苏打 | `苏打` | 中文 | 男 |
| 白桦 | `白桦` | 中文 | 男 |
| Mia | `Mia` | 英文 | 女 |
| Chloe | `Chloe` | 英文 | 女 |
| Milo | `Milo` | 英文 | 男 |
| Dean | `Dean` | 英文 | 男 |

官方英文示例使用 `Chloe`。

### 请求格式

- MIMO：`POST /v1/chat/completions`，header `api-key`。
  - `messages`: user = instructions（可选；voicedesign 必填），assistant = 待合成文本。
  - `audio`: `{ "format": "wav" | "pcm16", "voice": "<id>", "optimize_text_preview": false }`
  - `optimize_text_preview` **只支持 voicedesign**；预置音色传它会 400。
  - 非流式响应是 JSON：`choices[0].message.audio.data` 是 base64 WAV。
- OpenAI 兼容：`POST /v1/audio/speech`，header `Authorization: Bearer`，返回 MP3。
- 流式：`stream: true` + `format: "pcm16"`；**只有 `mimo-v2.5-tts` 能真正提前返回音频**；
  design 的 SSE 首个事件只是元数据，音频仍在约 1.8–3 s 后才到。

## 3. 延迟实测（桌面网络，2 次中位数）

| 模型/音色 | 非流式整段 | 流式首块音频 |
|---|---|---|
| design | 单词 ~1.87 s / 句子 ~1.76 s | ~1.95–3.3 s（无实际提前） |
| `mimo-v2.5-tts + Chloe` | 单词 ~1.92 s / 句子 ~2.54 s | **~0.73 s** |

结论：

- **非流式延迟差距很小**，网络波动大于模型差异；用户喜欢的 design 音色可以保留。
- 真正拉开差距的是流式首包。design 无法有效流式；要首次 <1 s 只能换 `mimo-v2.5-tts` 的英文预置音色（Mia/Milo/Dean）+ `pcm16`。
- 当前默认：**预置英文音色 + 预取/缓存**；design 仍可在声音页切回。首次冷合成约 1.9 s，之后命中缓存接近 0。

## 4. 当前实现（4.1，工作区）

- `data/tts/TtsConfig.kt`：endpoint / protocol / model / voice / instructions / key / fallback；
  MIMO/OpenAI 请求体；URL 归一化；`requestText()`（MiMo 单字补句号，OpenAI 原文）；
  `TtsDraft`（两个配置页共用的转换与校验）；加密存储用 kotlinx.serialization。
- `data/tts/TtsClient.kt`：一次请求，无重试；MiMo JSON/base64 音频解析；WAV/MP3 校验；10/30/45 s 超时。
- `data/tts/TtsConfigStore.kt`：Android Keystore AES/GCM + `AtomicFile`，文件 `filesDir/tts.encrypted`；
  key 只在这里落盘，且已在 `backup_rules.xml` / `data_extraction_rules.xml` 中排除出云备份与设备迁移。
- `data/tts/SpeechService.kt`：优先读加密 store，debug/`-PttsLocal` 的 BuildConfig 只做首次引导种子；
  AI 优先、URL 兜底；`cacheDir/tts` 磁盘缓存；按
  `protocol+endpoint+model+voice+有效instructions+text` 的 SHA-256 做 key
  （预置音色不发送描述，所以描述也不进它的 key；voicedesign 与 OpenAI 兼容接口都进）；
  Semaphore 2；in-flight 去重；单个 MediaPlayer。
- `ui/voice/SpeechActions.kt`：`LocalSpeechActions`，学习卡/词条页统一入口。
- `ui/voice/SpeechButton.kt`：AI 优先，旧 URL 兜底；播放脉冲在 `graphicsLayer` 读取，不再每帧重组。
- `ui/voice/VoiceSettingsSheet.kt`：应用内「声音」配置页。顶部「预置音色 / 设计音色」分段；
  预置模式是四张 64dp 音色卡（Mia / Chloe / Milo / Dean，默认 Chloe，选中的卡纸白底 + 墨边 + 抬 2dp），
  设计模式是音色描述框。试听文本可编辑，带 record/read/lead/close/use/live 六个快选词（点词即读）；
  「连接」默认折叠为一行，展开才有接口地址、API 密钥（留空沿用 / 清除）与回退开关；
  音效在同一页（五个开关、音量、分组试听、恢复默认）；底部固定试听 / 保存。
  全页字号上限 14.5sp，常规高度一屏内，无滚动；展开连接或小屏才滚动。
  非模板的自定义 MiMo 模型在未改动音色与模板时原样保留，并在音色卡上方显示当前配置；
  OpenAI 兼容配置通过「连接」展开里的引擎切换直接编辑，OpenAI 时隐藏 MiMo 模板/音色卡。
- 入口：首页品牌行「收纳 → 声音」；首次引导第 2 屏也可配置并试听；
  学习页/词条页仍保留喇叭与自动朗读。
- `StudyScreen`：单词/例句自动朗读走 AI；`StudyCard` 预取当前词 + 前 5 例句；队列前 3 词预取。
- `BookDetail`：打开书内页即预取前 3 个词。
- `WordScreen`：词条页喇叭走 AI；AI 启用时隐藏“全部”真人连读。
- 配置来源：release 的 BuildConfig 六项全空；用户 key 只从应用内写入加密 store。
- 4.1 正式包：`Wordloom-4.1-arm64.apk`（同 `app/build/outputs/apk/release/app-release.apk`；
  arm64，versionCode 14 / 4.1），已确认 APK 内不含用户 key。含 key 的本地测试包
  只用于验证，不再交付。

## 5. 目标架构与剩余

### 5.1 加密配置（✅ 已实现）

`data/tts/TtsConfigStore.kt`：

- Android Keystore AES/GCM + `AtomicFile`；文件 `filesDir/tts.encrypted`。
- 字段：endpoint、protocol、model、voice、instructions、key。
- key 不进备份、不进日志、不进 BuildConfig、不进 release APK。
- 连接/试听错误要像 ebook 的 `safeAiSpeechTestMessage` 一样打码（Bearer、sk-、用户 key）；
  当前错误信息只来自 `TtsClient` 的状态码/简短原因，不回显请求头或 key。

### 5.2 应用内配置 UI（✅ 朗读页与音效页）

- 「声音」底部抽屉：顶部「预置音色 / 设计音色」分段；预置模式为四张音色卡
  （Mia 明亮 / Chloe 温暖 / Milo 沉稳 / Dean 低沉，默认 Chloe），设计模式为描述框。
- 「连接」折叠行显示 `MiMo · host`（OpenAI 时显示 `OpenAI 兼容 · host`），展开后是
  引擎切换（MiMo / OpenAI 兼容）、接口地址、OpenAI 的模型/声音、API 密钥
  （已保存 / 留空沿用 / 清除）与「合成失败时使用真人录音」；密钥永不回显。
- 试听文本可编辑，六个同形异读词一点即替换并朗读；底部固定试听 / 保存，错误就地显示在按钮上方。
- 音效同页：总音量（墨色滑杆）、评分/拼写/练习/书架分组开关、分组试听、恢复默认。
- 入口：首页品牌行「收纳 → 声音」；首次引导第 2 屏配置朗读。
- 视觉：纸面（浅色 #F7F4ED / 深色 surface）、15sp 以内的衬线标题与墨色线、圆形墨色保存按钮；
  分区用 13dp 单线字形（波形 / 播放 / 钥匙 / 喇叭 / 重置），没有 Material 灰色设置面板。
- 引擎切换：连接展开里可选 MiMo / OpenAI 兼容；OpenAI 时显示模型与声音字段，
  隐藏 MiMo 模板/音色卡，已有描述原样保留。
- 自定义配置保护：非模板的自定义 MiMo 模型在未改音色/模板时不会被静默覆盖；
  只有点了音色卡或切换模板才写回模板模型。

### 5.3 播放与预取

- ✅ 配置变化 → 缓存 key 变化（含 `tts-v2` 格式版本与补句号后的文本），旧缓存自然失效。
- ✅ 预取：书内页前 3 词、队列前 3 词、当前词 + 前 5 例句；并发 2；in-flight 去重。
- ✅ 失败状态：`Loading / Playing / Failed`；重试一次后，按「合成失败时使用真人录音」
  决定回退旧 URL 还是提示失败（提示里带服务端原因，如 401 / 超时）。
- ✅ 缓存：128 MiB LRU；命中时刷新最近使用时间，正在写入/试听/测试的临时文件不参与清理。
- ✅ 音频焦点：`AudioFocus` 请求 transient focus；来电或其他播放器接管时停止当前 clip，
  播放结束/取消立即 abandon。
- ⏸ 流式：design 音色没有实际提前音频，当前不启用；只有改用 `mimo-v2.5-tts` 英文预置音色时
  才值得做 `pcm16` SSE + AudioTrack/Media3。当前选择预取/缓存。

### 5.4 英文质量

- 预置英文音色为默认；design 音色是用户偏好，英文是否口音由用户试听决定。
- 若需要稳定英文母语感：用 `mimo-v2.5-tts + Chloe/Mia`，或保留 design 音色但加
  “native English pronunciation” 的英文补充指令（需实测音色是否漂移）。
- 单词真人录音仍可作为兜底；但用户已决定 AI 优先。

## 6. 验收

- release APK 的 BuildConfig/字符串中**没有 key**；`local.properties` 不进库。
- 应用内保存配置后重启仍生效；换 voice/model 后缓存不复用旧音频。
- 试听/连接失败有可读错误；离线时已缓存可播，未缓存显示无发音，不静默。
- 冷启动首词在书内页停留 ≥2 s 后应命中预取；未命中时首字 ≤2 s。
- 记录 p50/p95 首字延迟；书架/手势/音效另见 `docs/REVIEW-AND-ROADMAP.md`。
