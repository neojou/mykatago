package com.neojou.mykatago.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.neojou.mykatago.analysis.EngineCommand
import com.neojou.mykatago.analysis.EngineStatus
import com.neojou.mykatago.analysis.QueryBuilder
import com.neojou.mykatago.analysis.mergeAnalysis
import com.neojou.mykatago.analysis.planWholeGame
import com.neojou.mykatago.analysis.deeperVisits
import com.neojou.mykatago.analysis.findMistake
import com.neojou.mykatago.analysis.shouldApply
import com.neojou.mykatago.analysis.syncChildRoot
import com.neojou.mykatago.go.BoardState
import com.neojou.mykatago.go.GameNode
import com.neojou.mykatago.go.GameTree
import com.neojou.mykatago.go.Move
import com.neojou.mykatago.go.NodeId
import com.neojou.mykatago.go.Point
import com.neojou.mykatago.go.SgfParseException
import com.neojou.mykatago.go.SgfParser
import com.neojou.mykatago.go.Stone
import com.neojou.mykatago.go.branchDown
import com.neojou.mykatago.go.branchUp
import com.neojou.mykatago.go.mainlineAt
import com.neojou.mykatago.go.mainlineLength
import com.neojou.mykatago.go.onMainline
import com.neojou.mykatago.go.pathFromRoot
import com.neojou.mykatago.go.playerToMove
import com.neojou.mykatago.go.redo
import com.neojou.mykatago.go.replay
import com.neojou.mykatago.go.toMainline
import com.neojou.mykatago.go.undo
import com.neojou.mykatago.go.undoToBranch
import com.neojou.mykatago.platform.PlatformServices
import com.neojou.mykatago.settings.AppSettings
import com.neojou.mykatago.settings.BoardToggles
import com.neojou.mykatago.settings.GraphMode
import com.neojou.mykatago.ui.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class ReviewState(
    val tree: GameTree,
    val cursorId: NodeId,
    val depth: Int,
    val mainlineLength: Int,
    val onMainline: Boolean,
    val board: BoardState,
    val revision: Int,
    val toggles: BoardToggles,
    val settings: AppSettings,
    val pendingQueries: Int,
    val pondering: Boolean,
    val player: Stone,
    val engine: EngineStatus,
    val banner: String?,
    val showCoords: Boolean,
    val showNumbers: Boolean,
    val canOpenFiles: Boolean,
    val showEngineDialog: Boolean,
    val showAnalysisDialog: Boolean,
    val showWholeGameDialog: Boolean,
)

