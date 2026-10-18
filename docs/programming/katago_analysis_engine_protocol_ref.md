# KataGo Analysis Engine 協定參考

> 已查證：KataGo 官方 `docs/Analysis_Engine.md`（master，2026-10-18 讀取）與 KaTrain 的 `katrain/core/engine.py`。
> Homebrew 上的 KataGo 版本是 1.18.2（已查證：`formulae.brew.sh/api/formula/katago.json`）。

## 1. 為什麼用 Analysis Engine，不用 GTP

| | Analysis Engine（`katago analysis`） | GTP（`katago gtp` + `kata-analyze`） |
|---|---|---|
| 介面 | 每行一個 JSON，非同步，可以同時送出多個 query，每個 query 有自己的 id | 文字指令，一次只能跑一個 |
| 整局分析 | 用 `analyzeTurns` 或多個 query 平行處理，有 `priority`，可以 batch | 要逐手 `play` 後再 analyze，很慢 |
| ownership 和 policy | `includeOwnership` / `includePolicy` | `kata-analyze ownership true`，policy 要另外下指令 |
| 中止 | `{"action":"terminate","terminateId":...}` | 送任何一個指令就會中止 |
| KaTrain 的做法 | 只用這個，對 AI 對局也一樣 | 沒有用 |

**結論：mykatago 只用 Analysis Engine。** 已查證 KaTrain `engine.py` 只啟動 `analysis` 子命令。

## 2. 啟動指令

```bash
# KaTrain 的做法（engine.py 第 146–159 行）
katago analysis -model <MODEL.bin.gz> -config <analysis.cfg> \
  -override-config homeDataDir=~/.katrain
# 有 human-like 模型時再加：-human-model <b18c384nbt-humanv0.bin.gz>
```

mykatago 建議的寫法（推測，需要實測）：
```bash
/opt/homebrew/bin/katago analysis \
  -model /opt/homebrew/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz \
  -config /opt/homebrew/share/katago/configs/analysis_example.cfg \
  -override-config "logDir=$HOME/Library/Application Support/mykatago/katago_logs,homeDataDir=$HOME/Library/Application Support/mykatago/katago_home"
```
- 官方的 `analysis_example.cfg` 有一行 `logDir = analysis_logs`，這是**相對路徑**。打包成 .app 後，工作目錄可能沒有寫入權限，所以要用 `-override-config` 改掉，或是設定 `ProcessBuilder.directory(...)`（推測）。
- `-override-config` 可以用逗號串接多個設定（推測，跟 GTP 的用法相同，需要實測）。路徑含空白時，用 `ProcessBuilder` 傳參數列表就不必處理引號，KaTrain 也是傳 argv list。
- 啟動後 KataGo 會把載入訊息寫到 stderr。第一次執行時 Metal 或 OpenCL 可能要先 tune 一段時間。stdout 開始可用之前可以先送 query，它會排隊。
- 第一個 query 建議送 `{"id":"v","action":"query_version"}`，用來確認引擎已經就緒。

## 3. Query 欄位（已查證：官方文件）

| 欄位 | 必填 | 說明 | KaTrain 的值 |
|---|---|---|---|
| `id` | 是 | 任意字串，回應會帶同一個 id | `QUERY:<counter>` |
| `moves` | 是 | `[["B","Q16"],["W","D4"],...]`。可以是空陣列。pass 寫成 `"pass"` | 從 root 到目前節點的所有手 |
| `initialStones` | 否 | 讓子或 AB/AW 擺子 | 從各節點的 placements 收集 |
| `initialPlayer` | 否 | 第 0 手由誰下 | root 的 PL 或推算值 |
| `rules` | 是 | `"japanese"`、`"chinese"`、`"korean"`、`"aga"`、`"tromp-taylor"`、`"new zealand"`、`"stone_scoring"`，或 JSON 物件 | SGF 的 RU 對照表（`engine.py RULESETS_ABBR`，例如 jp→japanese） |
| `komi` | 強烈建議 | 整數或半整數 | SGF 的 KM |
| `boardXSize`、`boardYSize` | 是 | ≤19（預設編譯） | SZ |
| `analyzeTurns` | 否 | 要分析第幾手，0 是初始局面 | `[len(moves)]`，只分析最後一手 |
| `maxVisits` | 否 | 覆蓋 cfg 的設定 | engine/max_visits=500；fast=25；ponder=10,000,000 |
| `includeOwnership` | 否 | 回傳 ownership | 預設開（`_enable_ownership`） |
| `includeMovesOwnership` | 否 | 每個候選手各自的 ownership | 跟 ownership 一起開（mykatago 可以先關掉，省頻寬） |
| `includePolicy` | 否 | 回傳 policy，長度 N*N+1 | 開 |
| `includePVVisits` | 否 | PV 每一步的 visits | 沒用 |
| `avoidMoves` / `allowMoves` | 否 | `[{"player":"B","moves":["C3"],"untilDepth":1}]` | Find alternatives 和 region of interest 會用 |
| `overrideSettings` | 否 | 例如 `{"reportAnalysisWinratesAs":"BLACK","wideRootNoise":0.04,"maxTime":8.0}` | 一定會帶這三個（ponder 不帶 maxTime） |
| `reportDuringSearchEvery` | 否 | 每隔幾秒回報一次中間結果（`isDuringSearch:true`） | 一般 1 秒；ponder 0.25 秒 |
| `priority` / `priorities` | 否 | 數字越大越先處理 | 新的一手 1000、extra 100、sweep -10、整局 -100、整局重新分析 -1,000,000 |
| `focusMoves` 等 | 否 | 1.18 之後才有的新功能 | 沒用 |

