# SGF 解析與棋局樹（Game Tree）參考

> KaTrain 1.20 的 SGF 解析放在外部套件 `pysgf`（已查證：`pyproject.toml` 寫 `pysgf>=1.0.0,<2`；`tests/test_parser.py` 開頭註解說明 parser 已經移到 pysgf）。
> 提子規則與導覽在 `katrain/core/game.py`，節點分析在 `katrain/core/game_node.py`（已查證）。
> mykatago 要在 commonMain 用純 Kotlin 重寫，不依賴 JVM，這樣 wasm 也能編譯。

## 1. SGF 格式重點（FF[4]）

```
(;GM[1]FF[4]CA[UTF-8]SZ[19]KM[6.5]RU[Japanese]PB[黑方]PW[白方]RE[W+R]HA[2]AB[dp][pd]
 ;W[qp];B[dd]C[註解]
   (;W[pq];B[oq])      ← 主線（第一個子節點）
   (;W[qf])            ← 變化
)
```
- 結構：`(` 開始一個 GameTree，`;` 開始一個 Node，接著是 `PropIdent[Value][Value]...`，`)` 結束。
- 值裡的 `]` 和 `\` 要用 `\` 跳脫。`\` 後面接換行時，代表「軟換行」，要把換行移除。
- 座標：`aa` 是左上角，第一個字母是 x（欄），第二個是 y（列，從上往下數）。`B[]`，或是 19 路以下的 `B[tt]`，代表 **pass**。
- 必須支援的屬性：
  - Root：`GM FF CA SZ KM RU HA PB PW BR WR RE DT EV GN PL AP`
  - 擺子：`AB AW AE`
  - 著手：`B W`
  - 註解與標記：`C N MA SQ TR CR LB`
  - KaTrain 自訂：`KT`（分析快取）、`KTV`（格式版本）、`KTSF`／`KTSID`（shortcut）
- `SZ[19]` 或 `SZ[19:13]` 這種長方形棋盤可以先不支援，遇到時顯示錯誤。
- 編碼：先看 `CA[...]`。沒有 CA 時，先試 UTF-8，失敗再試 GB18030 或 Big5（推測：中文棋譜常見 GB2312；KaTrain 依賴 `chardet>=5.2.0` 做編碼偵測，已查證在 pyproject 依賴中）。**desktopMain** 讀 bytes 後偵測編碼，commonMain 的 parser 只處理 String。
- 野狐（Fox）的 SGF 有怪異之處：KaTrain 有專門的測試 `tests/test_parser.py test_foxwq`，包括 HA 與 AB 不一致、`num_starting_moves_black` 等邏輯（`game.py` 第 66–85 行）。mykatago 第一版先處理：**HA≥2 且沒有 AB 時，就放標準讓子**。
- GIB／NGF（韓國、日本格式）屬於 P2。

## 2. 資料模型建議（commonMain）

```kotlin
enum class Stone { BLACK, WHITE }
data class Point(val x: Int, val y: Int)               // y=0 在上方（與 SGF 和 KataGo 陣列一致）
sealed interface Move { val player: Stone
    data class Play(override val player: Stone, val point: Point) : Move
    data class Pass(override val player: Stone) : Move }

class GameNode(val parent: GameNode?) {
    val props: MutableMap<String, MutableList<String>> = linkedMapOf()   // 原始屬性，存檔時保留
    val children: MutableList<GameNode> = mutableListOf()                // children[0] = 主線
    val move: Move?               // 從 B/W 解析
    val placements: List<Pair<Stone, Point>>  // AB/AW
    val clears: List<Point>       // AE
    var analysis: NodeAnalysis? = null       // 由 AnalysisController 寫入（可觀察狀態）
    val depth: Int                // 從 root 起算的步數
}
class GameTree(val root: GameNode) { val boardSize: Int; val komi: Double; val rules: String; val handicap: Int }

data class NodeAnalysis(
    val root: RootInfo?, val moves: Map<String, MoveInfo>,  // key = GTP 字串
    val ownership: FloatArray?, val policy: FloatArray?,
    val visitsRequested: Int, val completed: Boolean)
