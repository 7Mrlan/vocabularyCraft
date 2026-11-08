# 把 vocabulary.com 逆向成安卓 App — 可行性研究与实施方案

> 侦察时间：2026-11-08　目标：https://www.vocabulary.com/
> 工具组合：`morluto/rea`（侦察 / 证据层） + `storytold/craft-anything`（生成 / 复刻层）

---

## 0. 一句话结论

**技术上可行，但不是"扒接口做客户端"那种可行。** vocabulary.com 没有公开 API，全站是服务端渲染的
HTML + Cloudflare 防护，公开可拿的只有「词条页 + 一个 2014 年就在用的 HTML 片段接口 + 音频 CDN」。
所以正确做法是：

> **rea 负责"看清它怎么运作"（网络观测 / HAR / 页面结构），craft-anything 负责"照着重做一个"
> （Kotlin + Compose 原生 UI），中间加一层自己写的解析网关 —— 而不是把网页塞进 WebView 就完事。**

学习/复习（quiz、list 进度、账号）全部在登录墙后面，这部分**不能也不该逆**，只能自己重写一套 SRS 逻辑。

---

## 1. 实测侦察结果（全部为本次实际请求的结论）

### 1.1 站点架构

| 项 | 实测 |
|---|---|
| 渲染方式 | 服务端渲染（JSP 风格），HTML 里直接带完整释义，不是 SPA |
| CDN / 防护 | `server: cloudflare`，下发 `__cf_bm` cookie，`cf-cache-status` 生效 |
| 前端资源 | `cdn.vocabulary.com/js3/assets/*.js`（smart-search、WordFinder、mixpanel 等，ESM 分包） |
| 结构化数据 | 词条页内嵌 `<script type="application/ld+json">`，`@type: DefinedTerm` |
| 官方 App | Google Play 搜不到官方包（前 15 条全为第三方）；App Store 那个同名 App 是 Curiosity Media 的第三方产品 |

### 1.2 可用的公开端点（重点）

| 端点 | 鉴权 | 返回 | 用途 |
|---|---|---|---|
| `GET /dictionary/{word}` | 无 | 完整词条 HTML（~60KB） | 解析主来源 |
| `GET /dictionary/definition.ajax?search={word}&lang=en` | **无** | **纯 HTML 片段（~23KB）**，无 `<html>` 外壳 | **首选**，字段最全、体积最小 |
| `GET /dictionary/` | 无 | 词典首页 | 搜索建议 / 词表入口 |
| `https://audio.vocabulary.com/1.0/us/{data-audio}.mp3` | 无 | `audio/mpeg`（实测 200） | 发音音频，`data-audio` 属性直接给出路径 |
| `https://www.vocabulary.com/sitemaps/*.xml` | 无 | sitemap 索引（articles / lists / …） | 词表发现 |

**不存在的东西**（都试过）：

- `api.vocabulary.com/dictionary/{word}` → 404
- `/api/dictionary/search?q=` → 404
- `definition.ajax` 加 `format=json` / `type=json` / `output=json` → 仍返回 HTML，**没有 JSON 模式**

### 1.3 `definition.ajax` 片段的解析契约（已用探针验证）

```
div.wordPage[data-word][data-lang][data-learnable][data-next][data-prev]
├─ div.col-1
│   ├─ div.ipa-with-audio          # 美/英各一个
│   │   ├─ div.us-flag-icon | div.uk-flag-icon
│   │   ├─ a.audio[data-audio="U/1RG942FZBMQCL"]
│   │   └─ span.span-replace-h3    # 音标，如 /juˈbɪkwədəs/
│   ├─ p.word-forms                # 词形变化
│   ├─ p.short / p.long            # 一句话释义 / 长释义
│   └─ div.word-definitions > ol > li.sense      # 一个义项一个 li
│       └─ div.defContent
│           ├─ div.pos-icon                      # 词性（verb / noun / adjective…，文本形式）
│           ├─ div.instances > span.detail       # 该义项的释义
│           └─ div.div-replace-dd                # 该义项的例句
├─ div.col-2
│   ├─ div.section.examples        # 综合例句（片段里可能为空，需异步补齐）
│   ├─ div.section.family          # 同根词
│   └─ div.section.related-lists > div.box-vocab-item
│       └─ a.vlist-title + p.vlist-txt           # 相关词表
└─ div.section.citation            # 引用格式（可直接做「复制引用」）
```

实测样本：

| 词 | 义项数 | 音标 | 音频 | 相关词表 |
|---|---|---|---|---|
| `ubiquitous` | 1 | us + uk | us | 3 |
| `ephemeral` | 2 | us + uk | us | 3 |
| `bank` | 18 | us + uk | us | 3 |
| `run` | **57** | us + uk | us | 0（长词条不给词表） |

`--tree` 模式可以直接打出 DOM 骨架，改版后用它对选择器。查不到的词返回的是「搜索建议页」（`found: false` + 候选词），
所以网关要处理两种响应形态。