### Query 範例
```json
{"id":"Q:12","moves":[["B","Q16"],["W","D4"],["B","Q3"]],"initialStones":[],"rules":"japanese","komi":6.5,"boardXSize":19,"boardYSize":19,"analyzeTurns":[3],"maxVisits":500,"includeOwnership":true,"includePolicy":true,"reportDuringSearchEvery":1.0,"priority":1000,"overrideSettings":{"reportAnalysisWinratesAs":"BLACK","wideRootNoise":0.04,"maxTime":8.0}}
```

整局分析時，**一個 query 帶多個 turn** 也可以（推薦，已查證官方支援）：
```json
{"id":"GAME:1","moves":[["B","Q16"],["W","D4"],["B","Q3"]],"rules":"japanese","komi":6.5,"boardXSize":19,"boardYSize":19,"analyzeTurns":[0,1,2,3],"maxVisits":100,"includeOwnership":true,"includePolicy":true,"priority":-100}
```
這樣會收到 4 個回應，每個都帶 `turnNumber`。KaTrain 的做法是每個節點各送一個 query，好處是跟變化分支的對應比較單純。**mykatago 建議主線用 analyzeTurns 批次送，分支節點則各送一個 query。** 用 `id + turnNumber` 找回對應的 node（推測，屬於設計選擇）。

### 特殊動作
```json
{"id":"T:1","action":"terminate","terminateId":"Q:12"}
{"id":"T:2","action":"terminate","terminateId":"GAME:1","turnNumbers":[5,6]}
{"id":"T:3","action":"terminate_all"}
{"id":"V:1","action":"query_version"}
{"id":"M:1","action":"query_models"}
{"id":"C:1","action":"clear_cache"}
```
被 terminate 的 query 一定還會回一次 `isDuringSearch:false`，可能帶 `"noResults":true`。**每個分析 query 最後都會有剛好一個 `isDuringSearch:false` 的回應**，可以用它判斷「這個 query 結束了」，這點官方文件有保證。

## 4. Response（已查證）

```json
{"id":"Q:12","isDuringSearch":false,"turnNumber":3,
 "moveInfos":[
   {"move":"D16","order":0,"visits":312,"edgeVisits":312,"winrate":0.512,"scoreLead":0.4,"scoreMean":0.4,"scoreStdev":28.1,"scoreSelfplay":0.6,"prior":0.21,"utility":0.01,"lcb":0.50,"utilityLcb":-0.02,"pv":["D16","C17","C16"]},
   {"move":"R4","order":1,"visits":120,"winrate":0.505,"scoreLead":0.3,"prior":0.18,"pv":["R4","Q4"]}
 ],
 "rootInfo":{"currentPlayer":"W","visits":500,"winrate":0.525,"scoreLead":0.3,"scoreSelfplay":0.5,"utility":0.02,"thisHash":"...","symHash":"...","rawWinrate":0.53,"rawLead":0.5},
 "ownership":[0.98, 0.97, "...共 361 個"],
 "policy":[0.0001, "...共 362 個，最後一個是 pass，-1 代表不合法"]}
```

