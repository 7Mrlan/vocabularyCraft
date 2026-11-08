# 设计系统

这些值是从参考界面的截图里**采样**出来的，外加站点 CSS 的字体栈。颜色本身不受版权保护，
但**不要**复制对方的 logo、插画、图标字体（SSStandard）或版式资产。

## 配色

| Token | 亮色 | 深色 | 用途 |
|---|---|---|---|
| `primary` | `#005AFB` | `#4EA9FE` | 主蓝：CTA、链接、强调 |
| `primaryBright` | `#189FFE` | `#189FFE` | 次蓝：分区底色、插画 |
| `primaryDeep` | `#0139A3` | `#0B2A5B` | 深蓝：卡片、渐变起点 |
| `ink` | `#162941` | `#E9EFF7` | 正文与标题 |
| `inkMuted` | `#5B6B82` | `#9AA9BE` | 次要文本、例句 |
| `coral` | `#F06989` | `#F38EA5` | 强调卡（第二个功能卡） |
| `coralSoft` | `#FFE5E9` | `#3A1D26` | 珊瑚卡底色 |
| `mint` | `#D3FFF7` | `#10312C` | 薄荷卡（第三个功能卡） |
| `action` | `#43AA01` | `#61C42A` | 「开始/注册」类按钮 |
| `surfaceAlt` | `#F3F5F9` | `#162941` | 分区底、输入框 |
| `outline` | `#DCE3ED` | `#25364D` | 分隔线 |

采样方法（可复现）：把截图转成 BMP，按屏幕高度切带，统计每一带的高频色。
首页的 nav / hero / 青色带 / 卡片区分别落在 `#4EA9FE` / `#005AFB` / `#189FFE` / `#0139A3` 附近。

## 字体

参考站用 **Open Sans**（正文）+ **Merriweather**（标题）+ **SSStandard**（图标字体）。

本项目 v0.1 用平台字体族（`SansSerif` / `Serif`）起步，保证任何设备都能构建和渲染。
要 1:1 复刻：

1. 从 Google Fonts 下载 Open Sans / Merriweather 的 **可变字体或静态 TTF**（两者都是 OFL，可随 App 分发）
2. 放进 `app/src/main/res/font/`
3. 改 `ui/theme/Type.kt`：

```kotlin
val SansFamily = FontFamily(
    Font(R.font.open_sans_regular, FontWeight.Normal),
    Font(R.font.open_sans_semibold, FontWeight.SemiBold),
    Font(R.font.open_sans_bold, FontWeight.Bold),
)
val SerifFamily = FontFamily(
    Font(R.font.merriweather_regular, FontWeight.Normal),
    Font(R.font.merriweather_bold, FontWeight.Bold),
)
```

## 版式

- 词条标题：Serif Bold / 36sp / 行高 42sp（藏蓝，见下节）
- 导读短段：Serif / 17sp / 行高 28sp
- 导读长段、义项正文：Sans / 15sp / 行高 25sp
- 例句：Sans Italic / 14sp，`#666`
- 卡片圆角：18dp；搜索框：28dp 胶囊；按钮：16dp
- 页面左右边距：20dp（首页）/ 24dp（词条页）

## 首页结构（与参考站对齐的部分）

```
┌ 渐变 hero（deep → primary → bright）────────────┐
│  wordmark · 一句话 tagline                       │
│  [ 搜索胶囊 ]                                    │
│  ↻ 随机一个词                                     │
│         ～～～ 波浪过渡（Canvas 贝塞尔）～～～      │
├ 青色带 ─────────────────────────────────────────┤
│  [查词] [复习] [随机]  三张卡（蓝 / 珊瑚 / 薄荷）  │
└──────────────────────────────────────────────────┘
  今日复习卡（计数 + 进度）
  最近查过的词（横向 chips）
```

## 首页 hero（2026-11 改版）

圆形拼贴在手机宽度下有两种坏结果：要么被搜索框整块压住，要么为了露出来而撑出一大块空白，
把「随机一个词」推到离输入框很远的地方。所以 hero 改由**排版**承担识别度，不再放背景插画：

