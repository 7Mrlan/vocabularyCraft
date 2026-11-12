# AI TTS：决策、现状与目标架构

> 状态时间：2026-11-12。分支：`animation`。
> 本文是 TTS 的唯一事实来源；压缩对话后按本文继续。

## 1. 已定决策

- **单词和例句都走 AI TTS**；旧 Vocabulary.com / Wiktionary 真人录音与 Youdao TTS 只作为失败/未配置时的兜底。
- **保留 design 音色**：`mimo-v2.5-tts-voicedesign` + 用户原来的中文 instructions
  （“温润知性的成熟女声，中低音，音色干净，吐字清晰，语速从容，句末自然下落，不过分甜。”）。
- **预取 + 磁盘缓存**优先于流式；热路径一次请求，失败快速回退，不做 4 次重试。
- **最终 release 不含 key**；可配置 TTS 必须在应用内完成（加密存储），不能把 key 编进 APK。
- 试听/测试抽屉已从产品 UI 移除；不再保留 `testTts`、`VoiceSheet`、旧测试 APK。

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
- 当前选择：**design + 预取/缓存**；首次冷合成约 1.8 s，之后命中缓存接近 0。

## 4. 当前实现（4.1，工作区）

- `data/tts/TtsConfig.kt`：endpoint / protocol / model / voice / instructions / key；MIMO/OpenAI 请求体；URL 归一化。
- `data/tts/TtsClient.kt`：一次请求，无重试；MiMo JSON/base64 音频解析；WAV/MP3 校验；10/30/45 s 超时。
- `data/tts/TtsConfigStore.kt`：Android Keystore AES/GCM + `AtomicFile`，文件 `filesDir/tts.encrypted`；
  key 只在这里落盘，且已在 `backup_rules.xml` / `data_extraction_rules.xml` 中排除出云备份与设备迁移。
- `data/tts/SpeechService.kt`：优先读加密 store，debug/`-PttsLocal` 的 BuildConfig 只做首次引导种子；
  AI 优先、URL 兜底；`cacheDir/tts` 磁盘缓存；按
  `protocol+endpoint+model+voice+instructions+text` 的 SHA-256 做 key；Semaphore 2；in-flight 去重；单个 MediaPlayer。
- `ui/voice/SpeechActions.kt`：`LocalSpeechActions`，学习卡/词条页统一入口。
- `ui/voice/SpeechButton.kt`：AI 优先，旧 URL 兜底；播放脉冲在 `graphicsLayer` 读取，不再每帧重组。
- `ui/voice/VoiceSettingsSheet.kt`：应用内「声音」配置页；纸面、衬线、墨色按钮；MiMo voicedesign
  推荐默认；支持试听、保存；密钥输入用密码遮罩且不回显。
- 入口：首页品牌行「收纳 → 声音」；首次引导第 2 屏也可配置并试听；
  学习页/词条页仍保留喇叭与自动朗读。
- `StudyScreen`：单词/例句自动朗读走 AI；`StudyCard` 预取当前词 + 前 5 例句；队列前 3 词预取。
- `BookDetail`：打开书内页即预取前 3 个词。
- `WordScreen`：词条页喇叭走 AI；AI 启用时隐藏“全部”真人连读。
- 配置来源：release 的 BuildConfig 六项全空；用户 key 只从应用内写入加密 store。
- 4.1 正式包：`Wordloom-4.1-arm64.apk`（同 `app/build/outputs/apk/release/app-release.apk`；
  38.04 MiB，arm64，versionCode 14 / 4.1），已确认 APK 内不含用户 key。含 key 的本地测试包
  只用于验证，不再交付。

## 5. 目标架构与剩余

### 5.1 加密配置（✅ 已实现）

`data/tts/TtsConfigStore.kt`：

- Android Keystore AES/GCM + `AtomicFile`；文件 `filesDir/tts.encrypted`。
- 字段：endpoint、protocol、model、voice、instructions、key。
- key 不进备份、不进日志、不进 BuildConfig、不进 release APK。
- 连接/试听错误要像 ebook 的 `safeAiSpeechTestMessage` 一样打码（Bearer、sk-、用户 key）；
  当前错误信息只来自 `TtsClient` 的状态码/简短原因，不回显请求头或 key。

### 5.2 应用内配置 UI（✅ 朗读页；⏳ 音效页）

- 已实现「声音」底部抽屉的朗读页：引擎（MiMo / OpenAI 兼容）、endpoint、model、voice、
  instructions、key、试听、保存；默认值按 MiMo voicedesign 推荐音色。
- 入口：首页品牌行「收纳 → 声音」；首次引导后续直接复用此页。
- 音效页仍待做：总音量、评分/拼写/练习/书架开关、安静模式、恢复默认；与朗读页共用同一个 sheet。
- 视觉：纸面、衬线标题、细墨线、圆形墨色按钮；没有 Material 灰色设置面板。

### 5.3 播放与预取

- ✅ 配置变化 → 缓存 key 变化，旧缓存自然失效。
- ✅ 预取：书内页前 3 词、队列前 3 词、当前词 + 前 5 例句；并发 2；in-flight 去重。
- ✅ 失败状态：`Loading / Playing / Failed`；失败一次后回退到旧 URL，不静默。
- ⏳ 缓存：磁盘 LRU（上限待定，建议 128 MB）；命中 <50 ms。当前只增不减，需要清理策略。
- ⏳ 音频焦点：requestAudioFocus；来电/其他 App 播放暂停；蓝牙切换处理。
- ⏳ 可选流式：若换 `mimo-v2.5-tts` 英文 voice，使用 `pcm16` SSE + AudioTrack/Media3；
  design 不做流式。

### 5.4 英文质量

- design 音色是用户偏好；英文是否口音由用户试听决定。
- 若需要稳定英文母语感：用 `mimo-v2.5-tts + Chloe/Mia`，或保留 design 音色但加
  “native English pronunciation” 的英文补充指令（需实测音色是否漂移）。
- 单词真人录音仍可作为兜底；但用户已决定 AI 优先。

## 6. 验收

- release APK 的 BuildConfig/字符串中**没有 key**；`local.properties` 不进库。
- 应用内保存配置后重启仍生效；换 voice/model 后缓存不复用旧音频。
- 试听/连接失败有可读错误；离线时已缓存可播，未缓存显示无发音，不静默。
- 冷启动首词在书内页停留 ≥2 s 后应命中预取；未命中时首字 ≤2 s。
- 记录 p50/p95 首字延迟；书架/手势/音效另见 `docs/REVIEW-AND-ROADMAP.md`。
