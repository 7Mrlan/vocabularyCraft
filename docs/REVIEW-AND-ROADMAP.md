# 全仓审查、决策与路线图

> 状态时间：2026-11-12。分支：`animation`。
> 本文保留 2026-11 全仓审查的结论、已定决策、当前完成度和后续顺序；
> 压缩对话后以本文 + `docs/AI-TTS.md` + `docs/ONBOARDING.md` 为准。

## 1. 发布与代码状态

- `v4.0.0` 的三个提交已推送到 `main` / `animation`，tag `v4.0.0` 已推；
  **GitHub Release 未创建**（按用户要求暂停；账号计费导致 Actions 不可用）。
- 4.1 的 TTS/播放/预取/配置/引导/书架/手势/音效改动已提交并推送到 `animation`；本地已构建无 key 的
  `Wordloom-4.1-arm64.apk`（同 `app/build/outputs/apk/release/app-release.apk`；versionCode 14 /
  versionName 4.1，arm64，38.01 MiB，SHA-256
  `081a80306cc5465b5ab00b923b242f7fbadfcdb27b3e33ea5808fa54a7c6ffb0`）。
- 该 4.1 包的 BuildConfig 六个 TTS 字段全为空，APK 内不含用户 key；密钥改由应用内配置写入
  Android Keystore 加密文件。之前含 key 的本地测试包只用于验证，**不再作为交付物**。
- 正式 release 必须是空 key；发布前仍需真机走查延迟、帧率、音频焦点、深色模式。
- `v4.0.0` tag 不要移动；后续发 `4.1` / `4.1.1`，由维护者决定何时创建 Release。

## 2. 已完成 / 部分完成 / 未完成

| 项 | 状态 | 说明 |
|---|---|---|
| 4.0 全仓审查修复（数据/备份/学习状态/文档） | ✅ 已提交 | 3 个提交：`5f7c6d6`、`de4db3e`、`679016d` |
| AI TTS 实际播放（学习卡单词/例句、学习卡/词条页喇叭） | ✅ 已提交、未真机验证 | design 音色；旧音源兜底 |
| TTS 预取/磁盘缓存 | ✅ 已提交、未真机验证 | 书内页前 3 词、队列前 3 词、当前词 + 前 5 例句；128 MiB LRU |
| 首词自动朗读 | ✅ 已提交、未真机验证 | AI 不依赖词条 audio；预取提前 |
| **系统内可配置 TTS（加密）** | ✅ 已提交、未真机验证 | `TtsConfigStore`（Keystore AES/GCM + AtomicFile）、首页「声音」配置页、无 key release；见 `docs/AI-TTS.md` |
| 首次下载动态引导（含 AI TTS 配置） | ✅ 已提交、未真机验证 | 4 屏 Pager；首启路由、跳过/完成 flag、TTS 测试与保存；见 `docs/ONBOARDING.md` |
| 书架环形模型重构 | ✅ 未真机验证 | `BookshelfWheel` 纯模型 + 单测；插图同层、层叠锚点冻结到回弹结束 |
| 学习卡手势重构（取消上下切层） | ✅ 未真机验证 | 只识别水平拖动；中文/英文改按钮；单一朗读服务 + 音频焦点 |
| 音效重做 / 声音设置 | ✅ 未真机验证 | 木质/毡质 WAV；一个 `SoundService`；音量、分组开关、书架纸页声默认开（5.0.1 起） |
| 首页品牌行三按钮 + 收纳抽屉 | ✅ 未真机验证 | 词书/词表/收纳把手；纸白抽屉含地图、备份、声音、重新看引导 |
| 4.1 单测 | ✅ 2026-11-12 | `:app:testDebugUnitTest` 通过（含书架环、缓存 LRU） |
| 音频焦点 / 缓存 LRU | ✅ 未真机验证 | `AudioFocus` + 128 MiB LRU；见 `docs/AI-TTS.md` |
| 流式 PCM | ⏸ 不启用 | design 音色无实际提前音频；保留预取/缓存，避免无效复杂度 |

## 3. 书架：抖动根因与已落地架构

### 3.1 根因

