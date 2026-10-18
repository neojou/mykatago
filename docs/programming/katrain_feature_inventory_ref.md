# KaTrain 功能盤點（供 mykatago 移植參考）

> 調查日期：2026-10-18（Asia/Taipei）。
> 來源：`https://github.com/sanderland/katrain` 的 master，commit `f4981cf`（2026-08-24 13:24 +0200，換算台北時間 2026-08-24 19:24）。`pyproject.toml` 與 `katrain/core/constants.py` 標示的版本是 **1.20.0**。
> Neo 提供的截圖是 **KaTrain v1.15.0**，兩版有差異的地方會另外註明。
> 標記：**已查證** = 讀過原始碼或官方文件；**推測** = 根據經驗推論，實作前要再確認。

## 1. 授權與素材（已查證：`LICENSE`、`pyproject.toml`）

- 程式碼採 **MIT**（`pyproject.toml` 寫 `license = "MIT"`，`LICENSE` 的主體條款是 MIT，Copyright 2020 Sander Land）。概念、演算法與參數可以參考，也可以改寫成 Kotlin。若整段翻譯程式碼，請在 mykatago 的 NOTICE 或文件裡保留 KaTrain 的 MIT 版權聲明。
- **不可直接複製**的素材：
  - `katrain/img/*.png`（導覽箭頭、Equalize、Rotate 等選單圖示）。`LICENSE` 說明這些圖示來自 flaticon，「used with permission」，作者包括 Freepik、bqlqn 等。這是授權給 KaTrain 使用，不代表可以再散布，所以**不要複製**，改用 Material Icons 或自己在 Canvas 上畫。
  - `katrain/img/board.png`、`B_stone.png`、`W_stone.png`、`topmove.png`、`dot.png`、`inner.png`、`graph_bg.png`：來源沒有寫明（推測屬於 MIT 的「其他內容」），但為了安全，**仍建議用 Canvas 自己畫**，木紋可用漸層代替。
  - `katrain/sounds/*.wav`：來源沒有寫明，**不要複製**。復盤用不到音效。
  - DIGITAL-7 字型：只限非商業使用，**不要用**。Noto 字型屬於 SIL OFL，mykatago 已經自帶 Noto Sans TC，不需要再引入。
  - `katrain/KataGo/` 內附的 KataGo 執行檔與 DLL 只供 Windows／Linux 使用。macOS 版請使用者自行用 Homebrew 安裝。
  - `katrain/models/b10c384h6nbttflrs.bin.gz` 是 KataGo 的模型。**不要放進 repo**，讓使用者自己下載。

## 2. 原始碼地圖（已查證）

