# 分析選項（Analysis Options）與設定參考

> 依據：Neo 提供的 KaTrain v1.15.0 截圖（`:Analysis Options` 下拉選單）、KaTrain 1.20.0 原始碼：`core/game.py analyze_extra`、`__main__.py shortcuts / _on_keyboard_down`、`gui/theme.py KEY_*`、`config.json`、`popups.kv`（已查證）。
> zh-TW 名稱取自 `katrain/i18n/locales/tw/LC_MESSAGES/katrain.po`（已查證）。若名稱標「建議」，表示是我們自己取的。

## 1. 「分析選項」下拉選單（照截圖的順序）

| # | 英文（截圖） | 快捷鍵（截圖） | zh-TW（tw.po） | KaTrain 動作 | 行為（已查證：`game.py analyze_extra`） | mykatago 優先序 |
|---|---|---|---|---|---|---|
| 1 | Deeper analysis | a | 深度分析 | `analyze-extra extra` | 對目前節點以 `visits = 已請求的 visits + max_visits` 再分析一次，priority 100，不設時間上限 | **P0** |
| 2 | Equalize visits of potential moves | s | 選點平均 | `analyze-extra equalize` | 必須等目前節點分析完成。把每個候選手補到「目前最多的 visits」：對每個候選手送一個 refine query（父局面＋該手） | P1 |
| 3 | Fast analysis of all moves | d | 快速全盤分析 | `analyze-extra sweep` | 對盤面上所有合法點（依 policy 排序）各用 `fast_visits`（25）分析，priority -10 | P1 |
| 4 | Find alternative moves | f | 尋找其他選點 | `analyze-extra alternative` | 用 `avoidMoves` 排除現有的所有候選手（untilDepth 1），重新搜尋，再對新選點做 fast refine | P1 |
| 5 | Set region of interest | g | 設定欲分析區域 | `select-box` | 在棋盤上拖曳出矩形；之後的分析用 avoidMoves 排除區域外所有點（B、W 兩方都排除） | P1 |
| 6 | Reset analysis | h（v1.15）；**1.20 改成 Ctrl+H**，避免跟 macOS ⌘H 衝突 | 重設分析 | `reset-analysis` | terminate 這個節點的 query，清空分析後重新分析 | **P0** |
| 7 | Insert moves | i | 插入棋子 | `insert-mode` | 在主線中間插入手順（插入模式時禁止導覽） | P2 |
| 8 | Fast playout to game end | l | 快速下完 | `selfplay-setup end` | 從目前局面讓 AI 用 fast visits 自我對弈到終局，結果做成分支 | P2 |
| 9 | Deeper full game analysis | F2 | 深度全盤分析 | `open_game_analysis_popup` | 開啟對話框：每手 visits（預設 **2500**）、☐ 限定手數範圍（從 0 到 999）、☐ 僅分析失誤（門檻 `eval_thresholds[-4]`=3 目）。priority 設為 -1,000,000，不設時間上限，不做期間回報 | **P0** |
| 10 | Performance Report | F3 | 表現報告 | `open_report_popup` | 雙方的準確度評分、平均損失目數、AI 首選一致率、前 5 名一致率、點損直方圖。可篩選「整局／布局 (<14%)／中盤 (14–40%)／官子 (>40%)」，百分比 × 盤面點數 = 手數 | P1 |
| 11 | Tsumego Frame | F10 | 死活題外框 | `open_tsumego_frame_popup` | 在局部死活題周圍自動補外框，讓 KataGo 能正確分析（`tsumego_frame.py`） | P2 |
| 12 | Toggle continuous analysis | space | 切換持續分析 | `toggle_continuous_analysis` | 開啟後，目前節點送出 ponder query（maxVisits 10M，每 0.25 秒回報）。換節點時自動 terminate 舊的再送新的。Shift+space 時不顯示訊息 | **P0** |
| 13 | Force AI Move | enter | 要求AI產生下一手 | `ai-move` | 依 AI 設定下一手 | P2 |