```
品牌行 ································ (地图入口)
查一个词，
记住一辈子。        ← 衬线粗体 26/36
[ 搜索胶囊 56dp ]
↻ 随机一个词        ← 距输入框 14dp
～～～ 波浪 ～～～
```

- 唯一的高饱和元素是那条搜索胶囊，其余靠字重和层级拉开
- hero 高度由内容决定，没有单独占位的装饰层
- 波浪仍然是从深蓝到亮蓝的唯一边界语言

## 地图（已实现）

参考站的地图不是自然底图，而是**整片蓝色调**：陆地被压成浅蓝灰，水面是高饱和蓝，
标签本身也是蓝的，再叠一层上浅下深的渐变融进蓝色分区。我们复刻的是这套读法：

- **底图**：MapLibre Native + OpenFreeMap `liberty` 样式（OSM 数据，无需 token）
- **双色调**：`MapTint` 在瓦片之上盖一层 4 段蓝色渐变（alpha 0.24–0.42），
  让陆地、水面、标签统一进蓝色系统，同时保留地名可读性
- **标签**：运行时把所有 symbol 图层的 `text-field` 改成
  `coalesce(name:en, name)`，去掉希腊文/阿拉伯文与拉丁文并排的杂音
- **会自己动**：`WorldMap(tour = true)` 每 9 秒在欧/东亚/北美/南美/非洲/南亚之间
  做 3.2 秒的镜头飞行，和原作那种"地图自己在漂"的感觉一致
- **飘卡**：白色 callout 每 5 秒轮换一张你**真实收藏**的词卡（词 + 释义摘要 + 收藏数）

> 原作 Bowl 地图上的学校名与词数是服务端按赛季下发的内容，页面里不存在、也无法从
> 前端反推。我们只复刻它的**行为**（自动巡航、数字滚动、轮播卡片），数据源换成用户
> 自己的收藏 —— 有意义的动画，而不是伪造的地理数据。

## 地图（早期方案存档）

参考站首页那张地图是 **Mapbox GL JS**：可拖动、可缩放、国家名 + 城市点、计数器滚动。
安卓端计划：

- **MapLibre Native**（Mapbox GL 的开源分支，Apache-2.0）
- 底图：**OpenFreeMap** 或 Protomaps 的 PMTiles（OSM 数据，无需 token，可商用）
- 样式：自定义 JSON，去掉道路/POI，只留国界、海陆、城市点 —— 这样才像它那种
  「干净的世界地图 + 一点装饰」而不是通用导航图
- 数字滚动：`animateIntAsState` + 自定义 `Text` 逐位动画
- 交互：`MapView` 原生 pan/zoom；镜头动画用 `CameraAnimationsPlugin`

不需要 Mapbox 账号，也没有 token 泄漏风险。

## 词条页（2026-11，按原站 CSS 复刻）

词条页的值**不是**采样出来的，而是从参考站的 `dictionary-*.css` 里直接读到的 CSS 变量与选择器。
这是踩过坑之后的规则：**颜色一律以样式表为准**——截图里同一处文字边缘会因为抗锯齿混出
`#14243A`、`#2D4D80` 之类的假色，和真值能差出 20 多个亮度级。

| 常量（`ui/word/WordScreen.kt`） | 值 | 对应原站 |
|---|---|---|
| `RefHeadline` | `#00578A` | `.word-area h1` / `.span-replace-h3`（标题、音标） |
| `RefLink` | `#0090E3` | 描边按钮、链接 |
| `RefLead` | `#444444` | `.blurb` 正文 |
| `RefForms` | `#545454` | `p.word-forms` |
| `RefExample` | `#666666` | 例句 |
| `RefPanel` | `#EDF7FC` | 浅蓝底块 |
| `RefBanner` | `#BE6DCF` | `Definitions of …` 紫色横幅 |

词性色沿用原站 `--pos-color-*`：

