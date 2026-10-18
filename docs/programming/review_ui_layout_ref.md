# 復盤 UI 版面參考（仿 KaTrain Analysis 模式）

> 依據：Neo 提供的 KaTrain **v1.15.0** macOS 截圖（Analysis 模式；截圖本身不放在這份文件中，以下是文字描述），以及 KaTrain 1.20.0 原始碼的 `katrain/gui.kv`、`gui/badukpan.py`、`gui/theme.py`、`gui/widgets/graph.py`（已查證）。
> 從截圖目測的顏色一律標「約略」。從 `theme.py` 換算出來的 hex 值標「已查證」。

## 1. 整體版面（ASCII）

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│ ≡ │ ☑下一手 ☑顯示選點 ☑最佳選點 ☐直覺選點 ☐形勢判斷 │ ▾分析選項 (:Analysis Options)              │ ← 頂列
├──────────────────────────────────────────────────────────────┬───────────────────────────┤
│   A B C D E F G H J K L M N O P Q R S T                       │ [ 下棋 Play ][▌分析 Analysis▐] │ ← 分頁（選中＝黃色）
│19 ┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼ 19                     │ ┌ Human ─────┐ ┌ Human ─────┐ │
│18 ┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼─┼ 18                     │ │●Normal Game│ │○Normal Game│ │ ← 棋手卡（提子數 0）
│ .                                                             │ │  提子 0     │ │  提子 0     │ │
│ .        ◯-0.1     (候選手：彩色圓 + 「點損/visits」)             │ └────────────┘ └────────────┘ │
│ .        / 52       ◯+0.0                                      │ (3)(4)(5)(6)(7)(8)(9)(10)(11)(12)│ ← 手數圓點列
│ .                   /152                                       │ ┌ 勝率 / 目差 圖 ──────────▸┐ │
│ .        ●•  ○•  (棋子上的評估點)                                 │ │ 綠線=勝率  藍線=目差        │ │ ← 可收合（▸）
│  1 ┼─┼─ ... ─┼ 1                                               │ └──────────────────────────┘ │
│   A B C D E F G H J K L M N O P Q R S T                       │ [勝率 Win Rate][點損 Point Loss]│
│                                                               │ 勝率      W 52.5%            │
│                                                               │ 預估目差  W+0.3               │ ← 統計面板 ▸
│                                                               │ 目數損失  …                   │
│                                                               │ [詳細 Details][註記 Notes]  ▸ │
│                                                               │ 貼目: 6.5                     │
│                                                               │ 規則: 日本規則 (Japanese)     │ ← 資訊面板
├──────────────────────────────────────────────────────────────┴───────────────────────────┤
│ [Pass]   ⟸(紅) |◀  ◀◀  ◀  (●○)  ▶  ▶▶  ▶|  ⟹(紅)  ⟳                          ● (綠=引擎就緒)│ ← 底部導覽列
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

mykatago 的差異與補充（建議）：
- 頂列最左邊的 **≡（漢堡選單）** 在 KaTrain 裡會開啟側邊抽屜，包含新對局、存檔、載入、設定。mykatago 改用 **macOS 原生選單列**（Compose `Window { MenuBar { … } }`），讓 ⌘O 等快捷鍵符合 Mac 習慣，App 內的 ≡ 按鈕可保留成同樣的選單。**現有的 About 必須保留**（`AboutRequest` 與 macOS App 選單的 About 共用同一個對話框）。
- 底部導覽列加上 **移動滑桿**（KaTrain 沒有，Neo 要求的）與 **「跳到第 N 手」輸入框**。建議放在導覽按鈕下方再加一列，或放在按鈕列右側。
- 右側面板寬度：KaTrain 的 `RIGHT_PANEL_ASPECT_RATIO = 0.4`（寬／高，已查證）。底部列高度 `CONTROLS_PANEL_MIN_HEIGHT = 50`、`MAX = 75` dp（已查證）。
- 棋盤維持正方形，置中，用 `Modifier.aspectRatio(1f)`。視窗縮放時，棋盤取 min(可用寬, 可用高)。

## 2. 頂列（截圖已確認的項目）

| 順序 | 元件 | KaTrain 鍵 | 預設（截圖） | zh-TW（KaTrain tw.po，已查證） | 快捷鍵（theme.py，已查證） |
|---|---|---|---|---|---|
| 0 | ≡ 漢堡選單 | `hamburger.png` | — | — | — |
| 1 | Next Moves | `analysis:nextmoves`，`show_children` | ☑ | 下一手 | q |
| 2 | Show Dots | `analysis:dots`，`eval` | ☑ | 顯示選點 | w |
| 3 | Top Moves | `analysis:topmoves`，`hints` | ☑ | 最佳選點 | e |
| 4 | Policy Moves | `analysis:policy`，`policy` | ☐ | 直覺選點 | r |
| 5 | Expected Territory | `analysis:territory`，`ownership` | ☐ | 形勢判斷 | t |
| 6 | ▾ Analysis Options（下拉選單） | `btn:Analyze` | — | 分析選項 | — |

