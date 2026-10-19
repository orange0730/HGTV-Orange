<p align="center"><img src="docs/preview/app-logo.png" width="160" alt="紅果TV 橘子版 圖示"></p>

# 紅果TV 橘子版

給 Android TV（也能裝在 Android 手機、平板、電視盒）的短劇播放器。
這是 [pan8664716/HGTV](https://github.com/pan8664716/HGTV)（紅果TV）的修改版，
加上**繁體中文介面**、**手機掃碼輸入搜尋**、**選擇任一版本更新**、**多位使用者**、**觀看統計**、**片頭片尾跳過**等功能。

> [!IMPORTANT]
> - 本應用**不是**紅果短劇的官方客戶端，與平台沒有任何關聯。內容來自平台的非官方介面，平台改版時隨時可能失效。
> - 本應用不提供帳號登入、不解鎖付費內容、不破解平台的新版加密或風控。
> - 原始碼以 [GPL-3.0](LICENSE) 授權公開；派生來源與第三方元件見 [NOTICE.md](NOTICE.md)。

## 目錄

- [和原版有什麼不同](#和原版有什麼不同)
- [下載與安裝](#下載與安裝)
- [使用說明](#使用說明)
- [隱私](#隱私)
- [已知限制](#已知限制)
- [專案結構](#專案結構)
- [開發](#開發)
- [發布新版本](#發布新版本)
- [授權與致謝](#授權與致謝)

## 和原版有什麼不同

| | 原版（pan8664716/HGTV） | 橘子版 |
| --- | --- | --- |
| 介面語言 | 簡體中文 | **繁體中文（臺灣用語）**，可切回簡體 |
| 搜尋 | 打簡體才找得到 | 繁體、簡體都找得到（送出前自動轉簡體） |
| 手機輸入 | 程式還在，但搜尋頁的按鈕被拿掉了 | 搜尋列加回「手機輸入」按鈕 |
| 應用內更新 | 只能裝最新版 | **可選任一版本**；可改來源倉庫、設定 token（私有倉庫）、選正式版或預覽版頻道 |
| 使用者 | 一台電視一份記錄 | **最多 6 位使用者**，各自的收藏、觀看記錄、播放偏好 |
| 觀看統計 | 無 | 今天、最近 7 天、30 天、累計、連續天數、最常看的劇 |
| 清晰度、倍速 | 全部劇共用一個設定 | 播放中改的**只記在這部劇**，設定頁的是全域預設 |
| 片頭片尾 | 無 | 每部劇各自設定，**自動跳過** |
| 套件名稱 | `com.hgtv.star` | `tw.orange.hgtv`（可以和原版**同時安裝**） |

> 上游在 GitHub 上公開的原始碼停在 1.0.9；上游 1.1.x 安裝包裡的功能（語音搜尋、軟體解碼、手機號登入、Android 4.4 支援）
> 沒有公開原始碼，因此不在橘子版內。

## 下載與安裝

1. 到 [Releases](https://github.com/orange0730/HGTV-Orange/releases/latest) 下載 `HGTV-orange-<版本號>.apk`。
2. 用隨身碟、區網分享或 ADB 裝到電視上：

   ```sh
   adb install -r HGTV-orange-2.0.0.apk
   ```

3. 系統需求：Android 8.0（API 26）以上。

**從原版轉過來**：橘子版和原版是兩個不同的 App，可以同時存在，記錄不會自動搬過來。
在原版裡用「設定 → 手機備份與恢復」把記錄存到手機，再到橘子版的同一個地方匯入
（橘子版讀得懂原版 `hgtv.library` 第 1 版格式的備份；原版 1.1.x 沒有公開原始碼，若它改過格式就可能無法匯入）。

**之後的更新**：在 App 裡「設定 → 橘子版 → 選擇版本安裝」就能更新，不必再手動下載。

## 使用說明

所有新功能都在「**設定**」頁中間的「**橘子版**」那一欄。

### 介面語言

「界面语言」切換繁體（臺灣用語）或簡體。劇名、簡介、彈幕都會跟著轉換；只改顯示，送給平台的資料不變。

### 手機輸入搜尋

用遙控器打字很慢的時候：

1. 進「搜尋」頁，按搜尋框旁邊的「**手機輸入**」。
2. 電視出現一個 QR Code，用手機掃描（手機和電視要在**同一個 Wi-Fi / 區網**）。
3. 在手機瀏覽器裡輸入劇名（繁體、簡體都可以），按送出，電視就開始搜尋。

這個 QR Code 是**一次性**的：網址含隨機密碼、只接受區網連線、用過一次或 5 分鐘後就失效，關掉視窗也會立刻失效。

### 選擇版本更新

「選擇版本安裝」會列出更新來源倉庫裡的所有版本（日期、預覽版標記、目前安裝的版本）。

- 選**較新**的版本：下載、比對 SHA-256、檢查套件名稱與簽名都通過後，交給系統安裝（收藏與記錄會保留）。
- 選**較舊**的版本：Android 不允許直接覆蓋成舊版，App 會說明原因，並顯示那一版的下載 QR Code。
  要裝舊版得先解除安裝（記錄會被清除，建議先用「手機備份與恢復」匯出）。
- 進入 App 時也會自動檢查一次新版（播放中不打擾；選過「暫不更新」就不再主動提示）。

### 更新來源、token、頻道

- 「**更新来源**」：預設是本倉庫 `orange0730/HGTV-Orange`，可以用手機掃碼改成別的倉庫（格式 `owner/repo`）。
  別的倉庫發的安裝包，**簽名必須和目前安裝的一樣**才裝得上。
- **token**：私有倉庫需要 GitHub token 才讀得到。建議建立只讀、只限該倉庫的
  [fine-grained token](https://github.com/settings/personal-access-tokens)（權限只要 Contents: Read）。
  token 只存在這台電視上，只會送給 `api.github.com`。
- 「**更新频道**」：只收正式版，或連預覽版（GitHub 上標為 Pre-release 的版本）也收。

### 多位使用者

「使用者」可以新增（從常用稱呼挑，或用手機輸入名字）、切換、重新命名、刪除。
每位使用者有自己的收藏、觀看記錄、播放偏好、每部劇的設定與觀看統計；
介面語言、更新設定、平台的裝置身分全家共用。
最多 6 位；第一位（預設使用者）與目前正在用的使用者不能刪除。

### 觀看統計

「觀看統計」顯示目前使用者今天、最近 7 天、最近 30 天、累計看了多久，看完幾集，連續觀看幾天，以及看最久的 5 部劇。
只記在這台電視上，可以清除。

### 每部劇的清晰度、倍速

在**播放中**用選單改清晰度或倍速，只會記在這部劇上，下次看這部劇自動套用；
「設定 → 播放偏好」裡的是全域預設，沒有個別設定的劇都用它。

### 片頭片尾自動跳過

播放時按選單（遙控器的「上」或「選單」鍵），最下面一行「**片頭片尾**」：

- 「片头到此刻」：片頭播完時按一下，之後這部劇每一集都從這裡開始。
- 「片尾从此刻」：片尾開始時按一下，之後播到這裡就直接下一集。
- 「清除本剧」：清掉這部劇的設定。
- 「自动跳过」：全部劇的總開關（設定頁也有一樣的開關）。

## 隱私

- 收藏、觀看記錄、統計、設定都**只存在這台裝置上**，不上傳。
- 手機輸入、手機備份用的是電視在區網內臨時開的小網頁：隨機網址、限區網、最長 5 分鐘、用完即關；
  這段連線是區網內的 HTTP，請在信任的家用網路使用（尤其是貼 token 的時候）。
- GitHub token 存在 App 的私有儲存區（不會被系統備份，`allowBackup=false`）。

## 已知限制

- 平台是非官方介面，改版時可能無法播放或搜尋；播放網址有時效。
- 橘子版新增的功能目前通過了單元測試、lint 與建置檢查，**尚待更多實機測試**；遇到問題請開 [Issue](https://github.com/orange0730/HGTV-Orange/issues)。
- 上游 1.1.x 的功能（語音搜尋等）沒有公開原始碼，橘子版沒有。

## 專案結構

```
android/                  Gradle 工程根目錄（用 Android Studio 打開這一層）
├─ app/                   電視介面、遙控器焦點、本機收藏與進度、彈幕、ExoPlayer 播放器
│  └─ src/main/kotlin/com/hgtv/star/
│     ├─ MainActivityOrange.kt   橘子版新增的設定欄、使用者、統計、片頭片尾、每部劇設定
│     ├─ data/OrangeStores.kt    橘子版資料怎麼存（每位使用者一份）
│     ├─ data/AppUpdater.kt      應用內更新（含選擇版本、更新來源）
│     └─ tv/ZhText.kt            介面語言（顯示時轉繁體）
├─ core/                  內容列表、搜尋、播放位址解析、彈幕解析（純 Kotlin，可在電腦上測試）
│  ├─ src/main/kotlin/…/ChineseText.kt   繁簡轉換（OpenCC 詞典）
│  ├─ src/main/kotlin/…/OrangeData.kt    使用者、每部劇設定、觀看統計的資料與計算
│  ├─ src/main/kotlin/…/AppUpdates.kt    更新資訊、版本清單、下載與驗證
│  ├─ src/main/resources/opencc/         OpenCC 1.4.2 詞典（Apache-2.0）
│  └─ src/test/           單元測試
docs/                     文件；CHANGELOG.md 同時是發布說明的來源
tools/                    版本、發布、圖示與主題的腳本
```

## 開發

需要 **JDK 17** 與 **Android SDK**（Platform 36、Build Tools 36.0.0）。Gradle 8.14.3 由包裝器自動下載。
設定 `ANDROID_HOME`，或在 `android/local.properties` 寫 `sdk.dir=C:/Android/sdk`（路徑用正斜線）。

```sh
# 單元測試 + lint + debug 版（不需要發布金鑰，可直接安裝測試）
./android/gradlew -p android :core:test :app:lintDebug :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

每次推送到 GitHub，[CI](.github/workflows/ci.yml) 也會跑同樣的檢查，並把 debug 版 APK 放在 Actions 的附件裡。

## 發布新版本

正式版在**維護者自己的電腦上**打包簽名（發布金鑰不上傳到 GitHub），再用 `gh` 發布：

1. 升版本號（versionName 與 versionCode 一起加大）：

   ```sh
   tools/bump-version.sh 2.0.1
   ```

2. 在 [docs/CHANGELOG.md](docs/CHANGELOG.md) 最上面寫 `## 2.0.1：<標題>` 這一節（它就是 Release 說明與 App 內的更新說明）。
3. 提交並推送。
4. 設定金鑰後發布（密碼請自己輸入，不要寫進檔案）：

   ```sh
   export HGTV_KEYSTORE=/path/to/release.jks HGTV_KEY_ALIAS=<alias>
   export HGTV_STORE_PASSWORD='…' HGTV_KEY_PASSWORD='…'
   tools/release.sh                # 正式版；加 --prerelease 發預覽版，加 --dry-run 只打包不發布
   ```

`tools/release.sh` 會先檢查工作區乾淨、提交已推送、CHANGELOG 有這一版、tag 還沒用過，
再跑測試與 lint、打正式包，產生 `HGTV-orange-<版本號>.apk`、`SHA256SUMS`、`update.json`，最後建立 GitHub Release。

> [!WARNING]
> 所有版本必須用**同一把金鑰**簽名。換了金鑰，已安裝的使用者就無法在 App 內更新（系統也不允許覆蓋安裝）。
> 請備份好金鑰檔與密碼。

## 授權與致謝

- 授權：[GPL-3.0-only](LICENSE)。修改與再發布時必須同樣以 GPL-3.0 公開原始碼。
- 本版由 [orange0730](https://github.com/orange0730) 修改。
- 上游：[pan8664716/HGTV](https://github.com/pan8664716/HGTV)（紅果TV），它又派生自
  [N3urda/hongguoTV](https://github.com/N3urda/hongguoTV)；上游程式碼的著作權歸原作者（pan8664716、N3urda、ermilylu 等）。
- 內容協定與加密處理移植自 [drpys](https://github.com/woshishiq1/drpys)（GPL-3.0）。
- 繁簡轉換詞典：[OpenCC](https://github.com/BYVoid/OpenCC)（Apache-2.0）。
- 完整的第三方元件清單見 [NOTICE.md](NOTICE.md)。

<details>
<summary>介面截圖（上游版本，橘子版另有繁體介面與「橘子版」設定欄）</summary>

<table>
<tr>
<td colspan="3" align="center"><img src="docs/preview/theme.png" width="100%" alt="十四套主題對比"></td>
</tr>
<tr>
<td align="center"><img src="docs/preview/1.jpg" width="100%" alt="介面截圖 1"></td>
<td align="center"><img src="docs/preview/2.jpg" width="100%" alt="介面截圖 2"></td>
<td align="center"><img src="docs/preview/3.jpg" width="100%" alt="介面截圖 3"></td>
</tr>
<tr>
<td align="center"><img src="docs/preview/4.webp" width="100%" alt="介面截圖 4"></td>
<td align="center"><img src="docs/preview/5.webp" width="100%" alt="介面截圖 5"></td>
<td align="center"><img src="docs/preview/6.webp" width="100%" alt="介面截圖 6"></td>
</tr>
</table>

</details>