| 词性 | 色值 |
|---|---|
| noun | `#0FA646` |
| verb | `#CD4D03` |
| adjective | `#007BC4` |
| adverb | `#7B61FF` |
| preposition | `#BE6DCF` |
| pronoun / conjunction | `#44AA02` |
| interjection | `#FA52B7` |

结构（自上而下）：

```
← 标题行（返回 · 书签 · 刷新）        ← 不再重复词名，标题就在下面一行
quick  [☰ Add to list]            ← 36sp 衬线藏蓝 + 描边按钮
🇺🇸 🔊🔊 /kwɪk/  [🔊 全部]          ← 旗帜 + 每条录音一个喇叭 + 音标 + 全部连读
Other forms: quicker; quickest; quicks   ← #545454，词形加粗
如果……                             ← 短导读（衬线 17/28）
这段词还有一层意思……                 ← 长导读（无衬线 15/25 #444）
▛ Definitions of quick ▟          ← 紫色斜角横幅，白字斜体词名
1. (adjective)  moving quickly…    ← 编号列 26dp + 词性胶囊列 86dp + 正文
   "quick of foot"                 ← 斜体例句
   synonyms: agile, nimble, spry   ← 藏蓝，整行可点进同义词页
   显示全部 8 个义项 · 全部同义词     ← 两个文字按钮
Word family                        ← <vcom:wordfamily>，胶囊可点
复习进度 [====----] 0/8            ← 只在已收藏时出现，FSRS 本地状态
来源：Vocabulary.com / 原始链接
```

- **义项对齐**靠固定列宽：序号列 26dp、词性胶囊列 86dp，胶囊用 1dp 同色 40% 描边，
  这样「(adjective)」不会把正文推成参差的左边缘
- **收藏态**：书签为实心时用首页亮蓝 `#005AFB`（`wl.primary`），未收藏用 `inkMuted`
- 正文默认只显示 4 条义项，与参考站折叠行为一致
- **不再有底部双按钮**：加词表走标题旁的 `Add to list`，加复习走右上书签，
  底部重复一次只是把同一动作说两遍，还占掉半屏
- **顶部不再写一遍词名**：AppBar 左边只留返回箭头，词名交给 36sp 标题

### 同义词页

`SynonymsScreen` 把各义项的 synonyms 按义项分组，每条形如「义项序号 + 词性 + 定义（衬线斜体）
+ 胶囊词」；点任意胶囊进入该词的词条页。数据全部来自已经在内存里的 `WordEntry`，
进入这个页面不会再发一次网络请求。

### 深色模式

原站只有浅色一套，所以深色不是抄来的，而是把这套身份**抬亮**后重建的：色相不变，亮度提高，
保证在 `#0B1320` 上仍然读得清。实现是 `WordScreen.kt` 里的两个 `WordLook`（`LightLook` /
`DarkLook`），按 `wl.isDark` 二选一，不给中间态留混用的机会。

| 用途 | 浅色（原站） | 深色（本项目） |
|---|---|---|
| 标题 / 音标 | `#00578A` | `#6FC3EA` |
| 链接、描边按钮 | `#0090E3` | `#4CB8F5` |
| 短导读 | `#222222` | `#E6EBF1` |
| 长导读 | `#444444` | `#BFCAD8` |
| Other forms | `#545454` | `#9AA9BE` |
| 例句 | `#666666` | `#93A2B5` |
| synonyms 标签 | `#888888` | `#7D8CA0` |
| 浅蓝底块 | `#EDF7FC` | `#16293A` |
| 紫色横幅 | `#BE6DCF` | `#BE6DCF`（保持不变，白字仍可读） |

词性色同样各给一套亮色版本（noun `#4BD58A`、verb `#FF9350`、adjective `#56B6F0`、
adverb `#A894FF`…），由 `posColor(pos, dark)` 选择。

复习进度条在深色下用主题 `primary`（亮蓝）画进度、`panel` 画轨道；`#00578A` 在深底上
会糊成一片，不再使用。