```

## 3. 棋盤規則（提子、劫、自殺）：參考 `game.py _validate_move_and_update_chains`

KaTrain 的演算法（已查證）：
1. 落點已有棋子時，丟出 `IllegalMove("Space occupied")`。
2. 跟相鄰的同色串合併成一串。
3. 相鄰的敵串若沒有氣，就提掉，記入 `last_capture`。
4. **簡單劫**：上一手剛好提掉 1 子，這手又下在那個點，而且這手也只提 1 子，就判定為劫（`ko_or_snapback`）。
5. 自殺：單子自殺一律禁止；多子自殺只在 `tromp-taylor`、`new zealand` 規則，或規則 JSON 的 `suicide=true` 時允許。
6. 從 SGF 重建盤面時，`ignore_ko=true`，因為棋譜裡可能有被強制的手。

Kotlin 建議用不可變的 `BoardState(size, IntArray grid, capturesB, capturesW, koPoint)`，提供 `fun play(m: Move): Result<BoardState>`。導覽時從 root 重新計算，或是對每個節點的 BoardState 做 LRU 快取。19 路 300 手每次重算也很快，推測每次不到 1ms，可以先不做快取。

## 4. 導覽語意：參考 `game.py undo/redo`

| 動作 | 行為 |
|---|---|
| redo(n) | 沿 `children[0]` 往下走 n 步（KaTrain 會先依 `ordered_children` 排序，教學模式用的，復盤可以忽略） |
| undo(n) | 往 parent 走 n 步 |
| undo("branch") | 往上走到第一個有多個子節點的分岔點 |
| undo("main-branch")（Shift+B） | 回到主線 |
| find-mistake(redo/undo) | 往下或往上找，直到 `points_lost ≥ eval_thresholds[-4]`（=3.0），停在該手的 **parent**，讓使用者看到「該下哪裡」 |
| 跳到第 N 手（滑桿） | 在主線上從 root redo(N)（或從目前路徑） |
| 點棋盤（試下） | 子節點已有同一手就移過去，沒有就新增子節點（變化），然後送分析 |

## 5. 節點分析快取與合併：參考 `game_node.py set_analysis`

- 一般 query 的結果：把舊的 moves 的 order 設成 999，新的 `moveInfos` 依 visits 較大的優先合併。`root`、`ownership`、`policy` 直接覆蓋。
- 收到 `isDuringSearch:false` 時，設 `completed=true`。
- **父節點同步**：子節點的 rootInfo 回來後，也寫進父節點的 `moves[child.move]`。這樣父節點的候選手表裡一定有實際下出的那一手。
- `refine_move`（equalize、sweep 用）：送出「父局面 + 該手」的 query，把結果的 rootInfo 存成父節點 moves 中那一手的資料，PV 前面補上該手。
- `find_alternatives`：用 avoidMoves 把現有候選手全部排除，結果只合併 moves，不覆蓋 root。

## 6. 存 SGF 時的分析資料（P1）

- KaTrain 的 `KT` 屬性有三個值，依序是 `[ownership, policy, main_json]`，每個都經過 gzip 壓縮再 base64 編碼。ownership 與 policy 是 **float16**（`struct.pack("e")`），格式版本 `KTV[1.0]`（已查證：`game_node.py analysis_dumps`、`utils.pack_floats`）。
- mykatago 若想跟 KaTrain 互通，可以實作相同格式，在 JVM 上用 `java.util.zip.GZIPOutputStream`，float16 用 `Float.floatToFloat16`（JDK 20+）。**建議 P1 先只寫註解（C）和標記（MA、SQ），不寫 KT**，等之後要互通時再加。
- KaTrain 自動產生的註解會加上不可見的標記 `SGF_INTERNAL_COMMENTS_MARKER = "\u3164\u200b"`，重新載入時會剝掉這些註解，避免重複累加（已查證：`constants.py`、`game_node.add_list_property`）。mykatago 可以用同樣的方法，或改用自己的標記。
- 存檔時 root 會寫 `CA[UTF-8]`、`AP[mykatago:版本]`。

## 7. 測試建議（commonTest，用 kotlin-test）

- 解析：基本棋譜、變化、跳脫字元、pass（`B[]`、`B[tt]`）、AB/AW/AE、中文 PB/C。
- 規則：提一子、提多子、劫、自殺、打二還一（snapback）。
- 座標：SGF↔GTP↔Point 來回轉換，19、13、9 路，確認跳過 I。
- 序列化往返：parse → serialize → parse 的結構要一致。
- 測試用 SGF **自己寫**。KaTrain 的 `tests/data/*.sgf` 是第三方棋譜，著作權不明，不要複製。
