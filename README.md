# 易经卜卦 / I Ching Oracle

一款单屏的 Android 原生应用，从周易六十四卦（京房 / King Wen 序列）中随机抽取一卦，并可一键把卦象分享到 DeepSeek AI 求解读。

| | |
|---|---|
| 包名 | `com.thatgfsj.iching` |
| 当前版本 | 1.0.0 (versionCode 1) |
| 平台 | Android 8.0+ (API 26 ~ 34) |
| 技术栈 | Kotlin 2.0 + Jetpack Compose + Material 3 |

## 主要功能

- **首页**：64 卦名作为半透明字云缓慢飘动，居中显示"八卦"标题与"心诚则灵"题词，下方一颗"点击抽取"主按钮。
- **抽签动画**：点击进入抽签，六爻从下往上逐爻出现，3 秒内完成。
- **卦象详情**：卦名（中 / 拼音 / 英文）+ 六爻（阳 `━━━━━━━━━━` / 阴 `━━    ━━`）+ 卦辞 + 象传，配色为墨纸风格。
- **再抽一签**：点击卦象卡片任意位置，或底部"再抽一签"按钮，即可重抽。
- **问 AI**：底部"问 AI"按钮（仅在抽完卦后出现），弹窗确认后将当前卦象的卦名、卦辞、象传以系统分享的形式发送到 DeepSeek（包名 `com.deepseek.chat`），随后在 DeepSeek 中向 AI 描述想问的事。

## 快速构建

需要 JDK 17+ 与 Android SDK（platform 34、build-tools 34）。

```bash
cd IChingOracle
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

通过 ADB 安装到设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 目录结构

```
IChingOracle/
├── app/
│   ├── build.gradle.kts                       # AGP 配置，依赖通过 libs.versions.toml
│   ├── src/main/
│   │   ├── AndroidManifest.xml                # 含 <queries> 声明 DeepSeek 包名
│   │   ├── assets/hexagrams.json              # 64 卦（King Wen 序列）
│   │   ├── java/com/thatgfsj/iching/
│   │   │   ├── MainActivity.kt                # Activity + Compose host
│   │   │   ├── data/
│   │   │   │   ├── Hexagram.kt                # @Serializable 数据模型
│   │   │   │   └── HexagramRepository.kt      # 资产加载 + 随机抽取 + 全名列表
│   │   │   └── ui/
│   │   │       ├── oracle/
│   │   │       │   ├── IChingOracleScreen.kt  # 全部 Compose UI
│   │   │       │   ├── IChingViewModel.kt     # Initial / Drawing / Loaded / Error
│   │   │       │   └── DeepSeekShare.kt       # 分享文本 + ACTION_SEND
│   │   │       └── theme/Theme.kt             # 墨纸配色
│   │   └── res/                                # 启动图标、主题
│   └── src/test/java/com/thatgfsj/iching/data/
│       └── HexagramTest.kt                    # 4 个 JVM 单元测试
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

详见 [DEVELOPMENT.md](./DEVELOPMENT.md)。

## 数据流

```
MainActivity.setContent
  └─ IChingOracleTheme { IChingOracleScreen() }
       │  collectAsStateWithLifecycle
       ▼
  IChingViewModel ──── getAllNames() ────> HomePage 字云
       │  draw() ──► Drawing(hex) ──► delay 2.2s ──► Loaded(hex, fadeKey)
       ▼
  HexagramRepository.drawRandom()        DeepSeekShare.shareToDeepSeek()
       │                                          │
       ▼                                          ▼
  assets/hexagrams.json                   Intent.ACTION_SEND ──> com.deepseek.chat
```

## 许可证

MIT License。

## 数据来源

`app/src/main/assets/hexagrams.json` 内容参考通行本《周易》王弼注整理而得，已逐卦与原文比对修正标点与截断：

| 卦 | 修正内容 |
|---|---|
| 讼 | 卦辞断句 + 补"利见大人，不利涉大川" |
| 泰 | "吉，亨" → "吉亨" |
| 坎 | "维心亨，行有尚" → "维心亨。行有尚" |
| 既济 | "亨小，利贞" → "亨，小利贞" |
| 坤、复、夬、萃、震、艮 | 补全卦辞后半段 |

JSON 结构与 Rust 后端（`/o/clawwork/AgentCompanyOS/crates/pipe-server/src/hexagrams.json`）保持同步。