1. **插图单独一层位移**：`BooksScreen` 把 `dBase` 传给 `BookCover`，
   `BookCover` 用 `graphicsLayer { translationX = artParallax * 10f * density }`
   让插图层在卡片内部滑动；卡片同时有 `rotationY`、圆角裁切、阴影，拖动时插图在裁切框里滑动，产生抖动/闪动。
2. **zIndex 中点交换**：`zIndex = 10 - |dStack|`，拖到两张书中间时两张卡 zIndex 相等，
   越过中点后交叉，重叠封面在那一帧交换绘制顺序，出现闪动。
3. **六张封面每帧重组/重绘**：拖动时 `position` 每帧变化，六张 `BookCover` 都在 composition，
   重算 Canvas、阴影、3D transform，低端机放大前两个问题。

### 3.2 已落地模型

```kotlin
BookshelfWheel (纯 Kotlin，可单测)
  offset: Float          // 唯一位置真值，只在拖动/回弹时变化
  count: Int
  nearestIndex: Int      // derivedStateOf，按页变化，不按帧
  direction: Int         // 拖动开始/回弹目标决定，决定层叠顺序
  fun delta(index): Float
  fun settleTarget(velocity): Int
  fun stackOrder(index): Float
```

UI 规则：

- **一个 `graphicsLayer` 读 offset**：翻译、旋转、缩放、zIndex 都在 layer/draw 层；
  offset 只触发 layer invalidation，不触发六张封面重组。
- **插图同层**：插图、底色、文字放同一个 layer；拖动中插图不动。视差只在回弹/选中时做很小的插值，
  不越过裁切边缘。
- **稳定 zIndex**：`stackAnchor` 在拖动开始冻结、回弹结束后才更新；拖动中点不交换。
- **组合窗口**：拖动只组合中心 + 左右各一张；回弹目标超过一页时再扩展；不要六张全画。
- **手势只做水平**：`Modifier.draggable(Orientation.Horizontal)` 或 `detectHorizontalDragGestures`；
  不要 `detectDragGestures` + axis。
- **回弹**：一个 `Animatable` + spring/decay；`snapTo` 取消旧动画；目标归一化；
  点按卡片用“最近代表” target，避免绕远路。
- 书架视觉身份来自“环形牌堆 + 3D 转身”，不是插图每帧滑动。

### 3.3 对抗性清单

| 场景 | 处理 |
|---|---|
| 拖到两张书正中间 | 层叠由方向 + nearest 决定，中点不交换 |
| 快速甩过 2–3 张 | `settleTarget` 用 velocity + 最近代表；目标可越界，落定归一化 |
| 回弹中再次拖动 | 一个 `Animatable`；`snapTo` 取消旧动画；拖动只写 offset |
| 回弹中点按侧卡 | 从当前 offset 反推最近 index，只选中该卡 |
| 书数量/自动书计数变化 | spec 稳定 key；计数变化才更新 |
| 旋转/进程恢复 | 保存 nearestIndex + offset；恢复后归一化 |
| 只有 1 本书/空列表 | 模型层处理 count<=1，UI 单卡/空态 |
| RTL/无障碍 | contentDescription/selected 语义；方向不依赖 RTL |
| 低端机 60fps | 只 layer 变换；限制组合窗口；阴影不每帧重算 |
| 插图裁切 | 插图同层；视差只在安全范围 |

## 4. 学习卡：卡手根因与已落地架构

### 4.1 根因

- **脉冲在 composition 读动画值**：旧 `SpeakerButton` 的 `val value by transition.animateFloat(...)`
  在播放时每帧重组；新 `SpeechButton` 已把读取移进 `graphicsLayer`，但 Home/Review/Spelling 仍在用旧
  `SpeakerButton`。
- **单个 `detectDragGestures` + 一次性的 axis 锁定**：第一下稍微偏垂直就锁成 `axis=2`，
  之后水平拖动不翻页。
- **子组件抢手势**：例句页 `SpeakerButton` 的 `clickable`；主卡/中文层 `verticalScroll` 与上下切层冲突。
- **音频播放不是主因**：AudioCache 在 IO，MediaPlayer 异步；卡手来自 UI/手势。

