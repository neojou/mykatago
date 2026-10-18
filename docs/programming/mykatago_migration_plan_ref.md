# mykatago 移植計畫：從模板到 KaTrain 式復盤工具（macOS Desktop）

> 依據：`https://github.com/neojou/mykatago` HEAD `7fd0cd7`（2026-10-18 10:23 +0800，台北時間，訊息「version 0.1.0 Kotlin multiplatform」）。
> 已查證 = 讀過 repo 檔案；推測 = 設計建議或尚未實測的 API。

## 1. 現況（已查證）

| 項目 | 內容 |
|---|---|
| 性質 | 從 KMP 模板（Hello2026 系列）複製來的骨架，**還沒有任何圍棋或 KataGo 程式碼** |
| Targets | `jvm("desktop")` + `wasmJs { browser() }`（`composeApp/build.gradle.kts`） |
| 版本 | Kotlin **2.3.0**、Compose Multiplatform **1.10.0**（`gradle/libs.versions.toml`）、Gradle **9.3.0**（wrapper）、`jvmToolchain(25)`，需要 JDK 25 |
| 依賴 | 只有 compose runtime、foundation、material3、ui、components.resources，以及 desktop 的 `compose.desktop.currentOs`。**沒有 kotlinx-serialization**。`MyLog` 用的 kotlinx-coroutines 是透過 compose 間接引入的（推測） |
| 檔案 | commonMain：`App.kt`（AboutRequest、初始化狀態）、`AppVersion.kt`（APP_NAME、NAME 加上 configure 標記）、`HomeScreen.kt`（只有 About 選單與置中的 APP_NAME；KDoc 和 `MainContent.KChart` 是模板留下的，跟這個 App 無關）、`Theme.kt`（Noto Sans TC）、`com/neojou/tools/{MyLog,SystemSettings}.kt`、`tools/ui/menu/{MyTopMenuBar,MyTopMenuItem}.kt`（App 內的 TopAppBar 選單，最多兩層）。desktopMain：`Main.kt`（Window 960×640、macOS About handler）。wasmJsMain：`WasmMain.kt`、`index.html` |
| 版本與名稱 | `gradle.properties`：`app.displayName=mykatago`、`app.rootName=Mykatago`、`app.group=com.neojou.mykatago`、`app.version=0.1.0`；`AppVersion.NAME="0.1.0" // configure:app.version`。用 `./configure.sh version X` 一次改兩處 |
| 文件 | `docs/programming/How-To-Change_Project_Name.md`、`How-To-Change_Version_number.md`（說明文字寫模板初始值是 0.1，實際已經是 0.1.0，有點小落差） |
| 測試 | **沒有**，沒有 commonTest 或 desktopTest |
| 雜項 | `build.gradle.kts` 的註解提到「Vite/npm SPA (package.json)」，`.gitignore` 也有 next.js、vite 的段落，但 repo 裡沒有 package.json，這些都是模板殘留 |

### ⚠ 關於移除 wasm 的風險（已查證：`configure.sh`）
`configure.sh` 寫死了：
- `SOURCE_ROOTS` 包含 `composeApp/src/wasmJsMain/kotlin`
- `INDEX_HTML = composeApp/src/wasmJsMain/resources/index.html`，而且必須剛好有一個 `<title>`

如果刪掉 wasmJs target 和這些目錄，`./configure.sh proj_name` 會直接失敗（`version` 子命令不受影響）。
**建議：第一版保留 wasmJs target，讓它能編譯就好**，wasm 端只顯示「此版本僅支援 macOS 桌面」。所有引擎、檔案、Process 相關的程式碼都放在 desktopMain。若 Neo 想移除 wasm，**要先問他**，並同步修改 configure.sh 和兩份文件。

## 2. 架構建議

