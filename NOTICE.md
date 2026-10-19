# 派生來源與第三方聲明

## 0. 本倉庫：orange0730/HGTV-Orange（紅果TV 橘子版）

本倉庫是 [pan8664716/HGTV](https://github.com/pan8664716/HGTV) 的 fork，**自 2026-10-19 起由 orange0730 修改**，
按 GPL-3.0-only 派生。上游程式碼的著作權歸其作者（pan8664716、N3urda、ermilylu 等），派生部分歸本倉庫維護者。
倉庫根的 `LICENSE` 與上游同一份文字，未作改動。

派生基線：上游 `main` 的 `63c38c7`（`docs: 1.1.7.2 变更记录`，2026-10-19）。
注意：上游在 GitHub 上公開的 Android 原始碼停在 **1.0.9**（2026-10-16 的 `2e19694`），之後只有文件更新；
上游 Release 頁的 1.1.x 安裝包沒有對應的公開原始碼，因此那些版本的功能**不在**本版內。

本版相對上游的改動（GPL-3.0 §5(a) 要求的修改聲明）：

- 套件名 `com.hgtv.star` → `tw.orange.hgtv`，應用名改為「紅果TV 橘子版」，版本號從 2.0.0 起算，
  可以和上游版本同時安裝；
- 新增：繁體中文介面（臺灣用語，可切換簡體）、繁體搜尋、搜尋列「手機輸入」、選擇任一版本更新、
  更新來源 / token / 頻道設定、多位使用者、觀看統計、每部劇記住清晰度與倍速、片頭片尾自動跳過；
- 應用內更新改讀本倉庫的 Release，安裝包命名改為 `HGTV-orange-<版本號>.apk`；
- 發布流程改為本機 `tools/release.sh`（發布金鑰不上傳），GitHub Actions 只做測試與 debug 建置；
- 移除 `tools/login-lab/`（上游的登入實驗工具，與 App 建置無關）。

逐項改動見 [變更記錄](docs/CHANGELOG.md) 與 git 歷史。

## 1. 上游的派生來源：N3urda/hongguoTV（GPL-3.0）

（以下第 1、2 節是上游 pan8664716/HGTV 的聲明，原文保留。）

本仓库是 [N3urda/hongguoTV](https://github.com/N3urda/hongguoTV) 的 fork，**自 2026-10-06 起在上游基础上修改**，
按 GPL-3.0-only 派生。上游代码的著作权归其作者（N3urda、ermilylu 等），派生部分归本仓库维护者。
仓库根的 `LICENSE` 与上游同一份文本，未作改动。

本版相对上游的改动（GPL-3.0 §5(a) 要求的修改声明）：

- 移除 React Native 电视版、iOS 工程与 Node / Docker 内容服务，只保留 Android TV 原生客户端；
- 可构建的 Gradle 工程从 `kotlin-tv/` 移到 `android/`，仓库根只留文档与工具；
- 包名 `com.hongguotv.nativeapp` → `com.hgtv.star`，应用名改为「红果TV」；
- 重做界面（新拟态与十四套材质主题）、发布流程与产物命名。

派生基线为上游 `main` 的 `eea73e6`（`fix: release updater TLS connections off the main thread`，2026-09-23），
此后上游无新提交。逐项改动见 [变更记录](docs/CHANGELOG.md)。

## 2. 第三方依赖声明

`core` 中内容请求签名、Spade URL / key 处理、MP4 CENC 处理是以下 GPL-3.0 源码的 Kotlin 移植：

- drpys, `spider/js/红果果[短].js`
- https://github.com/woshishiq1/drpys/tree/22261adfa31435e3b3ef5a730b8a2a75bcaf1715
- 本版改动：完整 Kotlin 实现；签名只选择已验证的 hash branch 0；加密媒体通过 Media3 DataSource 直接读取；增加样本边界检查、子样本处理和 AVC 原始编码恢复。

本仓库的 Android TV 原生应用源码和派生实现以 GPL-3.0-only 发布，许可证全文见 `LICENSE`
（与仓库根目录 `LICENSE` 同一份文本）。源码仓库：https://github.com/orange0730/HGTV-Orange
（上游：https://github.com/pan8664716/HGTV ），
发布 APK 时同时提供对应源码、Gradle 包装器和构建说明。APK 不包含 Node、React Native、JavaScript 引擎或该 JavaScript 源文件。开发期用于逐行核对的上游 JS 参考实现只在本机使用，不随本仓库分发，也不参与应用构建或运行。

其他依赖：

- OpenCC 繁簡轉換詞典（橘子版新增，`core/src/main/resources/opencc/`，取自 ver.1.4.2，原樣未改）：
  https://github.com/BYVoid/OpenCC — Apache-2.0，授權全文隨詞典放在 `core/src/main/resources/opencc/LICENSE.txt`
- ZXing core（本机生成手机输入与更新二维码）：https://github.com/zxing/zxing — Apache-2.0
- Kotlin 标准库：https://github.com/JetBrains/kotlin — Apache-2.0
- AndroidX Media3：https://github.com/androidx/media — Apache-2.0
- OkHttp / Okio：https://github.com/square/okhttp / https://github.com/square/okio — Apache-2.0
- Bouncy Castle SM3：https://github.com/bcgit/bc-java — MIT
- jsoup（仅解析官网 HTML，不执行网页脚本）：https://jsoup.org/ — MIT
- Android desugar_jdk_libs：https://github.com/google/desugar_jdk_libs — GPL-2.0 with Classpath Exception
- org.json（仅 JVM 工具，APK 使用 Android 内置实现）：https://github.com/stleary/JSON-java — 公有领域声明
- JUnit（仅测试）：https://github.com/junit-team/junit4 — EPL-1.0
- Microsoft Fluent Emoji（弹幕表情图，`app/src/main/assets/danmaku_emoji/`，已转为 128px WebP）：
  https://github.com/microsoft/fluentui-emoji — MIT，许可证全文随图放在
  `app/src/main/assets/danmaku_emoji/LICENSE.txt`

应用图标与电视横幅（`app_icon.xml`、`tv_banner.png` 及其派生位图）由上游维护，并在上游基础上重绘（2026-10-12 改为扁平白底）。这里的应用不是内容平台官方客户端。

应用内「设置 → 开源许可」弹窗展示的就是本文件的信息摘要：GPL-3.0-only 的许可范围、
源码仓库 `orange0730/HGTV-Orange`（派生自 `pan8664716/HGTV`）、上面的第三方组件清单，
以及「本机记录只保存在这台设备上」这一条边界。改这里时同步改 `MainActivity.licenseText()`。