其他快捷鍵（已查證：`theme.py`、`__main__.py`）：
- Esc：停止所有分析（`analyze-extra stop`，停止 ponder 並 terminate 所有 query）。
- q/w/e/r/t：五個顯示開關。
- p：Pass。
- b：回到上一個分岔點。Shift+b：回主線。
- ↑/↓：切換分支。
- m：顯示手數。k：顯示座標。
- macOS 上，KaTrain 把 ⌘ 視同 Ctrl（`ctrl_pressed = "ctrl" in modifiers or ("meta" in modifiers and platform=="macosx")`）：⌘L 載入、⌘S 存檔、⌘D 另存、⌘N 新局、⌘C／⌘V 複製或貼上 SGF。
- F5 計時器、F6 指導設定、F7 AI 設定、F8 一般與引擎設定、F9 參與貢獻。

**mykatago 快捷鍵建議**（依 macOS 慣例，屬建議）：⌘O 開啟 SGF、⌘S 存檔、⌘⇧S 另存、⌘, 設定；保留 KaTrain 的單鍵快捷鍵（a/s/d/f/g、space、←/→、Home/End、n）；Reset 用 ⌃H；F2 和 F3 照舊（Mac 鍵盤需要按 fn，建議選單也放一份）。

## 2. 「分析設定」對話框（mykatago 的 P0 對話框，整合 KaTrain 的 engine 設定與 F2）

| 欄位 | KaTrain 鍵／預設（已查證：`config.json`） | zh-TW 標籤（tw.po） | 說明 |
|---|---|---|---|
| 每手最大 visits | `engine/max_visits` = **500** | 分析模式中，每手棋最大運算步數 | 一般分析與 Deeper 的增量 |
| 快速分析 visits | `engine/fast_visits` = **25** | 快速分析模式中，每手棋最大運算步數 | 用於 sweep、alternatives、載入時的快速分析 |
| 分析時間上限（秒） | `engine/max_time` = **8.0** | 分析時間上限 | 寫入 overrideSettings 的 `maxTime`；ponder、Deeper、F2 不套用 |
| Wide root noise | `engine/wide_root_noise` = **0.04** | 寬根雜訊（增加了納入考慮的選點多樣性） | 建議 0.02–0.1 |
| 啟用形勢判斷資料 | `engine/_enable_ownership` = true | — | 決定 query 的 `includeOwnership` |
| 載入時快速分析 | `general/load_fast_analysis` = false | 快速分析 | 載入 SGF 後，整局先用 fast_visits 分析一遍 |
| 載入後回到開頭 | `general/load_sgf_rewind` = true | — | |
| 執行緒（**KaTrain 沒有這個 UI**） | cfg：`numAnalysisThreads`、`numSearchThreadsPerAnalysisThread`（官方 analysis_example.cfg 的值是 2 和 16；KaTrain 內附 cfg 寫 `numAnalysisThreads=12`、`numSearchThreads=8`） | 建議：分析執行緒數、每局面搜尋執行緒數 | Neo 要求要有。用 `-override-config numAnalysisThreads=N,numSearchThreadsPerAnalysisThread=M` 傳入，**修改後要重新啟動引擎**（推測：這兩個設定無法透過每個 query 的 overrideSettings 修改，需要實測） |
| 期間回報間隔 | 常數 `REPORT_DT=1`、`PONDERING_REPORT_DT=0.25`（`constants.py`） | 建議：分析更新頻率 | 可以做成進階設定 |

F2「深度全盤分析」對話框（已查證：`popups.kv ReAnalyzeGamePopup`）：
- 每步棋的計算深度：預設 2500。
- ☐ 限定手數：從 [0] 到 [999]。
- ☐ 僅分析失誤：只分析自己或子節點點損 > 3 的節點。
- [分析] 按鈕。

## 3. 「指導／分析設定」（KaTrain F6，mykatago P1）

| 欄位 | 預設（已查證：`config.json trainer`） | zh-TW |
|---|---|---|
| 點損門檻 | `eval_thresholds` = [12, 6, 3, 1.5, 0.5, 0] | 目數損失下限 |
| 各級顯示點 | `show_dots` = 全部 true | 顯示選點 |
| 各級寫入 SGF 註解 | `save_feedback` = [T,T,T,T,F,F] | 存入SGF |
| 顯示最後幾手的點 | `eval_on_show_last` = 3 | 顯示最後幾手棋的選點 |
| 低 visits 門檻 | `low_visits` = 25 | — |
| hint 第一行 | `top_moves_show` = `top_move_delta_score` | 目差變化 |
| hint 第二行 | `top_moves_show_secondary` = `top_move_visits` | 計算深度 |
| 可選值 | `top_move_score`（目差）、`top_move_delta_score`（目差變化）、`top_move_winrate`（勝率）、`top_move_delta_winrate`（勝率波動）、`top_move_visits`（計算深度）、`top_move_nothing` | |
| 接近 0 時顯示兩位小數 | `extra_precision` = false | 損失目數接近零時顯示兩位小數 |
| 快取分析到 SGF | `save_analysis` = false | 儲存分析到SGF中 |
| 存 SGF 標記 | `save_marks` = false | — |
| 顯示 AI 的點與註解 | `eval_show_ai` = true | 顯示AI的選點/SGF評論 |
| 主題 | `theme:normal` / `theme:red-green-colourblind` | — |

