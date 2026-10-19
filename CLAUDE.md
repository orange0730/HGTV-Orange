# 紅果TV 橘子版（HGTV-Orange）

使用者：吳東橙（GitHub: orange0730），工作語言為繁體中文。

## 這是什麼
- [pan8664716/HGTV](https://github.com/pan8664716/HGTV) 的 GPL-3.0 fork，倉庫 `orange0730/HGTV-Orange`，工作分支習慣用 `orange-*`。
- 真正的 Kotlin 原始碼（Gradle），**不是** `D:\code\hgtv-mod`（那是另一個 App：SkillGodAk 版的 smali 修改，不要混用程式碼）。
- 上游公開的原始碼停在 1.0.9；上游 1.1.x 的功能沒有原始碼，不要宣稱橘子版有。
- 套件名 `tw.orange.hgtv`、應用名「紅果TV 橘子版」、版本 2.0.0（versionCode 200）起算。Kotlin 的 namespace 仍是 `com.hgtv.star`（為了好合併上游，不要改）。

## 橘子版新增的東西放在哪
- `app/.../MainActivityOrange.kt`：設定頁「橘子版」那一欄、使用者、統計、片頭片尾、每部劇清晰度 / 倍速。
- `app/.../data/OrangeStores.kt`：資料檔配置（每位使用者一份 `native-library-v1[-id]`、`orange-series-v1-id`、`orange-stats-v1-id`；整台共用 `orange-device-v1`；平台裝置身分在 `native-session-v1`，換使用者不重新註冊）。
- `app/.../tv/ZhText.kt` + `TvComponents.kt` 的 `ZhTextView`：顯示時簡轉繁。送給平台的字串（分類名、搜尋詞、id）**不能轉**；搜尋詞用 `Zh.query()` 轉簡體。
- `core/.../ChineseText.kt`：OpenCC 1.4.2 詞典（`core/src/main/resources/opencc/`，Apache-2.0）。
- `core/.../AppUpdates.kt`：更新來源 / token / 頻道、版本清單。安裝包命名 `HGTV-orange-<版本>.apk`，Release 要附 `update.json`。
- `core/.../OrangeData.kt`：使用者、每部劇設定、觀看統計的資料與計算（有單元測試）。
- 盡量把新程式寫在新檔案，少改上游檔案，之後合併上游比較容易。

## 建置與測試（Windows 這台）
- JDK：`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`；Android SDK：`C:\Android\sdk`（`android/local.properties` 寫 `sdk.dir=C:/Android/sdk`，要用正斜線）。D: 槽幾乎滿了，SDK 與 Gradle 快取都在 C:。
- `./android/gradlew -p android :core:test :app:lintDebug :app:assembleDebug`
- 本倉庫 `core.autocrlf=false`、`core.eol=lf`（全域 Git 設定會轉 CRLF，弄壞 shell 腳本）。
- Windows 的 `python3` 是商店假程式；腳本裡要挑真的能跑的 Python（見 `tools/release.sh`）。
- 上游的 `tools/verify-button-system.py` 本身已過期（只掃頂層目錄，修好後也會在上游自己的檔案上失敗），沒有放進 CI。

## 發版規則（重要）
- 正式版在本機用 `tools/release.sh` 打包簽名並發布；金鑰不上傳 GitHub。CI（`.github/workflows/ci.yml`）只跑測試、lint、debug 建置。
- 所有版本用**同一把金鑰**簽名；升版用 `tools/bump-version.sh`，並在 `docs/CHANGELOG.md` 寫「## <版本>：…」一節（Release 說明與 App 內更新說明都從這裡取）。
- 沒有使用者明確要求，不要改版本號、不要推送、不要發 Release（上游 `AGENTS.md` 也是這個規則）。

## 不做
- App 內登入紅果帳號、裝置註冊 / 請求簽名 / 風控相關的修改、破解新版加密、解鎖付費內容。上游已有的這些模組原樣保留、不改。
- 已移除上游的 `tools/login-lab/`（登入實驗工具），不要加回來。