### 4.2 已落地架构

- 拆成：`StudyDeckState`（队列/评分/回顾）、`StudyPageRing`（水平例句环）、
  `StudyLayerSwitch`（英文/中文层）、`StudyAudioCoordinator`（音频状态机）。
- **取消上下滑动切层**：改用现有「中文 / 英文」按钮；垂直滚动不再与切层抢手势。
- **水平例句环**：保留现有三页虚拟环，但只接 `detectHorizontalDragGestures`；不再有 axis 锁定。
- **喇叭**：`SpeechButton` 只识别 tap；AI 朗读由 `SpeechService` 单播放器取消旧 clip，
  兜底 `AudioPlayer` 也只有一个 job。
- **脉冲**：只在 `playing` 时存在，读在 `graphicsLayer`；拖动开始暂停。
- 若保留自定义环：axis 阈值 16–24dp，允许手指未抬起前重新判定。

### 4.3 对抗性清单

| 场景 | 处理 |
|---|---|
| 从喇叭上开始滑 | 喇叭只识别 tap，不消费 move |
| 主卡/中文层垂直滚动 | 取消上下切层，用按钮 |
| 例句环无限循环 | 虚拟页 + 取模，key 用 page % count |
| 快速连续翻页 | 单一 Pager/AnchoredDraggable 状态，不手写 job |
| 播放中翻页 | 页面变化 stop 旧 clip |
| 首句预取未完成 | 预取当前 + 下 2；未就绪轻量 loading/回退 |
| 长按选词 | 长按只作用于文本，水平移动父层优先 |
| 双击评分/下一词 | 保留现有 guard |
| 进程恢复 | 保存 page index + layer，不保存动画中间值 |
| 低端机 | 脉冲移出 composition；3D 只 layer；窗口限制 |

## 5. AI TTS

完整模型/音色/请求/延迟/架构/验收见 **`docs/AI-TTS.md`**。

关键结论：

- 用户喜欢 design 音色；**design 非流式延迟与英文预置音色接近**，差距在流式首包
  （Chloe ~0.73 s，design ~1.9–3.3 s 且无实际提前音频）。
- 决策：**design + 预取/缓存**；首次冷合成约 1.8 s，之后命中缓存。
- 当前 4.1 是本地测试包，key 内置；**正式必须可配置、无 key**。
- 待办：加密 `TtsConfigStore`、应用内「声音」配置页、release 无 key、音频焦点、LRU、可选流式。

## 6. 音效：从游戏奖励改为安静阅览室（已落地）

### 6.1 旧现状

`tools/sfx/make_sfx.py` 是纯加法正弦合成：`tone()` 2–4 个 partial + 指数衰减；`tick()` 噪声 + 210 Hz；
无低通、无 room、无母带；频率偏高（880/1318/2637 Hz），音量 ~0.5；
8 个事件，评分每次响、书架每次过卡响、练习/拼写也响。

### 6.2 已落地声音设计

| 事件 | 现在 | 目标 |
|---|---|---|
| 秒懂 | 明亮上行五度 | 温暖木琴/玻璃，轻、短 |
| 认识 | 同音色三度 | 单声软木/毡，低音量 |
| 模糊 | 单音 | 更低、更短 |
| 忘记 | 干 tick | 保留，更闷更轻 |
| 拼对 | 三音琶音 | 两声木琴，更低 |
| 拼错 | 单 tick | 保留，更闷 |
| 四选一答对 | 和弦 | 单声软木 |
| 书架过卡 | 纸页声 + 低毡 tick | 默认开启（5.0.1 起），无震动 |

- 母带：约 -18 LUFS、峰值 -3 dB、2–5 ms 淡入淡出。
- 默认音量 0.25–0.35；±2% 音高/力度随机；`SoundService` 统一 `SoundPool`、静音、音频焦点。
- AI 音效：离线生成 one-shot（3–5 候选/事件），人工筛选、母带、OGG，运行时只播本地文件；
  不建议运行时生成。没有 AI 音效服务时先做物理建模候选。