錯誤與警告的格式：
```json
{"error":"Illegal move ...","field":"moves","id":"Q:12"}
{"warning":"...","field":"...","id":"Q:12"}
```
- KaTrain 遇到沒有 id 的錯誤會寫 log 後略過。有 id 的錯誤會把該 query 從表中移除，並呼叫 error_callback。
- **只要 `reportAnalysisWinratesAs=BLACK`，所有 winrate 和 scoreLead 都是黑方視角。** KaTrain 會在每個 query 的 overrideSettings 強制帶這個設定，即使 cfg 已經寫了也一樣。mykatago 也要這樣做。
- 解析時要能容忍新增欄位：`Json { ignoreUnknownKeys = true }`。

## 5. 陣列座標（重要，已查證）

- `ownership`、`policy`、`moveInfos[].ownership`：**row-major，從左上角（A19）開始，到右下角（T1）結束**。
  - index = `row * boardXSize + col`，`row=0` 是最上面一行（第 19 行）。
  - KaTrain 的 `var_to_grid` 會翻轉 y，因為它內部的 y=0 在下方。**mykatago 建議內部座標用 y=0 在上方**，跟 SGF 和陣列一致，就不需要翻轉。不過 GTP 字串的轉換要寫對：`row = size - number`。
- `policy` 的長度是 N*N+1，最後一個值是 pass，`-1` 代表不合法（已有棋子）。
- ownership 的值域是 -1..1，正值偏黑（BLACK 視角）。

## 6. GTP 座標

- 欄位：`ABCDEFGHJKLMNOPQRST`，**沒有 I**。行號：1 在最下方。pass 寫成 `"pass"`。
- SGF 座標轉 GTP：SGF 的 `"pd"` 表示 x=15（p）、y=3（d，從上往下數）→ 欄 `Q`（index 15）、行 `19-3=16` → `Q16`。

## 7. KaTrain 引擎管理器的行為（要移植的重點，已查證：`engine.py`）

1. 三條執行緒：stdin writer 從 queue 取 query 後才寫入。官方註解提到，持有鎖時 flush 可能因 stdout pipe 滿了而 deadlock。stdout reader 一次讀一行 JSON。stderr reader 只寫 log，看到 `Uncaught exception` 時視為引擎錯誤。
2. `queries: id -> (callback, error_callback, start_time, next_move, node)`。收到 `isDuringSearch:false` 時把該 id 移除。
3. `query_generation`：開新局或 restart 時加一。writer 遇到舊 generation 的 query 就丟掉。**mykatago 也要做，避免換棋譜後舊結果寫到新棋譜上。**
4. Ponder（持續分析）：同一時間只有一個 ponder query。除了 id、maxVisits、reportDuringSearchEvery 以外，若其他欄位都一樣就不重送；只要有不同，就先 terminate 舊的再送新的。
5. `terminate_queries(only_for_node)`：Reset analysis 時只中止這個節點的 query。
6. 引擎掛掉時（`process.poll()!=None`）跳出 recovery 對話框，可以重試或修改設定。

## 8. Kotlin 實作骨架（推測，屬於設計建議，API 名稱需要以實際版本確認）

```kotlin
// desktopMain
class KataGoAnalysisEngine(private val cmd: List<String>, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val pending = ConcurrentHashMap<String, Channel<AnalysisResponse>>()
    private val writeCh = Channel<String>(Channel.UNLIMITED)
    private lateinit var proc: Process
    fun start() {
        proc = ProcessBuilder(cmd).redirectErrorStream(false).start()
        scope.launch(Dispatchers.IO) { proc.outputStream.bufferedWriter().use { w -> for (l in writeCh) { w.write(l); w.newLine(); w.flush() } } }
        scope.launch(Dispatchers.IO) { proc.inputStream.bufferedReader().lineSequence().forEach(::dispatch) }
        scope.launch(Dispatchers.IO) { proc.errorStream.bufferedReader().lineSequence().forEach { MyLog.add("KATAGO", it) } }
    }
    fun analyze(q: AnalysisQuery): Flow<AnalysisResponse> = channelFlow { /* 註冊 pending[q.id]，送出，收到 isDuringSearch=false 就 close；awaitClose { terminate(q.id) } */ }
    fun terminate(id: String) = writeCh.trySend("""{"id":"T-$id","action":"terminate","terminateId":"$id"}""")
    fun stop() { writeCh.close(); proc.destroy() }
}
```
- 用 Flow 加上 `awaitClose { terminate }`，collector 被取消（例如換局面）時會自動送出 terminate，這樣「移動到別手就停止舊的分析」很自然就做到了。
- `Process.destroy()` 在 macOS 送的是 SIGTERM。要確保 App 關閉時一定會呼叫：`Window(onCloseRequest)`，加上 `Runtime.addShutdownHook` 當保險。