```
composeApp/src/
├─ commonMain/kotlin/com/neojou/mykatago/
│   ├─ App.kt                    (改：接受 PlatformServices；Ready 時顯示 ReviewScreen)
│   ├─ AppVersion.kt             (不動，用 configure.sh 改版號)
│   ├─ HomeScreen.kt             (保留 AboutDialog；HomeScreen 改為 ReviewScreen 的外殼，或在 P0 時直接移除 HomeScreen 並把 AboutDialog 搬到 AboutDialog.kt)
│   ├─ Theme.kt                  (改：加上 KaTrain 深色配色)
│   ├─ go/                       純 Kotlin，不依賴平台
│   │   ├─ Coordinates.kt        Point、GTP/SGF 互轉（跳過 I）
│   │   ├─ BoardState.kt         提子、劫、自殺、提子數
│   │   ├─ GameNode.kt / GameTree.kt
│   │   ├─ SgfParser.kt / SgfWriter.kt
│   │   └─ Rules.kt              RU → KataGo rules 字串
│   ├─ analysis/
│   │   ├─ AnalysisModels.kt     @Serializable 的 Query/Response/MoveInfo/RootInfo
│   │   ├─ NodeAnalysis.kt       合併邏輯、candidateMoves、pointsLost、evaluationClass
│   │   ├─ AnalysisEngine.kt     interface：start/stop/analyze(q): Flow<Response>/terminate/status: StateFlow
│   │   ├─ AnalysisController.kt ponder、analyzeWholeGame、deeper、reset、generation
│   │   └─ GameReport.kt         (P1) accuracy、直方圖
│   ├─ settings/AppSettings.kt   @Serializable data class（engine paths、visits、thresholds、UI toggles）
│   │                            + interface SettingsStore
│   ├─ platform/PlatformServices.kt  interface：pickSgfFile()、readText(path)、saveFile()、engineFactory、settingsStore、openUrl
│   └─ ui/review/
│       ├─ ReviewScreen.kt  ReviewState.kt（ViewModel 風格，StateFlow）
│       ├─ GoBoard.kt  AnalysisToolbar.kt  BottomNavBar.kt
│       ├─ RightPanel.kt  ScoreGraph.kt  TopMovesTable.kt  StatsPanel.kt  NotesPanel.kt
│       └─ dialogs/AnalysisSettingsDialog.kt  EngineSettingsDialog.kt  WholeGameAnalysisDialog.kt
├─ commonTest/kotlin/...         SgfParserTest、BoardStateTest、CoordinatesTest、NodeAnalysisTest
├─ desktopMain/kotlin/com/neojou/mykatago/
│   ├─ Main.kt                  (改：MenuBar、onKeyEvent、建立 DesktopPlatformServices、關閉時 stop 引擎)
│   ├─ engine/KataGoProcessEngine.kt   ProcessBuilder + JSON lines + coroutines
│   ├─ engine/KataGoPathDetector.kt    /opt/homebrew、/usr/local、PATH
│   ├─ platform/DesktopPlatformServices.kt  FileDialog、編碼偵測、Desktop.browse
│   └─ settings/JsonFileSettingsStore.kt   ~/Library/Application Support/mykatago/settings.json
└─ wasmJsMain/…                  (保留；提供「不支援」的 PlatformServices stub)
```