- Policy Moves 打開時，hint 會換成 policy 熱圖，兩者互斥（`badukpan.py` 第 909–913 行：`hints.active and not policy.active`）。
- 下拉選單的項目見 `analysis_options_ref.md`。

## 3. 中央棋盤（繪製規格）

| 元素 | 規格 | 來源 |
|---|---|---|
| 盤面底色 | `BOARD_COLOR` = **#D9AD66**（已查證：[0.85,0.68,0.40]）。KaTrain 用木紋貼圖 `board.png`，**不要複製**，用純色或漸層 | theme.py |
| 格線 | 黑色 `LINE_COLOR`。星位大小 `STARPOINT_SIZE=0.1` 格 | theme.py |
| 座標 | 上下標 A–T（**跳過 I**），左右標 1–19（1 在下方）。可用 k 鍵切換顯示 | badukpan.py `draw_coordinates`；`KEY_TOGGLE_COORDINATES="k"` |
| 棋子 | 半徑 = 格距 × `STONE_SIZE=0.505`。黑 #0D0D0D、白 #F2F2F2（已查證 BLACK/WHITE）。可加簡單的陰影與漸層 | theme.py |
| 最後一手 | 棋子中央畫一個小圓圈（KaTrain 用 `inner.png`） | theme.py `LAST_MOVE_TEXTURE` |
| **候選手 hint** | 在空點上畫彩色實心圓：半徑 = 棋子半徑 × `HINT_SCALE=0.98`，alpha `HINTS_ALPHA=0.8`。顏色依 `pointsLost` 分級（見 §6）。先畫一個盤面色的圓蓋掉格線，再畫彩色圓 | badukpan.py 第 935–1014 行 |
| hint 文字 | 兩行：第一行 `top_moves_show`（預設 `top_move_delta_score`，例如 **`-0.1`**、**`+0.0`**），第二行 `top_moves_show_secondary`（預設 `top_move_visits`，例如 **`52`**、**`152`**）。截圖上看起來像「-0.1 / 52」。字色黑 `HINT_TEXT_COLOR`；第一行字高約格距的 1/3，第二行約 1/3.33 | badukpan.py；config.json `trainer/top_moves_show*` |
| 點損格式 | `f"{-pointsLost:+.1f}"`，所以最佳手顯示 `+0.0`。`extra_precision` 開啟時，接近 0 會顯示兩位小數 | badukpan.py `format_loss` |
| visits 格式 | <1000 直接顯示；<1e5 顯示 `1.2k`；<1e6 顯示 `123k`；更多顯示 `1M` | utils.py `format_visits` |
| 低 visits 的手 | `visits < trainer/low_visits(25)`、不是 order 0、也不是已下過的子節點時：半徑縮成 0.7 倍、alpha 0.6、**不顯示文字** | badukpan.py 第 942–949 行 |
| 引擎首選 | 外圍加一圈 `TOP_MOVE_BORDER_COLOR` **#0AC8FA**（已查證，青藍色） | theme.py |
| Next Moves | 棋譜中下一手（子節點）的位置畫空心圓框，黑方用淺灰、白方用灰（`NEXT_MOVE_DASH_CONTRAST_COLORS`） | badukpan.py 第 1029–1048 行 |
| Show Dots | 最近 `eval_on_show_last=3` 手的棋子中央畫評估點，顏色依該手的 points_lost 分級，大小在 `EVAL_DOT_MIN_SIZE 0.25`～`MAX 0.5` 之間 | badukpan.py 第 642–690 行 |
| Policy 熱圖 | 每個合法點依 policy 值畫透明色塊（`POLICY_ALPHA=0.5`），policy 高於門檻時顯示百分比文字；最高的那點用 TOP_MOVE_BORDER 色 | badukpan.py 第 709–760 行 |
| 形勢判斷 | 空點畫半透明方塊：黑方 `[0,0,0.10,0.75]`、白方 `[0.92,0.92,1.0,0.8]`，`OWNERSHIP_GAMMA=1.33`。棋子依 ownership 降低不透明度（`STONE_MIN_ALPHA=0.85`）；可能被吃掉的子畫小方塊標記（`STONE_MARKS="weak"`） | theme.py |
| PV 預覽 | 滑鼠移到 hint 上時，以半透明棋子加數字顯示 PV（`GHOST_ALPHA=0.6`、`anim_pv_time 0.5`） | badukpan.py |
| 旋轉 | 底部 ⟳ 鍵可以旋轉棋盤（`rotate_gridpos`） | P2 |

