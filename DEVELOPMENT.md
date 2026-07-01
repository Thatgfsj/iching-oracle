# DEVELOPMENT.md

本文件记录易经卜卦 Android 应用的开发思路、模块划分与关键设计决策。

## 1. 技术栈

| 类别 | 选型 | 说明 |
|------|------|------|
| 语言 | Kotlin 2.0.20 | 主语言 |
| UI | Jetpack Compose + Material 3 (BOM 2024.11.00) | 单 Activity + 单 Composable 屏 |
| 构建 | AGP 8.7.0 + Gradle 8.10.2 | JDK 17 |
| 数据 | `kotlinx.serialization` + JSON | 64 卦数据烧入 `assets/` |
| 测试 | JUnit 4 | 纯 JVM 单元测试 |
| 异步 | Kotlin Coroutines + StateFlow | ViewModel 状态管理 |

**为什么不用 Room / DataStore？** 64 卦数据是只读、静态、不可变的，Room / DataStore 的迁移与初始化开销对这种场景不划算。直接 JSON + 懒加载单例足矣。

**为什么不用 Navigation Compose？** 整个 App 就一个屏，未来若加历史/浏览 64 卦等功能时再迁移。

## 2. 架构

单 module + MVVM，三层：

```
┌─────────────────────────────────────────────────┐
│  UI (Compose, ui/oracle/IChingOracleScreen.kt) │
│   - HomePage / DrawingView / LoadedView / ErrorView │
└─────────────────────────────────────────────────┘
                ▲   StateFlow<OracleUiState>
                │
┌─────────────────────────────────────────────────┐
│  ViewModel (ui/oracle/IChingViewModel.kt)        │
│   - Initial / Drawing / Loaded / Error           │
│   - draw() 触发状态机推进                         │
└─────────────────────────────────────────────────┘
                ▲   Hexagram (model) + Intent
                │
┌─────────────────────────────────────────────────┐
│  Data + Android                                │
│   - HexagramRepository (单例, lazy load)        │
│   - DeepSeekShare (ACTION_SEND chooser)         │
└─────────────────────────────────────────────────┘
```

## 3. 状态机

ViewModel 持有 `MutableStateFlow<OracleUiState>`，对外只暴露 `StateFlow`。

```kotlin
sealed interface OracleUiState {
    data object Initial : OracleUiState
    data class Drawing(val hexagram: Hexagram) : OracleUiState
    data class Loaded(val hexagram: Hexagram, val fadeKey: Int) : OracleUiState
    data class Error(val message: String) : OracleUiState
}
```

转换关系：

- `Initial` → 用户点"点击抽取" → `viewModel.draw()` → `Drawing(hex)` → `delay(2.2s)` → `Loaded(hex, fadeKey+1)`
- `Loaded` → 用户点"再抽一签"或点卡片 → 同样路径（Drawing → Loaded）
- `Loaded` → 用户点"问 AI" → 弹 `AskAiDialog` → 确认 → `Intent.ACTION_SEND` 路由到 DeepSeek
- 任意阶段 → repository 抛错 → `Error(message)`

为什么不把"是否正在播放动画"做成 ViewModel 字段而不是状态？因为动画时 UI 需要拿到卦象本身来逐爻显示，所以卦象必须在状态里。

## 4. 抽签动画（DrawingView）

总时长 2.2 秒，每爻 366ms。

```
时间轴 ─────────────────────────────────►

底部第 1 爻出现  ▲
                 │ ~366ms
第 2 爻出现       ▲
                 │ ~366ms
第 3 爻出现       ▲
                 │ ~366ms
第 4 爻出现       ▲
                 │ ~366ms
第 5 爻出现       ▲
                 │ ~366ms
第 6 爻出现       ▲
                 │
              t=2.2s  ──► Loaded 状态接管，显示卦象卡片
```

UI 用一个 `LaunchedEffect` 在 `delay(perLineMs)` 循环里累加 `visibleCount`，从底向上逐爻 `AnimatedVisibility` 淡入。

## 5. 首页字云

首页背景是 64 个卦名（来自 `HexagramRepository.allNames()`）按列表哈希做种子生成的固定位置散落，alpha 0.08。整层通过 `rememberInfiniteTransition` 做 ±12dp 缓慢往返漂移。

为什么不随机生成位置？每次 recomposition 重排会让字云抖动；用 `remember(names)` + 固定 RNG 种子保证同一份 names 列表始终得到同一张图。

## 6. 问 AI → DeepSeek

Android 系统分享 (`Intent.ACTION_SEND`) 加 `setPackage("com.deepseek.chat")`：

1. 用户点"问 AI"按钮
2. 弹出 `AlertDialog`（标题"请向 AI 描述你的问题"，正文解释分享流程）
3. 用户点"去分享" → 构造分享文本，调 `DeepSeekShare.shareToDeepSeek(context, hexagram)`
4. 函数先用 `context.packageManager.resolveActivity(deepSeekIntent, 0)` 试探 DeepSeek 是否可用
5. 若不可用，fallback 到不带 `setPackage` 的普通 chooser
6. `Intent.createChooser` 打开系统分享面板

**Android 11+ 包可见性**：AndroidManifest 已声明 `<queries><package android:name="com.deepseek.chat" /></queries>`，否则 chooser 会过滤掉 DeepSeek。

分享文本格式（`DeepSeekShare.formatHexagramShareText`）：

```
【易经卜卦】

我抽到了第 X 卦：乾（Qián / The Creative）

卦辞：元，亨，利，贞。
象传：天行健，君子以自强不息。

请帮我解读这卦对当前处境的启示。
```

## 7. 数据修正

`assets/hexagrams.json` 经历了标点与截断修正（详见根目录 README）。修正时严格按通行本王弼注比对，未引入个人解读。

## 8. 已知约束

- 不加音效（用户偏好）
- 不实现撤销/历史记录（用户没要求）
- 不做动态色（Material You 与"墨纸"风格冲突，注释里已说明）
- 单 module（功能规模不需要拆分）

## 9. 验证

```bash
./gradlew :app:compileDebugKotlin     # 编译通过
./gradlew :app:testDebugUnitTest      # 4/4 测试通过
./gradlew :app:assembleDebug          # 输出 app-debug.apk ≈ 13 MB
```

单元测试覆盖：
- `lines are computed bottom-up from the binary encoding` — 乾 / 坤 round-trip
- `line kind toggles per bit` — 混合二进制 `0b010101`
- `bundled hexagrams json parses cleanly` — 解析 + 64 条 + 唯一 binary
- `repository drawRandom picks within the dataset` — 50 次抽样覆盖