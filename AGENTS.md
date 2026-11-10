# AGENTS.md — 给 AI 助手的项目约定

Wordloom：本地优先的英英词典 + FSRS 间隔重复复习 App（Android / Kotlin / Compose）。
**动手前先读 `docs/BUILD.md`（构建、体积、发布）和 `docs/DESIGN.md`（设计令牌）。**

---

## 常用命令

```bash
./gradlew :app:assembleDebug          # 编译验证
./gradlew :app:assembleRelease        # 出包（release 只打 arm64-v8a）
./gradlew :app:assembleDebug --tests  # 单元测试（若新增）
```

工作分支是 `animation`，**不要直接改 `main`**；改动先落在 `animation`，验证后由维护者合并。

---

## 必须遵守的约束

1. **ABI：release 只打 `arm64-v8a`。**
   `app/build.gradle.kts` 里 `ndk { abiFilters += listOf("arm64-v8a") }` 是有意为之：
   MapLibre 与 ML Kit 每个 ABI 一份原生库（单 ABI 约 28 MiB），四 ABI 打包会让 APK 从
   38 MiB 涨到 117 MiB 而手机只加载一份。**不要新增/删除 `abiFilters`，除非明确要求。**
   debug 保持全 ABI（模拟器需要）。背景见 `docs/BUILD.md` 第 2 节。

2. **不要引入没被引用的依赖。** 已因无引用被删掉的四个：
   `datastore-preferences`、`material-icons-extended`、`haze-materials`、`ui-tooling-preview`。
   加依赖前先 grep 确认真的用到。

3. **不要用 `runCatching` 吞掉 `CancellationException`。**
   网络层统一用各自文件里的 `attempt { }` 辅助函数，它会重新抛出取消异常。
   否则协程取消后还会继续发下一次网络请求。

4. **失败语义要分清**：HTTP 404 = 「没有这个词」；IO / 5xx = 「请求失败，可重试」。
   联想接口：空列表 = 无匹配；抛异常 = 请求失败。UI 要能区分「没匹配」和「没结果」。

5. **Room 不得加回 `fallbackToDestructiveMigration()`。**
   它会在缺迁移时静默清空用户的复习卡与词表。升库必须写 `Migration`；
   `@Database(exportSchema = false)`，所以也不需要 `room.schemaLocation` 参数。

6. **深浅色两套色板不要混用。** 固定白底的表面（搜索胶囊、首页主卡、地图纸白飘卡）
   上的文字用浅色墨值（`#162941` / `#5B6B82` / `#005AFB`），不要用暗色 token。
   页面底色随主题的文字才用 `wl.ink` / `wl.inkMuted`。

7. **改动后同步文档**：`README.md`（能力与构建）、`docs/DESIGN.md`（令牌与动效）、
   `docs/BUILD.md`（工具链、体积、发布）。文档里的数字要实测，不要凭印象。

8. **提交信息遵循 Conventional Commits**（仓库有 pre-commit 门禁）：
   `feat(scope): …` / `fix(scope): …` / `perf(scope): …` / `chore(scope): …` 等，
   描述用中文并写清「改了什么、为什么」。

---

## 验证要求

- 有 Android SDK 的机器上，改完至少跑一次 `:app:assembleDebug`；
  没有工具链时**明确说明未做编译验证**，不要含糊带过。
- 涉及 UI 改动的，同步说明深色模式下的样子；
- 涉及网络/缓存的，说明离线与断网时的行为。

---

## 边界

- 仓库不带任何第三方词典正文；内容全部运行时抓取。
- `VocabularyComSource` 为主源，`WiktionarySource` 为兜底（CC BY-SA 4.0）。
- 不要把词典正文或第三方资产打包进仓库。
