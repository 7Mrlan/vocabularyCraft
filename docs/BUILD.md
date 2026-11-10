# 构建、体积与发布

这份文档记录**怎么构建、为什么这么构建、发布时检查什么**。
代码里的选择都会在这里写一句理由，避免下一次改动（尤其是换模型或升依赖）把它悄悄改回去。

---

## 1. 工具链

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17（Temurin） | AGP 8.7.3 要求 JDK 17；Android Studio 自带的 JBR 即可 |
| Gradle | 8.11.1 | 用仓库里的 wrapper（`gradlew` / `gradlew.bat`） |
| AGP / Kotlin / KSP | 8.7.3 / 2.0.21 / 2.0.21-1.0.28 | 见 `gradle/libs.versions.toml` |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | minSdk 26 = Android 8.0 |
| Compose BOM | 2024.12.01 | 所有 Compose 依赖版本由它统一 |

```bash
# macOS / Linux
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug

# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

> 踩坑：某些网络环境下 `services.gradle.org` 连接会超时，wrapper 下载 Gradle 发行版失败。
> 解决办法是提高 wrapper 的 `networkTimeout`，或直接用本地 Gradle 发行版的 `bin/gradle.bat`。

---

## 2. ABI 策略：release 只打 `arm64-v8a`（**重要，别改**）

`app/build.gradle.kts` 的 release 构建里有：

```kotlin
ndk {
    abiFilters += listOf("arm64-v8a")
}
```

**为什么只留一个 ABI**：`com.google.mlkit:translate` 与 `org.maplibre.gl:android-sdk` 都带原生库，
**每个 ABI 各一份**，而手机只会加载自己那一份：

| 原生库 | arm64-v8a | armeabi-v7a | x86 | x86_64 |
|---|---|---|---|---|
| `libtranslate_jni.so`（ML Kit 翻译运行时） | 15.6 MiB | 11.1 MiB | 16.5 MiB | 16.6 MiB |
| `libmaplibre.so`（地图） | 12.6 MiB | 9.1 MiB | 12.9 MiB | 12.9 MiB |
| **单个 ABI 合计** | **28.2 MiB** | 20.2 MiB | 29.4 MiB | 29.5 MiB |

把四个 ABI 全打进一个 universal APK，等于白背 79 MiB 永远用不到的原生代码：

| APK | 体积 | 说明 |
|---|---|---|
| v0.1.x（四 ABI，但只有 MapLibre） | 60.1 MiB | |
| v0.2.0（加了 ML Kit 翻译） | 121.2 MiB | 体积翻倍的原因就在这里 |
| v0.2.0 之前 + arm64 限制 | **37.7 MiB** | 当前版本 |

**规则**：

- release 只打 `arm64-v8a`（2017 年后的手机全部支持）；
- **debug 保留全部 ABI**，否则 x86 模拟器装不上；
- 需要 32 位老机时，在 `abiFilters` 里加 `armeabi-v7a`（包体 +11 MiB）；
- 需要在模拟器上装 release 时，临时把 `abiFilters` 去掉（包体 +59 MiB），别把过滤条件留在仓库里。

自查：

```bash
aapt2 dump badging app/build/outputs/apk/release/app-release.apk | findstr native-code
# 期望：native-code: 'arm64-v8a'
```

---

## 3. 体积账与体检

当前 release APK（37.7 MiB）的构成：

| 部分 | 未压缩体积 |
|---|---|
| `lib/arm64-v8a/*`（ML Kit + MapLibre） | 28.2 MiB |
| `classes.dex` + `classes2.dex` + `classes3.dex` | 23.6 MiB |
| `res/` + `assets/` | 0.5 MiB |

想看真实构成，解压统计：

```bash
unzip -l app/build/outputs/apk/release/app-release.apk | sort -k1 -n -r | head -20
```

dex 还有 23.6 MiB，因为 release 目前 **不开混淆**（`isMinifyEnabled = false`）：
`material-icons-extended` 这类依赖一旦被 R8 裁剪问题较多，收益（约 10 MiB）远小于风险，
本项目选择保持可读性与稳定。想开的话，先在 `proguard-rules.pro` 里补齐 Room / kotlinx.serialization
的 keep 规则，再实测。

---

## 4. 依赖卫生：这几个是**故意删掉**的，别再加回来

| 已删除 | 原因 |
|---|---|
| `androidx.datastore:datastore-preferences` | 全仓没有一处用到；主题偏好走 `SharedPreferences` |
| `androidx.compose.material:material-icons-extended` | 全站自绘图标，用不到 Material 图标集；它还白占 dex |
| `dev.chrisbanes.haze:haze-materials` | 磨砂卡片用的是 `haze` 核心 + 自己写的渐变层 |
| `androidx.compose.ui:ui-tooling-preview` | 没有 `@Preview`，`ui-tooling`（debug 用）留着即可 |

另外：`@Database(exportSchema = false)`，所以 **KSP 不该再传 `room.schemaLocation`**（已删除），
否则 Room 会去找一个不存在的 schema 目录。

新增依赖前先确认真的被引用；不要为了「也许以后用」装进包里。

---

## 5. 签名

```kotlin
// app/build.gradle.kts
signingConfig = if (localProps.getProperty("wordloom.storeFile") != null) {
    signingConfigs.getByName("release")
} else {
    signingConfigs.getByName("debug")
}
```

- `local.properties` 里读四个键：`wordloom.storeFile` / `wordloom.storePassword` /
  `wordloom.keyAlias` / `wordloom.keyPassword`；
- `local.properties` 与 `keystore/` 都不入库（见 `.gitignore`）；
- **没有 keystore 时回退 debug 签名**：包能装、能跑，但**无法覆盖安装旧版本**
  （签名不一致会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`），必须先卸载。
  正式发版请用真实 keystore 重签。

校验：

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## 6. CI：GitHub Actions 云端出包

`.github/workflows/release.yml` 已入库：

- **触发**：推送 `v*` 标签，或在 Actions 页手动 `workflow_dispatch`（输入已存在的标签名）；
- **环境**：GitHub 托管的 `ubuntu-latest`，JDK 17 + Android SDK 35；
- **产物**：`app/build/outputs/apk/release/app-release.apk`，同时上传为 Actions artifact
  并自动挂到对应的 GitHub Release；
- **门禁**：`Verify APK (ABI and size budget)` 步骤会用 `aapt2` 校验
  **只含 arm64-v8a**（多一个 ABI 就失败）且**体积 ≤ 60 MiB**（超预算就失败）。
  这条是机械校验：不管谁改动了 `abiFilters`，多打一份原生库都会让 CI 变红，
  而不是悄悄把包体从 38 MiB 涨回 117 MiB；
- **签名**：CI 上没有 `local.properties`，所以同样是 debug 签名。

> **已知阻塞**：如果账号处于 billing lock，任务根本不会启动，报的是
> `The job was not started because your account is locked due to a billing issue.`。
> 公开仓库用 Actions 本来不消耗付费分钟，这个锁是**账号级**的
> （付款方式失效 / 未结清账单 / 花费上限被触发），
> 在 <https://github.com/settings/billing> 处理即可，workflow 本身不用改。

---

## 7. 发布前检查清单

- [ ] `./gradlew :app:assembleDebug` 通过（至少证明能编译）
- [ ] `./gradlew :app:assembleRelease` 通过，产出 `app-release.apk`
- [ ] `aapt2 dump badging`：versionCode / versionName 正确、`native-code` 只有 `arm64-v8a`
- [ ] APK 体积 ≤ 60 MiB（CI 的 `Verify APK` 步骤会自动卡这条，本地可先看一眼文件大小）
- [ ] `apksigner verify`：签名符合预期（debug 或 release keystore）
- [ ] 真机安装并走一遍：首页、深浅色切换、查词与跳转、复习评分、四选一练习、地图巡航、备份导出/导入
- [ ] 长按选词翻译：**联网**（有道）与**断网**（ML Kit 端上模型）各试一次
- [ ] Release 资产已替换：`gh release upload v<version> <apk> --clobber`