## 4. 底部導覽列（截圖與 `gui.kv` 第 216–299 行，已查證）

| 位置 | 元件 | 動作 | 鍵盤 |
|---|---|---|---|
| 左（約 15%） | **Pass** 按鈕 | `play(None)`（復盤時就是試下 pass） | p |
| 中 1 | 紅色 ⟸「上一個失誤」 | `find-mistake undo`；顏色 `MISTAKE_BUTTON_COLOR` **#C90F0F**（已查證） | Shift+n |
| 中 2 | \|◀ 第一手 | `undo 9999` | Home、⌘/Ctrl+← |
| 中 3 | ◀◀ 後退 10 手 | `undo 10` | Shift+← |
| 中 4 | ◀ 後退 1 手 | `undo 1` | ←、z |
| 中 5 | ●○ 黑白雙圓 | 標示輪到誰下（`CircleWithText`，B/W 兩顆，目前輪到的那顆會亮起或顯示文字）。截圖上看似「輪到誰／失誤篩選」；**原始碼只確認是輪次指示**（推測：不是篩選器） | — |
| 中 6 | ▶ 前進 1 手 | `redo 1` | →、x |
| 中 7 | ▶▶ 前進 10 手 | `redo 10` | Shift+→ |
| 中 8 | ▶\| 最後一手 | `redo 9999` | End、⌘/Ctrl+→ |
| 中 9 | 紅色 ⟹「下一個失誤」 | `find-mistake redo` | n |
| 中 10 | ⟳ 旋轉 | `rotate` | — |
| 右（約 85%） | **引擎狀態燈** | 綠 = 就緒 `ENGINE_READY_COLOR`（#1E9600，與最佳等級同色）；橘 = 忙碌（#E6661A）；紅 = 掛掉（#CC0000）；持續分析時是黃色旋轉的半圓（`ENGINE_PONDERING_COLOR`）。尚待處理的 query ≥3 時，燈上顯示數字 | — |
| **mykatago 新增** | 移動滑桿（0..主線長度）＋「第 [ N ] 手 / 共 M 手」輸入框 | 跳到第 N 手 | ⌘G 開啟跳轉（建議） |

↑/↓ 切換分支（`KEY_NAV_BRANCH_UP/DOWN`），Shift+B 回主線。

## 5. 右側面板（截圖 + `controlspanel.py`、`panels.py`、`config.json ui_state.analyze`）

由上到下：
1. **分頁**「下棋 Play」/「分析 Analysis」：選中的分頁用黃色 `PLAY_ANALYZE_TAB_COLOR`（YELLOW #CCCC1A，已查證）標示。截圖中 Analysis 被選中。mykatago 第一版**只做「分析」**，「下棋」分頁可以先不做，或做成停用的按鈕（P2）。
2. **棋手卡**（兩張並排）：截圖顯示「Human / Normal Game」對「Human / Normal Game」，提子數都是 0。mykatago 改顯示 SGF 的 **PB/PW 名稱、BR/WR 段位、提子數**，並標示輪到誰（亮框）。
3. **手數圓點列**：一排圓點，裡面有數字（截圖中是 3..12），代表最近幾手，點擊可以跳過去。顏色依該手的點損分級（推測：截圖上看似如此；原始碼中對應的是 move tree 或歷史列，實作前再確認）。mykatago 可以用一條橫向捲動的 LazyRow 來做。
4. **圖表面板**（可收合，右上角 ▸）：
   - 勝率線：綠色 `WINRATE_COLOR` GREEN #1ACC1A；標記點 #0DB20D（已查證）。
   - 目差線：藍色 `SCORE_COLOR` BLUE #4CB2E6；標記點 #3399CC（已查證）。
   - 點損模式：黃色 `POINTLOSS_COLOR` #CCCC1A。
   - Y 軸刻度：目差以 5 目為單位、勝率以 10% 為單位，依整局的最大值自動調整，對稱於 0／50%（`graph.py` 第 130–139 行）。
   - X 軸：每手一格，最少 15 格的寬度。目前手用紅色圓點 `GRAPH_DOT_COLOR` #D94D4D 標示。**點擊或拖曳可以跳到該手**（`on_touch_down/move`）。
   - 下方分頁：「勝率 Win Rate」/「點損 Point Loss」（截圖中有這兩個分頁）。