### 主要設計決定
1. **協定**：只用 KataGo Analysis Engine（JSON lines），細節見 `katago_analysis_engine_protocol_ref.md`。不使用 GTP。
2. **JSON**：加入 `org.jetbrains.kotlin.plugin.serialization`（版本跟 Kotlin 一樣是 2.3.0）以及 `kotlinx-serialization-json`。2026-10-18 maven central 上的最新 release 標記是 1.12.0-RC，**請選 1.11.x 以上的穩定版，並確認跟 Kotlin 2.3.0 相容**（推測）。設定 `Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }`。
3. **Coroutines**：`kotlinx-coroutines-core` 明確加進 commonMain（目前是間接依賴，推測）。Desktop 的 `Dispatchers.Main` 需要 `kotlinx-coroutines-swing`，`compose.desktop.currentOs` 應該已經帶進來了（推測，需要確認）。IO 用 `Dispatchers.IO`，它只在 JVM 上有，所以要放在 desktopMain。
4. **引擎生命週期**：App 啟動後，若設定有效就自動啟動引擎；修改設定後重新啟動（KaTrain 會顯示「改變設定後，重新啟動引擎」）。`Window(onCloseRequest = { engine.stop(); exitApplication() })`，再加一個 shutdown hook 當保險。
5. **query 路由**：`ConcurrentHashMap<String, SendChannel<AnalysisResponse>>`，key 是 id。整局批次時用 `id` + `turnNumber`。保留 `generation` 計數器，載入新棋譜時加一並 `terminate_all`。
6. **取消**：ponder 用 Flow 並在 `awaitClose { terminate(id) }`。換節點時取消舊 Job，就會自動送出 terminate。
7. **狀態管理**：`ReviewState`（commonMain 的類別，持有 `MutableStateFlow`）：`tree`、`currentNode`、`board`、`analysisVersion: Int`（分析結果更新時加一，觸發重繪）、`toggles`、`engineStatus`、`settings`。UI 用 `collectAsState()`。分析結果用 `Dispatchers.Main` 寫回，或在 StateFlow 上更新不可變的副本。**不要從 IO 執行緒直接改 Compose state。**
8. **UI 刷新頻率**：ponder 每 0.25 秒回報一次，直接更新即可。整局分析時大量回應進來，圖表要合併後再更新，例如最多每 200ms 一次（推測）。
9. **選單**：用 `FrameWindowScope.MenuBar { Menu("檔案") { Item("開啟 SGF…", shortcut = KeyShortcut(Key.O, meta = true), onClick = …) } }`（Compose Desktop API，推測 1.10 的簽名不變，需要確認）。App 內頂列只放 5 個開關和「分析選項」下拉選單（照截圖），`MyTopMenuBar` 可以重用，或改寫成新的 `AnalysisToolbar`。
10. **檔案選擇**：`java.awt.FileDialog(frame, "開啟棋譜", FileDialog.LOAD)`，設定 `filenameFilter`。在 macOS 上 `FileDialog` 是原生的視窗，比 JFileChooser 好用（推測）。用 `System.setProperty("apple.awt.fileDialogForDirectories","false")`。
11. **設定持久化**：`~/Library/Application Support/mykatago/settings.json`，用 kotlinx.serialization 讀寫，第一次啟動時自動偵測 KataGo 路徑並寫入。
12. **i18n**：UI 字串直接用 zh-TW，先集中在 `ui/Strings.kt` 的 `object Strings`。用語參考 KaTrain 的 tw.po，例如深度分析、選點平均、快速全盤分析、尋找其他選點、設定欲分析區域、重設分析、深度全盤分析、表現報告、切換持續分析、形勢判斷、直覺選點、最佳選點、目數損失、預估目差、勝率、計算深度。之後要多語系時再改用 Compose resources 的 `strings.xml`。
13. **About／版本**：保留 `AboutRequest`、`AboutDialog`、`installMacAboutHandler`。如果加了 macOS `MenuBar`，要確認 About 仍然由系統 App 選單處理，不要重複出現。
14. **授權**：在 repo 加上 `docs/programming/third_party_notices.md` 或 NOTICE，註明參考 KaTrain（MIT，Copyright 2020 Sander Land）。不複製 KaTrain 的圖片、音效或字型。

## 3. 分階段計畫與驗收條件

> 目標是一次完成 P0（Phase 1–4）。若時間或風險太高，至少要完成 Phase 1–2，而且每個 Phase 結束時 `./gradlew :composeApp:run` 都要能跑，build 不能壞掉。

### Phase 0：準備（不改功能）
- 加入 serialization plugin 與 json、coroutines 依賴；建立 `commonTest`（`kotlin("test")`）。
- **驗收**：`./gradlew :composeApp:desktopTest`（或 `allTests`）通過；`./gradlew :composeApp:run` 開得起來；`./gradlew :composeApp:wasmJsBrowserDistribution` 仍能 build（或依 Neo 的決定處理）。

### Phase 1：棋譜與棋盤（還不接引擎）
- `go/`：座標、BoardState、SGF parser/writer、GameTree；單元測試。
- `ReviewScreen`：中央棋盤（Canvas 繪製座標、棋子、最後一手、Next Moves 框）、底部導覽列（Pass、上一個或下一個失誤先停用、|◀ ◀◀ ◀ ●○ ▶ ▶▶ ▶|、滑桿、跳到第 N 手）、鍵盤（←→、Shift、⌘、Home/End、↑↓）。
- 檔案 → 開啟 SGF（⌘O）；最近開啟的檔案（可選）。
- 右側面板骨架：分頁「分析」、棋手卡（PB/PW、提子數）、資訊面板（貼目、規則、第幾手、SGF 註解）。
- **驗收**：能開啟一份有變化與中文註解的 19 路 SGF；提子顯示正確；用鍵盤和滑桿能走完整局；↑↓ 能切換變化；單元測試 ≥ 15 個並全部通過。