| 檔案 | 職責 | 對 mykatago 的意義 |
|---|---|---|
| `katrain/core/engine.py` | `KataGoEngine`：啟動 `katago analysis`，用 3 條 thread（stdin 寫入、stdout 讀取、stderr）收發 JSON lines，以 id 對應回呼函式，處理 terminate、pondering、priority | 對應到 desktopMain 的 `KataGoAnalysisEngine` |
| `katrain/core/game_node.py` | `GameNode`：快取每個節點的 analysis，計算 `candidate_moves`、`points_lost`、`policy_ranking`，產生 SGF 註解，`KT` 屬性用 gzip+base64 存分析結果 | 對應到 commonMain 的 `GameNode` 與 `NodeAnalysis` |
| `katrain/core/game.py` | `BaseGame`：提子與禁著規則（`_validate_move_and_update_chains`）、undo/redo、stop_on_mistake、`write_sgf`。`Game`：`analyze_all_nodes`、`analyze_extra`（extra、equalize、sweep、alternative、game、ponder、stop）、`selfplay`、`set_insert_mode`、`set_region_of_interest` | 規則與導覽放在 commonMain，分析動作放在 `AnalysisController` |
| `pysgf`（外部套件，`pysgf>=1.0.0,<2`） | SGF／GIB／NGF 解析。KaTrain 1.20 已經把舊的 `katrain/core/sgf_parser.py` 移到這個套件，`tests/test_parser.py` 開頭有說明 | mykatago 要自己寫 Kotlin 版 SGF parser |
| `katrain/core/ai.py` | AI 策略與 `game_report()`（第 110 行起，計算 accuracy 與點損直方圖） | game_report 屬於 P1；AI 策略屬於 P2 |
| `katrain/core/constants.py` | priority、REPORT_DT=1、PONDERING_REPORT_DT=0.25、top-move 顯示選項、AI 常數 | 直接參考常數值 |
| `katrain/core/utils.py` | `var_to_grid`（ownership/policy 一維陣列轉二維並翻轉 y）、`evaluation_class`、`format_visits`、`pack_floats`（float16） | 照搬邏輯 |
| `katrain/config.json` | 預設設定：engine、trainer（門檻、顏色開關）、ui_state | 預設值參考 |
| `katrain/gui/badukpan.py` | 棋盤繪製：座標、棋子、評估點、top-move hint、policy 熱圖、ownership、PV 動畫；`BadukPanControls`（底部導覽） | Compose Canvas 的繪製規格 |
| `katrain/gui.kv` | Kivy 版面配置：底部導覽列（第 216–299 行）、右側面板、分析下拉選單 | 版面參考 |
| `katrain/gui/widgets/graph.py` | 勝率與目差折線圖，自動調整刻度，點擊或拖曳可跳到該手 | 圖表規格 |
| `katrain/gui/controlspanel.py`、`gui/widgets/panels.py` | Play／Analysis 分頁、可收合面板、狀態列 | 右側面板 |
| `katrain/gui/popups.py`、`katrain/popups.kv` | 設定、ReAnalyze（F2）、GameReport（F3）、引擎修復、模型下載 URL（第 482–498 行） | 對話框規格 |
| `katrain/gui/theme.py` | 所有顏色、尺寸與快捷鍵（`KEY_*`） | 顏色與快捷鍵 |
| `katrain/__main__.py` | 動作分派（`_do_*`）、`load_sgf_file`、鍵盤處理 `_on_keyboard_down`（第 736 行起） | 動作清單 |
| `katrain/i18n/locales/tw/LC_MESSAGES/katrain.po` | **繁體中文翻譯** | zh-TW 用語可以直接參考，例如「深度分析」「選點平均」「形勢判斷」 |

## 3. 功能清單與優先序（復盤用途）

### P0：必須有（MVP）
| 功能 | KaTrain 位置 | 備註 |
|---|---|---|
| 載入 SGF（Load Game，Ctrl/⌘+L） | `__main__.py: load_sgf_file`、`_do_analyze_sgf_popup` | `general/load_sgf_rewind=true` 會在載入後回到第 0 手。`load_fast_analysis` 會用 fast_visits 分析 |
| 棋盤繪製（19 路，座標 A–T 跳過 I，1–19） | `badukpan.py: draw_coordinates`、`Move.GTP_COORD` | 也要支援 9 路與 13 路 |
| 導覽：上一手、下一手、±10 手、第一手、最後一手、上一個或下一個失誤、跳到第 N 手、滑桿 | `gui.kv` 第 216–276 行、`game.py: undo/redo` | 失誤門檻是 `eval_thresholds[-4]` = **3 目**（`__main__.py: _do_find_mistake`） |
| 鍵盤：←/z 上一手、→/x 下一手、Shift 一次 10 手、Ctrl/⌘ 到頭或到尾、Home、End、n 或 Shift+n 跳失誤、↑/↓ 切換分支 | `theme.py KEY_*`、`__main__.py` 第 783–794 行 | |
| 目前局面持續分析（ponder，空白鍵） | `game.py analyze_extra("ponder")`、`engine.py` 的 PONDER_KEY | 送出 maxVisits=10,000,000、每 0.25 秒回報一次。換局面時 terminate 舊的 query |
| 右側面板：勝率、目差、本手損失、候選手表 | `controlspanel.py`、`game_node.py candidate_moves` | |
| 整局勝率與目差圖，點擊可跳轉 | `widgets/graph.py` | |
| 整局分析（載入後自動分析；F2「深度全盤分析」可指定 visits、手數範圍、只分析失誤） | `game.py analyze_all_nodes`、`analyze_extra("game")`、`popups.kv ReAnalyzeGamePopup`（預設 2500 visits） | |
| 分析選項選單與設定對話框 | 見 `analysis_options_ref.md` | |
| 引擎設定：katago、model、config 路徑，max_visits、fast_visits、max_time、wide_root_noise | `config.json engine`、`popups.py ConfigPopup` | macOS 的預設路徑見 `mykatago_migration_plan_ref.md` |
| 棋盤上的 top-move hint（依點損上色，文字顯示「點損／visits」） | `badukpan.py draw_hover_contents` 第 892 行起 | |