## 4. 「引擎設定」（KaTrain F8 的 engine 區塊，mykatago P0）

| 欄位 | KaTrain | mykatago 在 macOS 的預設值（Homebrew，已查證 formula；路徑屬推測，需在 Neo 的 Mac 上執行 `brew --prefix` 確認） |
|---|---|---|
| KataGo 執行檔 | `engine/katago`，留空時 macOS 依序找 `katrain/KataGo/katago-osx`、PATH 中的 `katago`，最後找 `/opt/homebrew/bin/`（已查證：`engine.py get_engine_path` 第 80–104 行） | Apple Silicon：`/opt/homebrew/bin/katago`；Intel：`/usr/local/bin/katago`。**注意：從 Finder 或 Dock 啟動的 .app，PATH 裡不會有 `/opt/homebrew/bin`**，所以要像 KaTrain 一樣把它加進候選路徑 |
| 模型 | `engine/model`（內附 b10c384h6nbttflrs） | Homebrew 會安裝到 `$(brew --prefix)/share/katago/`：`kata1-b18c384nbt-s9996604416-d4316597426.bin.gz`（**推薦**）、`g170e-b20c256x2-s5303129600-d1228401921.bin.gz`、`g170-b40c256x2-s5095420928-d1229425124.bin.gz`（已查證：formula 的 resources 與 `pkgshare.install`） |
| 設定檔 | `engine/config` | `$(brew --prefix)/share/katago/configs/analysis_example.cfg`（formula 有 `pkgshare.install "cpp/configs"`，已查證）。建議複製一份到 `~/Library/Application Support/mykatago/analysis.cfg` 再修改 |
| Human-like 模型 | `engine/humanlike_model` | P2 |
| 覆寫指令 | `engine/altcommand`（不經 shell 直接執行） | P2 |
| 遠端 WebSocket | `engine/remote_url` | 不做 |

UI 建議：每個路徑欄位旁邊放 [瀏覽…]（AWT `FileDialog`）、[自動偵測]（掃描上表的候選路徑），以及檢查結果（✓ 檔案存在／✗ 找不到）。按 [測試引擎] 時送 `query_version`，顯示 KataGo 版本。

模型下載（已查證：`popups.py` 第 482–498 行的 URL，以及 katagotraining.org/networks/ 頁面可以連上）：
- 最新：`https://katagotraining.org/api/networks/newest_training/`；最強：`https://katagotraining.org/api/networks/get_strongest/`（這是 API，回傳的是 JSON 還是轉址，**尚未確認**）。
- 18b 推薦：`https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz`（Homebrew 用的是同一個）。
- 小型 transformer：`https://github.com/lightvector/KataGo/releases/download/v1.17.1/b10c384h6nbttflrs.bin.gz`。
- mykatago 第一版**不做下載功能**，只在設定頁放「開啟 katagotraining.org/networks」的連結（`Desktop.browse`）。

## 5. 後端（backend）說明（已查證：Homebrew formula `install`）

- Apple Silicon：Homebrew 用 `-DUSE_BACKEND=METAL` 編譯，使用 GPU 與 Neural Engine 加速。
- Intel Mac：用 `-DUSE_BACKEND=EIGEN` 編譯，只用 CPU，**速度慢**，建議把 max_visits 調低到 100–200（推測）。
- 想要 OpenCL 版本的話，需要自己編譯或下載官方 release，不是 Homebrew 的預設。
- 第一次啟動 Metal 版時可能會花一點時間初始化。stderr 會印出相關訊息，UI 要顯示「引擎啟動中」。