### Phase 2：引擎與即時分析
- `KataGoProcessEngine`、`KataGoPathDetector`、設定 JSON 檔、引擎設定對話框（路徑、瀏覽、自動偵測、測試引擎）。
- 持續分析（空白鍵切換），目前局面送 ponder，換手時 terminate。
- 棋盤：top-move hint（點損分級上色、`-0.1` / `52` 兩行文字、首選加青色外框、低 visits 縮小）、5 個開關（先完成 Next Moves、Show Dots、Top Moves）。
- 右側：勝率 `黑 52.5%`、預估目差 `白+0.3`、本手點損、候選手表（前 10 名）。
- 底部：引擎狀態燈（綠就緒、橘忙碌、紅錯誤、黃持續分析中）。
- **驗收**：在 Apple Silicon 上使用 Homebrew 安裝的 katago，打開 App 後 10 秒內（不含第一次 Metal 初始化）就看到候選手；快速按 → 連續換手時，舊的 query 有被 terminate（log 看得到）；拔掉模型路徑時會顯示中文錯誤，App 不會當掉；關閉視窗後 `pgrep katago` 找不到殘留的程序。

### Phase 3：整局分析與圖表
- 載入後自動用 fast 或一般 visits 分析整局（主線用 analyzeTurns 批次送，priority -100）。
- F2「深度全盤分析」對話框（visits 預設 2500、限定手數、僅分析失誤）。
- `ScoreGraph`：勝率（綠）與目差（藍）兩條線、目前手的紅點、點擊或拖曳跳轉、可收合；下方「勝率／點損」分頁。
- 上一個或下一個失誤按鈕啟用（門檻 3 目）；Show Dots 依點損著色。
- 手數圓點列。
- **驗收**：200 手的棋譜在 visits=100 下能完成整局分析並顯示進度（剩餘 query 數）；圖表與棋盤同步；n／Shift+n 會停在點損 ≥3 的那一手的前一手。

### Phase 4：分析選項與設定
- 頂列「分析選項」下拉選單，包含截圖上的全部項目。P0 先實作：深度分析（a）、重設分析（⌃H）、深度全盤分析（F2）、切換持續分析（space）、停止分析（Esc）。其他項目先停用並標示「（尚未支援）」。
- 「分析設定」對話框：max visits、fast visits、max time、wide root noise、ownership 開關、分析執行緒數與每局面搜尋執行緒數（改了就重新啟動引擎）。
- **驗收**：修改 max visits 後，深度分析的 visits 確實會增加（看 rootInfo.visits）；修改執行緒數後引擎重新啟動並繼續分析；設定在重新開啟 App 後還在。

### Phase 5（P1）
- Ownership 疊圖、Policy 熱圖、PV 滑鼠預覽、試下與變化樹、Equalize、Sweep、Alternatives、Region of interest、表現報告（F3）、存 SGF（含註解與 MA/SQ；KT 快取可選）、Notes 編輯。

### Phase 6（P2）
- 與 AI 對局、指導模式、計時器、AI 策略、Insert、Selfplay、Tsumego frame、human-like model。

## 4. 版號建議
- 目前是 `0.1.0`。Phase 1 合併時改成 **0.2.0**（`./configure.sh version 0.2.0`）。P0 全部完成（Phase 4）時改成 **0.3.0**，或依 Neo 的習慣改成 0.5.0。P1 完成時改成 0.4.0。
- 每次改版號時都要確認 About 的第二行與 `gradle.properties` 一致。

## 5. 風險
| 風險 | 對策 |
|---|---|
| 從 Finder 或 Dock 啟動時 PATH 裡沒有 `/opt/homebrew/bin` | 自動偵測時列出固定的候選路徑，KaTrain 也是這樣做的 |
| `analysis_example.cfg` 的 `logDir` 是相對路徑，打包後可能無法寫入 | 用 `-override-config logDir=...` 改掉，或設定 `ProcessBuilder.directory` |
| Intel Mac 只有 Eigen（CPU） | 預設 visits 降低，並在 UI 上提示 |
| stdout 太多，UI 卡頓 | 只在 IO 執行緒解析，合併後再更新 state；整局分析時不帶 `includeMovesOwnership` |
| 舊結果寫到新棋譜 | generation 計數器，加上換棋譜時送 `terminate_all` |
| 座標翻轉錯誤（KataGo 陣列從 A19 開始） | 統一用 y=0 在上方，並寫單元測試 |
| 中文 SGF 亂碼 | desktop 端偵測 CA 或嘗試多種編碼 |
| Compose Desktop 的 MenuBar 與 About handler 衝突 | 實測；About 交給系統 App 選單處理 |
| JDK 25 toolchain | Neo 的機器上要有 JDK 25，或由 Gradle toolchain 自動下載（推測） |
| 刪除 wasm 會讓 configure.sh 失效 | 先保留；要刪的話先問 Neo |
| KaTrain 素材授權 | 不複製 img、sounds、fonts，一律用 Canvas 繪製或 Material Icons |