- 已重建 `tools/sfx/make_sfx.py` 并重新生成 8 个 WAV：峰值 0.28–0.50，时长 50–355 ms，
  `SoundService` 再乘 0.30 默认音量；书架过卡声自 5.0.1 起默认开启。
- 旧的三个 `SoundPool` 已合并为 `SoundService.shared(context)`；评分/拼写/练习/书架分组开关在
  「声音」设置里。

## 7. 首页品牌行三按钮 + 收纳（已实现，未真机验证）

- 品牌行只显示三个按钮：**词书**、**词表**、**收纳把手**。词书/词表是 40dp 半透明圆钮 +
  knockout 图标；把手是双线 Canvas，展开时旋转 45° 成 X。
- 点把手从右上角展开**纸白抽屉**（218dp 宽、16dp 圆角、14dp 阴影）；动画为
  fade + scale（从 TopEnd）+ 上移 8dp，150–200ms，一次性，不循环。
- 抽屉行：地图 / 备份与恢复 / 声音；声音未配置时右侧显示珊瑚色「未配置」；
  底部「重新看引导」清 flag 后重播。
- 不改变 hero 高度，不推搜索胶囊；抽屉 `zIndex` 覆盖搜索胶囊；点行关闭并导航；返回键关闭。
- 「声音」打开 `VoiceSettingsSheet`：纸面、衬线标题、墨色保存按钮；默认推荐 MiMo
  voicedesign 音色；密钥只写入加密 store，界面上不回显。
- 深色模式：hero 固定藏蓝，抽屉固定纸白 + 浅色墨值；设置 sheet 跟随主题。
- 「声音」sheet 下半部已是音效设置：总开关、评分/拼写/练习/书架分组、音量、恢复默认。

## 8. 首次下载动态引导（已实现，未真机验证）

- `ui/onboarding/OnboardingStore.kt`：`wordloom_onboarding` 的 done / tts_skipped / seen_at。
- `ui/onboarding/OnboardingScreen.kt`：4 页 `HorizontalPager`；欢迎、TTS、学习卡演示、书架。
- `ui/onboarding/TtsSetupPage.kt`：只暴露 key；高级设置折叠；测试发音显示合成耗时；
  保存走 `SpeechService` + `TtsConfigStore`。
- 首启由 `AppNav` 读 flag 决定 `onboarding` / `home`；完成或跳过写 flag，去书架会先落到 home
  再打开 books，保证返回栈正常。
- 「重新看引导」已放在首页收纳抽屉；完整流程/文案/验收见 **`docs/ONBOARDING.md`**。

## 9. 后续顺序（按此执行）

1. **可配置 TTS**：✅ 加密 store + 首页「声音」配置页 + release 无 key + 38 MB 包。
2. **首次引导**：✅ 首次启动 4 屏动态引导；TTS 未配置时首页保留安静入口。
3. **书架环形模型**：✅ `BookshelfWheel` + 稳定层叠 + 插图同层（未真机验证）。
4. **学习卡手势/音频协调**：✅ 水平拖动、按钮切层、单一朗读服务、音频焦点（未真机验证）。
5. **音效重做**：✅ 木质/毡质短音、统一 SoundService、分组开关（未真机验证）。
6. **首页收纳动画**：✅ 三按钮 + 纸白抽屉 + 重新看引导。
7. 真机验收（延迟、帧率、音频焦点、深色模式），再发 4.1/4.1.1；不要移动 v4.0.0 tag。

## 10. 构建与验证

- 工具链：JDK 17 + Android SDK 35；本机临时工具链在 `D:\code\person\toolchain`（不入库）。
- 编译：`./gradlew :app:assembleRelease`（release/debug 都只打 arm64）。
- 本地 TTS 测试包：`./gradlew :app:assembleRelease -PttsLocal`（key 来自 gitignored `local.properties`，
  仅本地；正式/CI 不带此参数，key 为空）。
- 单测：`:app:testDebugUnitTest`；4.0 与 4.1（2026-11-12，含引导）均通过。
- 发布前检查见 `docs/BUILD.md` 第 7 节；TTS 需额外确认 release APK 无 key：
  `python -c` 读取 `local.properties` 的 key 并检查是否出现在 APK 字节中，只输出布尔值，不打印 key。