> 已附可运行探针：`probe/fetch_word.py`（仅 stdlib，单次请求，输出归一化 JSON）。
> 它就是将来 Kotlin 网关 `HtmlWordParser` 的 1:1 参照实现。

### 1.4 边界（这些地方别碰）

- `/account/` → **302 跳 `/login/`**
- `robots.txt` 明确 Disallow：`/account/`、`/ui/`、`/auth/`、`/challenge/`、`/login/`、`/signup/`、`/join/`
- `GPTBot`、`CCBot`、`Applebot-Extended` → **Disallow: /**（站点明确不希望 AI 抓全站）
- 8 次连发仍返回 200（无速率限制迹象），但 Cloudflare 无承诺，**不要用来做批量爬库**
- 登录后的 quiz / learn / progress 接口：未探测、不建议探测（属于绕过访问控制）

---

## 2. 两个项目到底怎么分工

两个仓库定位完全不同，套用错了会白干：

| | `morluto/rea` | `storytold/craft-anything` |
|---|---|---|
| 定位 | **逆向工程 MCP 工具箱**（给 agent 用） | **Clean-room 复刻引擎**（给 agent 用） |
| 网站能力 | ✅ Websites：页面结构、脚本、网络观测、截图（需 Chrome 系浏览器）；✅ HAR / SAZ 抓包分析 | ✅ Study 阶段看页面：三种屏宽截图、可见文本、量出来的布局/颜色/字体/间距 |
| 关键差别 | 以 **证据** 为产物（网络请求、payload、来源位置） | 默认 **Clean room**：**不看**源码和文件，只从"访客看得到的画面"重建；`--deep` 才读 JS |
| 安卓相关 | ✅ APK 分析（JADX）、ADB 设备/日志/截图 | 产物是**工程目录**（`app/`），不是 APK，要自己 Gradle 打包 |
| 适合本项目 | 摸清数据契约、抓 HAR、定位音频/例句来源 | 生成 Compose UI 骨架 + 布局/配色/交互 |

**结论：rea 负责"数据从哪来、长什么样"，craft-anything 负责"界面长什么样"。**
两者都不负责"把网站打包成 App"——那部分必须自己写（或让 Claude Code 在你指定的 Kotlin 工程里写）。

### 组合流水线

```
        vocabulary.com
              │
   ┌──────────┴───────────┐
   │ ① rea（侦察）         │  browser-observation + HAR
   │                      │  → 端点清单 / 字段结构 / 音频 URL 规则
   └──────────┬───────────┘
              │  产出 docs/api-contract.md（你自己定 schema）
   ┌──────────┴───────────┐
   │ ② craft-anything     │  rebuild https://www.vocabulary.com/dictionary/ubiquitous
   │    （复刻）           │  "Kotlin + Jetpack Compose 安卓词典客户端，纯本地渲染"
   │                      │  → app/ 下生成 Gradle 工程 + 三屏宽对照检查
   └──────────┬───────────┘
              │
   ┌──────────┴───────────┐
   │ ③ 自研解析网关        │  Kotlin/Ktor 或 Node 小服务：抓 definition.ajax → JSON
   │    （必须自己写）      │  + 本地缓存 + 限速 + 超时降级
   └──────────┬───────────┘
              │
   ┌──────────┴───────────┐
   │ ④ Android Studio 打包 │  Gradle assembleDebug → APK
   └───────────────────────┘
```

---

## 3. 三条实现路线（按投入排序）

### 路线 A：WebView 壳（2–3 天，可行但不推荐单独做）

`WebView` + 自建工具栏 + 深色模式注入 + 外部链接处理。
- 优点：最省事，登录/复习功能自动可用（用户在 App 里自己登录，不算绕过）
- 缺点：离线为零、首屏慢、无法本地复习、应用商店大概率判"纯网页封装"而拒审
- 定位：**M0 验证用**，两三天内拿到能装的 APK

### 路线 B：原生 UI + 解析网关（推荐主线，2–4 周）

```
┌─────────────── Android (Kotlin + Compose) ───────────────┐
│  ui/        SearchScreen / WordScreen / ReviewScreen      │
│  data/      WordRepository ← Room 缓存 ← Retrofit        │
│  media/     Media3 ExoPlayer + 音频磁盘缓存               │
│  work/      WorkManager：每日一词、预取、缓存清理          │
└───────────────────────┬──────────────────────────────────┘
                        │ HTTPS（自己的网关）
              ┌─────────┴──────────┐
              │  Gateway (Ktor/Node)│  → 抓 definition.ajax
              │  · HTML→JSON 解析   │  → 归一化 schema
              │  · LRU + 磁盘缓存   │  → 失败降级到词条页
              │  · 限速 / UA / 退避 │  → 不并发轰炸
              └─────────┬──────────┘
                        ▼
                 vocabulary.com
```

为什么必须有网关：解析规则一旦变（class 名改版），只需热更新网关，不用发新版 APK；
同时把"抓取"集中到一个受控出口，能限速、能缓存、能换源。

**自定数据契约**（不要照抄站点字段名，按 App 需要设计）：

```json
{
  "word": "string",
  "ipa": { "us": "string", "uk": "string" },
  "audio": { "us": "https://audio.vocabulary.com/...", "uk": "..." },
  "senses": [{ "pos": "adj.", "short": "string", "long": "string", "examples": ["string"] }],
  "forms": ["string"],
  "relatedLists": [{ "title": "string", "unit": "string" }],
  "source": { "url": "https://www.vocabulary.com/dictionary/{word}", "fetchedAt": "ISO8601" }
}
```

### 路线 C：Clean-room 重写（craft-anything 主战场）

用 craft-anything 的 **Clean room 模式**（默认）让它只看渲染结果，生成一套"神似但不抄"的 Compose UI；
词库/复习算法全部自研（SM-2 / FSRS 都行）。
- 这才是唯一能**公开发布**的形态：不复制站点文案、不用它的 logo 和名称
- 缺点：内容要自己解决（要么用户本地按需查、要么接 CC 协议词库如 WordNet / Wiktionary）
- 定位：**最终形态**

**推荐节奏：A（验证）→ B（主力）→ C（可发布）**，代码可复用，不必推倒重来。

---

## 4. 落地命令

### 4.1 装工具

```bash
npx rea-agents@latest setup          # 注册 MCP + skill，需 Node 22.23+/24.22+/26.12+
```

craft-anything（需 Rust 1.97+，且本机已有登录的 coding agent）：

```bash
git clone https://github.com/storytold/craft-anything && cd craft-anything
cargo install --path apps/craft-anything-cli
craft-anything-cli doctor
```

### 4.2 侦察（rea）

在 agent 里让它做（MCP 已注册的话直接说）：

```
用 rea 打开 https://www.vocabulary.com/dictionary/ubiquitous，
监听网络请求，列出所有 XHR/fetch 与音频请求，截图三种屏宽，
把「词条页的字段结构 + 音频 URL 规则」整理成 api-contract.md
```

或走 HAR（更可控，能看清每个响应体）：

```
1. Chrome DevTools → Network → 勾 Preserve log → 搜 3~5 个词 → 右键 Save all as HAR
2. rea 分析该 HAR：请求、响应、暴露的 payload、来源位置
```

### 4.3 复刻（craft-anything）

```bash
craft-anything-cli rebuild https://www.vocabulary.com/dictionary/ubiquitous \
  "Kotlin + Jetpack Compose 的安卓词典客户端：搜索框、词条页（音标/发音/释义/例句/相关词表）、收藏、深色模式。数据来自我提供的网关 JSON，不要内嵌网页。"
```

- 默认 clean room；想让它读站点 JS 才加 `--deep`（**公开分发时不要用 deep 的产物**）
- `--rounds 2` 多轮对齐；`--continue-in app/` 接着改
- 产物在 `Documents/Craft Anything/<项目>/app/`，用 Android Studio 打开再 `./gradlew assembleDebug`

### 4.4 打包

```bash
cd app && ./gradlew assembleDebug
```

---

## 5. 合规红线（决定这个项目能不能见光）

1. **robots.txt**：`/dictionary/` 未被禁止 —— 按需查词可以走；`/account/ /login/ /challenge/ /ui/` 明确禁止，别碰。
2. **登录墙**：quiz / list / 进度接口不逆、不自动答题。GitHub 上已有 `Vocabulary.com-Answer-Bot` 这类项目，
   属于绕过访问控制 + 破坏服务条款，不要组合进来。
3. **版权**：释义、例句、词表文案都有版权。**只做"用户按需查询 + 设备本地缓存"**，不做批量抓取、
   不做随 App 内置词库、不做对外转发。界面上保留原文链接与来源标注。
4. **Cloudflare**：不并发轰炸（网关侧串行 + 退避），不伪造 cookie、不尝试绕过人机校验。
5. **商标**：App 名称、图标、配色不要用 Vocabulary.com / IXL 的品牌资产；自起名字。
6. **分发**：Google Play 对"仅网页封装"会拒审；路线 A 只适合自用 sideload。

---

## 6. 里程碑

| 阶段 | 内容 | 产出 | 预估 |
|---|---|---|---|
| M0 | rea 侦察 + 契约文档 + WebView 壳 | 可装 APK + `api-contract.md` | 3–5 天 |
| M1 | 网关 HTML→JSON + 原生搜索/词条页 | 核心可用 | 1–2 周 |
| M2 | 收藏 / 本地缓存 / 发音 / 深色模式 / 每日一词 | 日常可用 | 1–2 周 |
| M3 | 自研 SRS 复习 + 自有 UI（路线 C） | 可发布形态 | 3–4 周 |

---

## 7. 待你决策的点

1. **目的是自用还是上架？** 自用 → 路线 A→B 足够；上架 → 必须走 C，且内容源要换成可商用词库。
2. **要不要复习/游戏化？** 要 → 得完全自研 SRS，且不能碰站点登录态。
3. **网关放哪？** 本机/局域网（最省事）还是公网小服务器（多设备共享缓存）？