5. **統計面板**（可收合）：
   - 勝率：`W 52.5%`，格式 `{B|W} {max(wr,1-wr):.1%}`，中文顯示「黑 52.5%」（`format_winrate`）。
   - 預估目差：`W+0.3`，格式 `{B|W}+{|score|:.1f}`（`format_score`）。
   - 目數損失：本手（剛下的那手）的 `points_lost`。若為負值，顯示「目數增加」。
6. **資訊面板**：分頁「詳細 Details」/「註記 Notes」（截圖如此）。Details 顯示 `comment()` 的內容：第幾手、座標、目差、勝率、點損、預測首選、PV、policy 排名。在 root 時顯示「貼目: 6.5」「規則: Japanese」。Notes 是可編輯的文字，對應 SGF 的 C 屬性。

## 6. 點損分級與顏色（已查證：`config.json trainer/eval_thresholds`、`theme.py EVAL_COLORS["theme:normal"]`）

`eval_thresholds = [12, 6, 3, 1.5, 0.5, 0]`；`evaluation_class(pl)`：從 i=0 開始，當 `pl < thresholds[i]` 且 i < 5 時 i++。

| class | 點損範圍 | 顏色（KaTrain RGB → hex） | 意義（建議的中文名稱） |
|---|---|---|---|
| 0 | ≥ 12 | [0.447,0.129,0.42] → **#72216B** 紫 | 大惡手 |
| 1 | 6 – 12 | [0.8,0,0] → **#CC0000** 紅 | 惡手 |
| 2 | 3 – 6 | [0.9,0.4,0.1] → **#E6661A** 橘 | 失誤（「跳到失誤」用的門檻 3 目就從這裡開始） |
| 3 | 1.5 – 3 | [0.95,0.95,0] → **#F2F200** 黃 | 緩手 |
| 4 | 0.5 – 1.5 | [0.67,0.9,0.18] → **#ABE62E** 黃綠 | 小損 |
| 5 | < 0.5 | [0.117,0.588,0] → **#1E9600** 綠 | 好手、最佳 |

截圖描述的「綠／黃／紅候選手圓」就是依照這張表上色。色盲主題（`theme:red-green-colourblind`）屬於 P2。

## 7. 整體配色

| 用途 | 已查證（theme.py） | 截圖目測（約略） |
|---|---|---|
| 背景 | `BACKGROUND_COLOR` [36,48,62] = **#24303E** | 深海軍藍／石板色，約 #2B3A4A |
| 面板底 | `BOX_BACKGROUND_COLOR` [46,65,88] = **#2E4158** | — |
| 較亮底（選中、邊框） | `LIGHTER_BACKGROUND_COLOR` [64,85,110] = **#40556E** | — |
| 文字 | WHITE #F2F2F2 | 白 |
| 選中分頁與強調色 | YELLOW #CCCC1A | 黃色 |
| 選單快捷鍵文字 | LIGHT_GREY #B3B3B3 | — |

建議：在 `Theme.kt` 新增 `darkColorScheme(background=0xFF24303E, surface=0xFF2E4158, surfaceVariant=0xFF40556E, primary=0xFFCCCC1A, onBackground=0xFFF2F2F2…)`，並保留現有的 `appFontFamily()`（Noto Sans TC）。

## 8. Compose 元件對應（推測：屬於設計建議）

| 區塊 | Composable | 主要 API |
|---|---|---|
| 整體 | `ReviewScreen(state, actions)` | `Column { TopToolbar; Row { BoardPane(weight 1f); RightPanel(width) }; BottomNavBar }` |
| 棋盤 | `GoBoard` | `Canvas` + `drawCircle`/`drawLine`/`drawText`（`rememberTextMeasurer`）、`pointerInput` 處理點擊與 hover（`onPointerEvent(PointerEventType.Move)`，desktop 用） |
| 圖表 | `ScoreGraph` | `Canvas` + `Path`；`detectTapGestures`/`detectDragGestures` |
| 候選手表 | `TopMovesTable` | `LazyColumn`：手、點損、勝率、目差、visits、prior |
| 頂列 | `AnalysisToolbar` | `Checkbox`×5 + `DropdownMenu`（每個項目右側以灰字顯示快捷鍵） |
| 鍵盤 | `Window(onKeyEvent=…)` 或 `Modifier.onPreviewKeyEvent` | 注意：Notes 欄位有焦點時要停用單鍵快捷鍵，KaTrain 也是這樣做的（`__main__.py` 第 740 行） |