class ReviewSession(
    private val services: PlatformServices,
    private val scope: CoroutineScope,
) {
    private var tree: GameTree = GameTree.empty()
    private var cursor: GameNode = tree.root
    private var settings: AppSettings = AppSettings()
    private var generation: Long = 0
    private var pondering: Boolean = false
    private var revision: Int = 0
    private var banner: String? = null
    private var engineStatus: EngineStatus = EngineStatus.Stopped
    private var showCoords: Boolean = true
    private var showNumbers: Boolean = false
    private var showEngineDialog: Boolean = false
    private var showAnalysisDialog: Boolean = false
    private var showWholeGameDialog: Boolean = false
    private var seq: Int = 0
    private val jobs = LinkedHashMap<String, Job>()
    private var coalesce: Job? = null

    /** True while a text field has focus, so single-key shortcuts stay out of the field. */
    var typing: Boolean = false

    var state by mutableStateOf(snapshot())
        private set

    init {
        scope.launch { bootstrap() }
    }

    fun node(id: NodeId): GameNode? = findNode(tree.root, id)

    fun openSgf() {
        val path = services.pickOpenPath() ?: return
        scope.launch {
            val parsed = runCatching {
                val text = services.decodeSgf(services.readBytes(path))
                SgfParser.parse(text).getOrElse { throw it }
            }
            parsed.onSuccess { loaded -> adopt(loaded) }
                .onFailure { error ->
                    banner = error.message ?: "無法開啟棋譜"
                    publish()
                }
        }
    }

    fun go(steps: Int) {
        cursor = if (steps >= 0) redo(cursor, steps) else undo(cursor, -steps)
        afterMove()
    }

    fun goFirst() {
        cursor = tree.root
        afterMove()
    }

    fun goLast() {
        cursor = mainlineAt(tree.root, mainlineLength(tree.root))
        afterMove()
    }

    fun previewDepth(depth: Int) {
        cursor = mainlineAt(tree.root, depth)
        publish()
    }

    fun jumpToDepth(depth: Int) {
        cursor = mainlineAt(tree.root, depth.coerceAtLeast(0))
        afterMove()
    }

    fun previousMistake() {
        cursor = findMistake(cursor, forward = false)
        afterMove()
    }

    fun nextMistake() {
        cursor = findMistake(cursor, forward = true)
        afterMove()
    }

    fun nextBranch() {
        cursor = branchDown(cursor)
        afterMove()
    }

    fun previousBranch() {
        cursor = branchUp(cursor)
        afterMove()
    }

    fun upToBranch() {
        cursor = undoToBranch(cursor)
        afterMove()
    }

    fun backToMainline() {
        cursor = toMainline(cursor)
        afterMove()
    }

    fun pass() {
        val player = playerToMove(cursor, tree)
        val existing = cursor.children.firstOrNull { child ->
            val move = child.move
            move is Move.Pass && move.player == player
        }
        cursor = existing ?: tree.addMove(cursor, Move.Pass(player))
        afterMove()
    }

    fun playAt(point: Point) {
        val player = playerToMove(cursor, tree)
        val existing = cursor.children.firstOrNull { child ->
            when (val move = child.move) {
                is Move.Play -> move.point == point && move.player == player
                else -> false
            }
        }
        if (existing != null) {
            cursor = existing
            afterMove()
            return
        }
        val move = Move.Play(player, point)
        val outcome = replay(tree, cursor).play(move, tree.rules, ignoreKo = false, ignoreSuicide = false)
        outcome.onSuccess {
            cursor = tree.addMove(cursor, move)
            afterMove()
        }.onFailure { error ->
            banner = error.message
            publish()
        }
    }

    fun togglePonder() {
        pondering = !pondering
        if (pondering) startPonder() else cancelNamed("ponder")
        publish()
    }

    fun stopAll() {
        pondering = false
        cancelAll()
        services.engine.terminateAll()
        publish()
    }

    fun deeper() {
        val node = cursor
        cancelNamed("ponder")
        val visits = deeperVisits(node.analysis?.visitsRequested ?: 0, settings.maxVisits)
        launchAnalysis(
            node = node,
            visits = visits,
            maxTime = null,
            reportEvery = 1.0,
            priority = QueryBuilder.PRIORITY_DEEPER,
            tag = "deeper",
            immediate = true,
            onDone = { if (pondering && cursor == node) startPonder() },
        )
    }

    fun resetAnalysis() {
        val node = cursor
        node.analysis = null
        cancelNamed("ponder")
        cancelNamed("pos")
        cancelNamed("deeper")
        publish()
        launchAnalysis(
            node = node,
            visits = settings.maxVisits,
            maxTime = settings.maxTimeSeconds,
            reportEvery = 1.0,
            priority = QueryBuilder.PRIORITY_POSITION,
            tag = "pos",
            immediate = true,
            onDone = { if (pondering && cursor == node) startPonder() },
        )
    }

    fun analyzeGame() {
        analyzeWhole(
            visits = settings.fastVisits,
            priority = QueryBuilder.PRIORITY_GAME,
            from = 0,
            to = mainlineLength(tree.root),
            mistakesOnly = false,
        )
    }

    fun analyzeWhole(visits: Int, priority: Int, from: Int, to: Int, mistakesOnly: Boolean) {
        val nodes = planWholeGame(tree.root, from, to, mistakesOnly)
        for (node in nodes) {
            launchAnalysis(
                node = node,
                visits = visits,
                maxTime = null,
                reportEvery = null,
                priority = priority,
                tag = "game-${node.id.value}",
                immediate = false,
                onDone = null,
            )
        }
    }

    fun setToggle(update: (BoardToggles) -> BoardToggles) {
        settings = settings.copy(toggles = update(settings.toggles))
        persist()
        publish()
    }

    fun setGraphMode(mode: GraphMode) {
        settings = settings.copy(graphMode = mode)
        persist()
        publish()
    }

    fun setCollapsed(graph: Boolean? = null, stats: Boolean? = null, info: Boolean? = null) {
        settings = settings.copy(
            graphCollapsed = graph ?: settings.graphCollapsed,
            statsCollapsed = stats ?: settings.statsCollapsed,
            infoCollapsed = info ?: settings.infoCollapsed,
        )
        persist()
        publish()
    }

    fun openEngineDialog() {
        showEngineDialog = true
        publish()
    }

    fun openAnalysisDialog() {
        showAnalysisDialog = true
        publish()
    }

    fun openWholeGameDialog() {
        showWholeGameDialog = true
        publish()
    }

    fun dismissDialogs() {
        showEngineDialog = false
        showAnalysisDialog = false
        showWholeGameDialog = false
        publish()
    }

    fun applyEngine(next: AppSettings) {
        var updated = next
        if (updated.configPath.isNotBlank()) {
            val installed = runCatching {
                services.installConfig(
                    updated.configPath,
                    updated.numAnalysisThreads,
                    updated.numSearchThreadsPerAnalysisThread,
                )
            }.getOrElse { updated.configPath }
            updated = updated.copy(configPath = installed)
        }
        val restart = updated.katagoPath != settings.katagoPath ||
            updated.modelPath != settings.modelPath ||
            updated.configPath != settings.configPath ||
            updated.numAnalysisThreads != settings.numAnalysisThreads ||
            updated.numSearchThreadsPerAnalysisThread != settings.numSearchThreadsPerAnalysisThread
        settings = updated.copy(toggles = settings.toggles, graphMode = settings.graphMode)
        showEngineDialog = false
        persist()
        if (restart) startEngineIfReady()
        publish()
    }

    fun applyAnalysis(next: AppSettings) {
        val threadsChanged = next.numAnalysisThreads != settings.numAnalysisThreads ||
            next.numSearchThreadsPerAnalysisThread != settings.numSearchThreadsPerAnalysisThread
        var updated = settings.copy(
            maxVisits = next.maxVisits,
            fastVisits = next.fastVisits,
            maxTimeSeconds = next.maxTimeSeconds,
            wideRootNoise = next.wideRootNoise,
            enableOwnership = next.enableOwnership,
            numAnalysisThreads = next.numAnalysisThreads,
            numSearchThreadsPerAnalysisThread = next.numSearchThreadsPerAnalysisThread,
            loadFastAnalysis = next.loadFastAnalysis,
            loadSgfRewind = next.loadSgfRewind,
        )
        if (threadsChanged && updated.configPath.isNotBlank()) {
            val installed = runCatching {
                services.installConfig(
                    updated.configPath,
                    updated.numAnalysisThreads,
                    updated.numSearchThreadsPerAnalysisThread,
                )
            }.getOrDefault(updated.configPath)
            updated = updated.copy(configPath = installed)
        }
        settings = updated
        showAnalysisDialog = false
        persist()
        if (threadsChanged) startEngineIfReady()
        publish()
    }

    fun autodetect(onDone: (AppSettings) -> Unit) {
        scope.launch {
            val detected = runCatching { services.detectEngine() }.getOrNull()
            if (detected == null || detected.katagoPath.isBlank()) {
                banner = Strings.missing
                publish()
                return@launch
            }
            val config = if (detected.configPath.isNotBlank()) {
                runCatching { services.installConfig(detected.configPath, null, null) }.getOrDefault(detected.configPath)
            } else {
                settings.configPath
            }
            onDone(
                settings.copy(
                    katagoPath = detected.katagoPath,
                    modelPath = detected.modelPath,
                    configPath = config,
                ),
            )
        }
    }

    fun testEngine(paths: AppSettings, onResult: (String) -> Unit) {
        scope.launch {
            if (paths.katagoPath.isBlank() || paths.modelPath.isBlank() || paths.configPath.isBlank()) {
                onResult(Strings.engineMissing)
                return@launch
            }
            val config = runCatching {
                services.installConfig(paths.configPath, paths.numAnalysisThreads, paths.numSearchThreadsPerAnalysisThread)
            }.getOrDefault(paths.configPath)
            services.engine.start(EngineCommand(paths.katagoPath, paths.modelPath, config))
            val response = services.engine.queryVersion()
            val text = when {
                response?.version != null -> "${Strings.found} ${response.version}"
                response?.error != null -> response.error
                else -> Strings.missing
            }
            onResult(text)
        }
    }

    fun browse(title: String, onPath: (String) -> Unit) {
        val path = services.browseForFile(title) ?: return
        onPath(path)
    }

    fun close() {
        cancelAll()
        services.engine.stop()
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
        if (typing && event.key != Key.Escape) return false
        when (event.key) {
            Key.Spacebar -> togglePonder()
            Key.Escape -> stopAll()
            Key.DirectionLeft -> go(if (event.isShiftPressed) -10 else -1)
            Key.DirectionRight -> go(if (event.isShiftPressed) 10 else 1)
            Key.DirectionUp -> previousBranch()
            Key.DirectionDown -> nextBranch()
            Key.Home -> goFirst()
            Key.MoveEnd -> goLast()
            Key.A -> deeper()
            Key.N -> if (event.isShiftPressed) previousMistake() else nextMistake()
            Key.P -> pass()
            Key.Q -> setToggle { it.copy(nextMoves = !it.nextMoves) }
            Key.W -> setToggle { it.copy(showDots = !it.showDots) }
            Key.E -> setToggle { it.copy(topMoves = !it.topMoves) }
            Key.R -> setToggle { it.copy(policy = !it.policy) }
            Key.T -> setToggle { it.copy(territory = !it.territory) }
            Key.B -> if (event.isShiftPressed) backToMainline() else upToBranch()
            Key.Z -> go(-1)
            Key.X -> go(1)
            Key.K -> {
                showCoords = !showCoords
                publish()
            }
            Key.M -> {
                showNumbers = !showNumbers
                publish()
            }
            else -> return false
        }
        return true
    }

    private suspend fun bootstrap() {
        settings = runCatching { services.settings.load() }.getOrDefault(AppSettings())
        if (services.canOpenFiles && settings.katagoPath.isBlank()) {
            val detected = runCatching { services.detectEngine() }.getOrNull()
            if (detected != null && detected.katagoPath.isNotBlank()) {
                val config = if (detected.configPath.isNotBlank()) {
                    runCatching { services.installConfig(detected.configPath, null, null) }
                        .getOrDefault(detected.configPath)
                } else {
                    ""
                }
                settings = settings.copy(
                    katagoPath = detected.katagoPath,
                    modelPath = detected.modelPath,
                    configPath = config,
                )
                services.settings.save(settings)
            }
        }
        if (!services.canOpenFiles) banner = Strings.wasmOnly
        else if (settings.katagoPath.isBlank() || settings.modelPath.isBlank() || settings.configPath.isBlank()) {
            banner = Strings.engineMissing
        }
        startEngineIfReady()
        publish()
        services.engine.status.collect { status ->
            engineStatus = status
            when (status) {
                is EngineStatus.Failed -> banner = status.message
                EngineStatus.Starting -> if (banner == null) banner = Strings.engineStarting
                is EngineStatus.Ready -> if (banner == Strings.engineStarting) banner = null
                EngineStatus.Stopped -> Unit
            }
            publish()
        }
    }

    private fun adopt(loaded: GameTree) {
        generation = services.engine.invalidate()
        cancelAll()
        tree = loaded
        cursor = if (settings.loadSgfRewind) loaded.root else mainlineAt(loaded.root, mainlineLength(loaded.root))
        pondering = false
        banner = if (loaded.hasMidGameSetup) Strings.midGameSetup else null
        publish()
        if (settings.loadFastAnalysis) analyzeGame() else ensureAnalysis()
    }

    private fun afterMove() {
        cancelNamed("pos")
        cancelNamed("ponder")
        publish()
        ensureAnalysis()
    }

    private fun ensureAnalysis() {
        if (pondering) startPonder()
        else if (cursor.analysis?.completed != true) {
            launchAnalysis(
                node = cursor,
                visits = settings.maxVisits,
                maxTime = settings.maxTimeSeconds,
                reportEvery = 1.0,
                priority = QueryBuilder.PRIORITY_POSITION,
                tag = "pos",
                immediate = true,
                onDone = null,
            )
        }
    }

    private fun startPonder() {
        val node = cursor
        launchAnalysis(
            node = node,
            visits = 10_000_000,
            maxTime = null,
            reportEvery = 0.25,
            priority = QueryBuilder.PRIORITY_POSITION,
            tag = "ponder",
            immediate = true,
            onDone = null,
        )
    }

    private fun launchAnalysis(
        node: GameNode,
        visits: Int,
        maxTime: Double?,
        reportEvery: Double?,
        priority: Int,
        tag: String,
        immediate: Boolean,
        onDone: (() -> Unit)?,
    ) {
        if (settings.katagoPath.isBlank()) return
        val gen = generation
        val id = "g$gen:$tag:${seq++}"
        val json = QueryBuilder.positionJson(
            tree = tree,
            node = node,
            id = id,
            maxVisits = visits,
            maxTime = maxTime,
            wideRootNoise = settings.wideRootNoise,
            includeOwnership = settings.enableOwnership,
            reportEvery = reportEvery,
            priority = priority,
        )
        val moveCount = pathFromRoot(node).count { it.move != null }
        cancelNamed(tag)
        val job = scope.launch {
            val me = coroutineContext[Job]
            var finished = false
            try {
                services.engine.analyze(id, json, gen, setOf(moveCount)).collect { response ->
                    if (!shouldApply(gen, generation)) return@collect
                    if (response.error != null) {
                        banner = response.error
                        publish()
                        return@collect
                    }
                    if (response.noResults == true) return@collect
                    node.analysis = mergeAnalysis(node.analysis, response, visits)
                    val parent = node.parent
                    val move = node.move
                    val root = node.analysis?.root
                    val parentAnalysis = parent?.analysis
                    if (parent != null && move != null && root != null && parentAnalysis != null) {
                        parent.analysis = syncChildRoot(parentAnalysis, QueryBuilder.gtpOf(move, tree.boardSize), root)
                    }
                    if (immediate) publish() else scheduleCoalesced()
                }
                finished = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                banner = error.message
                publish()
            } finally {
                if (jobs[tag] === me) jobs.remove(tag)
                if (finished) onDone?.invoke()
            }
        }
        jobs[tag] = job
    }

    private fun scheduleCoalesced() {
        if (coalesce?.isActive == true) return
        coalesce = scope.launch {
            delay(200)
            publish()
        }
    }

    private fun startEngineIfReady() {
        val katago = settings.katagoPath
        val model = settings.modelPath
        val config = settings.configPath
        if (katago.isBlank() || model.isBlank() || config.isBlank()) return
        services.engine.start(EngineCommand(katago, model, config))
    }

    private fun persist() {
        val snapshot = settings
        scope.launch { runCatching { services.settings.save(snapshot) } }
    }

    private fun cancelNamed(tag: String) {
        jobs.remove(tag)?.cancel()
    }

    private fun cancelAll() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
        coalesce?.cancel()
    }

    private fun publish() {
        revision++
        state = snapshot()
    }

    private fun snapshot(): ReviewState = ReviewState(
        tree = tree,
        cursorId = cursor.id,
        depth = cursor.depth,
        mainlineLength = mainlineLength(tree.root),
        onMainline = onMainline(cursor),
        board = replay(tree, cursor),
        revision = revision,
        toggles = settings.toggles,
        settings = settings,
        pendingQueries = jobs.size,
        pondering = pondering,
        player = playerToMove(cursor, tree),
        engine = engineStatus,
        banner = banner,
        showCoords = showCoords,
        showNumbers = showNumbers,
        canOpenFiles = services.canOpenFiles,
        showEngineDialog = showEngineDialog,
        showAnalysisDialog = showAnalysisDialog,
        showWholeGameDialog = showWholeGameDialog,
    )
}

private fun findNode(root: GameNode, id: NodeId): GameNode? {
    if (root.id == id) return root
    for (child in root.children) {
        findNode(child, id)?.let { return it }
    }
    return null
}