### P1：重要
| 功能 | KaTrain 位置 |
|---|---|
| 已下棋子上的評估點（Show Dots，最近 N 手，預設 3） | `badukpan.py draw_board_contents`、`trainer/eval_on_show_last` |
| 表現報告（F3）：每位棋手的 accuracy、平均點損、AI 首選一致率、點損直方圖，並可篩選布局、中盤、官子 | `ai.py game_report`；`popups.kv` 的 depth filter：(0,0.14)、(0.14,0.4)、(0.4,∞) × 盤面點數 |
| 變化圖與試下：點棋盤會建立分支，↑/↓ 切換分支，有 move tree | `game.py play`、`widgets/movetree.py` |
| Ownership 疊圖（形勢判斷） | `badukpan.py draw_territory`、`Theme.OWNERSHIP_*` |
| Policy 熱圖（直覺選點） | `badukpan.py` 第 695–760 行 |
| PV 預覽：滑鼠移到候選手上就顯示後續變化 | `badukpan.py active_pv_moves`、`anim_pv_*` |
| 存 SGF，附分析註解或 KT 快取 | `game_node.py sgf_properties`、`game.py write_sgf` |
| 額外分析：Deeper（a）、Equalize（s）、Sweep（d）、Alternatives（f）、Region of interest（g）、Reset（h） | `game.py analyze_extra` |
| Notes 與 Details 分頁 | `game_node.py comment()` |

### P2：之後再做
- 與 AI 對局：Play 分頁、Force AI Move（Enter）、AI 策略（`ai.py`，有十幾種）。
- 指導模式（teaching，自動悔棋）：`game.py analyze_undo`、`trainer/num_undo_prompts`。
- 計時器（F5）、Insert moves（i）、Fast playout to game end（l，`selfplay`）、Tsumego Frame（F10，`tsumego_frame.py`）。
- Human-like model（`-human-model`）、遠端引擎（`remote_engine.py`）、分散式訓練貢獻（`contribute_engine.py`）。
- 主題切換，例如紅綠色盲配色 `theme:red-green-colourblind`。

## 4. KaTrain 的核心公式（已查證：`game_node.py`、`ai.py`）

所有分析都使用 `reportAnalysisWinratesAs = BLACK`，也就是從黑方角度回報（`engine.py` 的 override_settings）。

- `player_sign(B)=+1`、`player_sign(W)=-1`。
- 候選手的點損：`pointsLost = sign(next_player) * (root.scoreLead - move.scoreLead)`
- 候選手的勝率損失：`winrateLost = sign(next_player) * (root.winrate - move.winrate)`
- 實際下出那一手的點損：`node.points_lost = sign(node.player) * (parent.scoreLead - node.scoreLead)`，用的是兩個節點的 rootInfo。
- 候選手排序：依 `(order, pointsLost)`。`order` 是 KataGo 給的排名，舊資料的 order 設為 999。
- 點損分級：`evaluation_class(pl, [12,6,3,1.5,0.5,0])` 傳回 0..5（0 代表最差）。
- accuracy：`100 * 0.75 ** weighted_ptloss`，權重依局面複雜度計算，詳見 `ai.py` 第 137–160 行。
