package com.neojou.mykatago.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neojou.mykatago.analysis.EngineStatus
import com.neojou.mykatago.analysis.formatLoss
import com.neojou.mykatago.analysis.formatScore
import com.neojou.mykatago.analysis.formatVisits
import com.neojou.mykatago.analysis.formatWinrate
import com.neojou.mykatago.analysis.pointsLostOfPlayedMove
import com.neojou.mykatago.analysis.scoreAxisMax
import com.neojou.mykatago.analysis.winrateAxisMax
import com.neojou.mykatago.go.GameNode
import com.neojou.mykatago.go.Stone
import com.neojou.mykatago.go.mainlineAt
import com.neojou.mykatago.settings.GraphMode
import com.neojou.mykatago.ui.Strings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun AnalysisToolbar(state: ReviewState, session: ReviewSession, about: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().background(BoardPalette.panel).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.canOpenFiles) {
            TextButton(onClick = session::openSgf) { Text(Strings.openSgf) }
        } else {
            TextButton(onClick = about) { Text(Strings.about) }
        }
        Toggle(Strings.nextMoves, state.toggles.nextMoves, "q") { session.setToggle { it.copy(nextMoves = !it.nextMoves) } }
        Toggle(Strings.showDots, state.toggles.showDots, "w") { session.setToggle { it.copy(showDots = !it.showDots) } }
        Toggle(Strings.topMoves, state.toggles.topMoves, "e") { session.setToggle { it.copy(topMoves = !it.topMoves) } }
        Toggle(Strings.policyMoves, state.toggles.policy, "r") { session.setToggle { it.copy(policy = !it.policy) } }
        Toggle(Strings.territory, state.toggles.territory, "t") { session.setToggle { it.copy(territory = !it.territory) } }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { menu = true }) { Text(Strings.analysisOptions) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuAction(Strings.deeper, "a") { menu = false; session.deeper() }
            DisabledItem(Strings.equalize, "s")
            DisabledItem(Strings.sweep, "d")
            DisabledItem(Strings.alternatives, "f")
            DisabledItem(Strings.region, "g")
            MenuAction(Strings.reset, "⌃H") { menu = false; session.resetAnalysis() }
            DisabledItem(Strings.insert, "i")
            DisabledItem(Strings.selfplay, "l")
            MenuAction(Strings.wholeGameDeep, "F2") { menu = false; session.openWholeGameDialog() }
            DisabledItem(Strings.report, "F3")
            DisabledItem(Strings.tsumego, "F10")
            MenuAction(Strings.togglePonder, "space") { menu = false; session.togglePonder() }
            DisabledItem(Strings.forceAi, "enter")
            MenuAction(Strings.stopAnalysis, "esc") { menu = false; session.stopAll() }
            MenuAction(Strings.analyzeGame, "") { menu = false; session.analyzeGame() }
            MenuAction(Strings.engineSettings, "") { menu = false; session.openEngineDialog() }
            MenuAction(Strings.analysisSettings, "") { menu = false; session.openAnalysisDialog() }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, key: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onClick() })
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(" $key", color = Color(0xFFB3B3B3), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MenuAction(label: String, shortcut: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { ShortcutLabel(label, shortcut) },
        onClick = onClick,
    )
}

@Composable
private fun DisabledItem(label: String, shortcut: String) {
    DropdownMenuItem(
        text = { ShortcutLabel("$label ${Strings.notYet}", shortcut) },
        onClick = {},
        enabled = false,
    )
}

@Composable
private fun ShortcutLabel(label: String, shortcut: String) {
    Row {
        Text(label, modifier = Modifier.weight(1f))
        if (shortcut.isNotEmpty()) {
            Spacer(Modifier.width(16.dp))
            Text(shortcut, color = Color(0xFFB3B3B3))
        }
    }
}

@Composable
fun BottomNavBar(state: ReviewState, session: ReviewSession) {
    var jumpText by remember(state.depth) { mutableStateOf(state.depth.toString()) }
    var sliding by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().background(BoardPalette.panel).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = session::pass) { Text(Strings.pass) }
            Nav(Strings.previousMistake, "⟸", Color(0xFFC90F0F), session::previousMistake)
            Nav(Strings.firstMove, "|◀", null, session::goFirst)
            Nav(Strings.back10, "◀◀", null) { session.go(-10) }
            Nav(Strings.back1, "◀", null) { session.go(-1) }
            TurnMark(state.player)
            Nav(Strings.forward1, "▶", null) { session.go(1) }
            Nav(Strings.forward10, "▶▶", null) { session.go(10) }
            Nav(Strings.lastMove, "▶|", null, session::goLast)
            Nav(Strings.nextMistake, "⟹", Color(0xFFC90F0F), session::nextMistake)
            Spacer(Modifier.weight(1f))
            EngineLamp(state)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val length = state.mainlineLength
            Slider(
                value = (sliding ?: state.depth).toFloat().coerceIn(0f, length.coerceAtLeast(0).toFloat()),
                onValueChange = {
                    val depth = it.roundToInt().coerceIn(0, length)
                    sliding = depth
                    session.previewDepth(depth)
                },
                onValueChangeFinished = {
                    session.jumpToDepth(sliding ?: state.depth)
                    sliding = null
                },
                valueRange = 0f..length.coerceAtLeast(1).toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text("${Strings.moveN} ", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.text.BasicTextField(
                value = jumpText,
                onValueChange = { jumpText = it.filter { ch -> ch.isDigit() }.take(4) },
                modifier = Modifier
                    .width(48.dp)
                    .background(BoardPalette.panelLight, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .onFocus(session),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFF2F2F2)),
                singleLine = true,
            )
            Text(" ${Strings.moveOf} ${state.mainlineLength} ${Strings.moveUnit}")
            TextButton(onClick = {
                session.jumpToDepth(jumpText.toIntOrNull() ?: state.depth)
            }) { Text(Strings.jump) }
        }
    }
}

@Composable
private fun Nav(description: String, glyph: String, color: Color?, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(glyph, color = color ?: Color.Unspecified)
    }
}

@Composable
private fun TurnMark(player: Stone) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        StoneDot(Stone.BLACK, player == Stone.BLACK)
        StoneDot(Stone.WHITE, player == Stone.WHITE)
    }
}

@Composable
private fun StoneDot(stone: Stone, active: Boolean) {
    val color = if (stone == Stone.BLACK) Color(0xFF111111) else Color(0xFFF2F2F2)
    Spacer(
        Modifier
            .size(if (active) 18.dp else 14.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun EngineLamp(state: ReviewState) {
    val color = when {
        state.engine is EngineStatus.Failed -> BoardPalette.failed
        state.engine is EngineStatus.Ready && state.pondering -> BoardPalette.pondering
        state.engine is EngineStatus.Ready && state.pendingQueries > 0 -> BoardPalette.busy
        state.engine is EngineStatus.Ready -> BoardPalette.ready
        else -> BoardPalette.busy
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(14.dp).clip(CircleShape).background(color))
        if (state.pendingQueries >= 3) {
            Text(" ${state.pendingQueries}", style = MaterialTheme.typography.labelMedium)
        }
        if (state.engine is EngineStatus.Ready) {
            Text("  ${state.engine.version}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun RightPanel(state: ReviewState, node: GameNode?, session: ReviewSession, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(BoardPalette.panel)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(Strings.analysisMenu, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        PlayerRow(state, node)
        MoveDots(state, session)
        Section(Strings.graphWinrate, state.settings.graphCollapsed) {
            session.setCollapsed(graph = !state.settings.graphCollapsed)
        }
        if (!state.settings.graphCollapsed) {
            Row {
                TextButton(onClick = { session.setGraphMode(GraphMode.WINRATE) }) {
                    Text(Strings.graphWinrate, color = if (state.settings.graphMode == GraphMode.WINRATE) MaterialTheme.colorScheme.primary else Color.Unspecified)
                }
                TextButton(onClick = { session.setGraphMode(GraphMode.POINT_LOSS) }) {
                    Text(Strings.graphLoss, color = if (state.settings.graphMode == GraphMode.POINT_LOSS) MaterialTheme.colorScheme.primary else Color.Unspecified)
                }
            }
            ScoreGraph(state, session, Modifier.fillMaxWidth().height(140.dp))
        }
        Section(Strings.winrate, state.settings.statsCollapsed) {
            session.setCollapsed(stats = !state.settings.statsCollapsed)
        }
        if (!state.settings.statsCollapsed) StatsBlock(node)
        CandidateTable(node)
        Section(Strings.details, state.settings.infoCollapsed) {
            session.setCollapsed(info = !state.settings.infoCollapsed)
        }
        if (!state.settings.infoCollapsed) InfoBlock(state, node)
    }
}

@Composable
private fun Section(title: String, collapsed: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("${if (collapsed) "▸" else "▾"} $title")
    }
}

@Composable
private fun PlayerRow(state: ReviewState, node: GameNode?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerCard(
            name = state.tree.playerBlack.ifBlank { Strings.playerBlack },
            rank = state.tree.rankBlack,
            captures = state.board.capturesBlack,
            active = state.player == Stone.BLACK,
            black = true,
            modifier = Modifier.weight(1f),
        )
        PlayerCard(
            name = state.tree.playerWhite.ifBlank { Strings.playerWhite },
            rank = state.tree.rankWhite,
            captures = state.board.capturesWhite,
            active = state.player == Stone.WHITE,
            black = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayerCard(
    name: String,
    rank: String,
    captures: Int,
    active: Boolean,
    black: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(if (active) BoardPalette.panelLight else Color(0xFF24384C), RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Text("${if (black) "●" else "○"} $name", maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (rank.isNotBlank()) Text(rank, style = MaterialTheme.typography.labelSmall)
        Text("${Strings.captures} $captures", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MoveDots(state: ReviewState, session: ReviewSession) {
    val start = (state.depth - 8).coerceAtLeast(0)
    val end = (state.depth + 4).coerceAtMost(state.mainlineLength)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (depth in start..end) {
            val node = mainlineAt(state.tree.root, depth)
            val lost = pointsLostOfPlayedMove(node)
            val color = if (lost == null) BoardPalette.panelLight else BoardPalette.evalColor(lost)
            Text(
                depth.toString(),
                color = if (depth == state.depth) Color.Black else Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (depth == state.depth) MaterialTheme.colorScheme.primary else color)
                    .clickable { session.jumpToDepth(depth) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun StatsBlock(node: GameNode?) {
    val root = node?.analysis?.root
    val lost = node?.let { pointsLostOfPlayedMove(it) }
    Column {
        Text("${Strings.winrate}  ${root?.let { formatWinrate(it.winrate) } ?: Strings.notAnalyzed}")
        Text("${Strings.scoreLead}  ${root?.let { formatScore(it.scoreLead) } ?: "—"}")
        val lossLabel = when {
            lost == null -> Strings.notAnalyzed
            lost < 0 -> "${Strings.pointGain}  ${formatLoss(lost)}"
            else -> "${Strings.pointLoss}  ${formatLoss(lost)}"
        }
        Text(lossLabel)
    }
}

@Composable
private fun CandidateTable(node: GameNode?) {
    val rows = node?.analysis?.candidates().orEmpty().take(12)
    Column {
        Text(Strings.candidates, fontWeight = FontWeight.Medium)
        Text(
            "${Strings.move}   ${Strings.pointLoss}   ${Strings.winrate}   ${Strings.scoreLead}   ${Strings.visits}   ${Strings.prior}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB3B3B3),
        )
        if (rows.isEmpty()) Text(Strings.notAnalyzed, style = MaterialTheme.typography.bodySmall)
        rows.forEach { row ->
            Text(
                "${row.move}   ${formatLoss(row.pointsLost)}   ${formatWinrate(row.winrate)}   ${formatScore(row.scoreLead)}   ${formatVisits(row.visits)}   ${row.prior}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoBlock(state: ReviewState, node: GameNode?) {
    val tree = state.tree
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(Strings.details, fontWeight = FontWeight.Medium)
        if (node == null || node.parent == null) {
            val komi = if (tree.komiDefaulted) "${Strings.komiMissing} ${tree.komi}" else "${Strings.komi}: ${tree.komi}"
            Text(komi)
            Text("${Strings.rules}: ${tree.rules.label}")
        } else {
            val move = node.move
            val coord = when (move) {
                is com.neojou.mykatago.go.Move.Play -> com.neojou.mykatago.go.pointToGtp(move.point, tree.boardSize)
                is com.neojou.mykatago.go.Move.Pass -> "pass"
                null -> ""
            }
            Text("${Strings.moveNumber}: ${node.depth}")
            Text("${Strings.coordinate}: $coord")
            val root = node.analysis?.root
            if (root != null) {
                Text("${Strings.winrate}: ${formatWinrate(root.winrate)}")
                Text("${Strings.scoreLead}: ${formatScore(root.scoreLead)}")
            } else {
                Text(Strings.notAnalyzed)
            }
            val lost = pointsLostOfPlayedMove(node)
            if (lost != null) {
                val label = if (lost < 0) Strings.pointGain else Strings.pointLoss
                Text("$label: ${formatLoss(lost)}")
            }
            val best = node.analysis?.candidates()?.firstOrNull()
            if (best != null) {
                Text("${Strings.best}: ${best.move} ${formatLoss(best.pointsLost)}")
                if (best.pv.size > 1) Text("${Strings.pv}: ${best.pv.joinToString(" ")}")
            }
        }
        Text(Strings.notes, fontWeight = FontWeight.Medium)
        Text(node?.comment().orEmpty().ifBlank { "—" })
    }
}

@Composable
fun ScoreGraph(state: ReviewState, session: ReviewSession, modifier: Modifier = Modifier) {
    val nodes = remember(state.tree, state.revision, state.mainlineLength) { mainlineList(state) }
    Canvas(
        modifier.pointerInput(nodes.size) {
            detectTapGestures { offset ->
                if (nodes.size <= 1) return@detectTapGestures
                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                val depth = (fraction * (nodes.size - 1)).roundToInt()
                session.jumpToDepth(depth)
            }
        },
    ) {
        drawRect(BoardPalette.panelLight)
        if (nodes.size < 2) return@Canvas
        val winrates = nodes.map { it.analysis?.root?.winrate }
        val scores = nodes.map { it.analysis?.root?.scoreLead }
        val losses = nodes.map { pointsLostOfPlayedMove(it) }
        val maxScore = scores.filterNotNull().maxOfOrNull { abs(it) } ?: 0.0
        val maxLoss = losses.filterNotNull().maxOfOrNull { abs(it) } ?: 0.0
        val scoreMax = scoreAxisMax(max(maxScore, maxLoss))
        val winMax = winrateAxisMax(
            winrates.filterNotNull().maxOfOrNull { abs(it - 0.5) * 100.0 } ?: 0.0,
        )
        fun xOf(index: Int) = size.width * index / (nodes.size - 1).toFloat()
        fun yOf(value: Double, axis: Double) = size.height * (0.5f - (value / axis).toFloat() * 0.45f)
        drawLine(Color(0x66FFFFFF), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
        if (state.settings.graphMode == GraphMode.WINRATE) {
            strokeSeries(winrates.map { it?.let { wr -> (wr - 0.5) * 100.0 } }, winMax, BoardPalette.winrate, ::xOf, ::yOf)
            strokeSeries(scores, scoreMax, BoardPalette.score, ::xOf, ::yOf)
        } else {
            strokeSeries(losses, scoreMax, BoardPalette.pointLoss, ::xOf, ::yOf)
        }
        val cursor = state.depth.coerceIn(0, nodes.lastIndex)
        drawCircle(BoardPalette.graphDot, 4.5f, Offset(xOf(cursor), size.height * 0.5f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.strokeSeries(
    values: List<Double?>,
    axis: Double,
    color: Color,
    xOf: (Int) -> Float,
    yOf: (Double, Double) -> Float,
) {
    val path = Path()
    var started = false
    values.forEachIndexed { index, value ->
        if (value == null) {
            started = false
            return@forEachIndexed
        }
        val x = xOf(index)
        val y = yOf(value, axis)
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(path, color, style = Stroke(width = 2f))
}

private fun mainlineList(state: ReviewState): List<GameNode> {
    val nodes = ArrayList<GameNode>()
    var current: GameNode? = state.tree.root
    while (current != null) {
        nodes += current
        current = current.mainChild()
    }
    return nodes
}

private fun Modifier.onFocus(session: ReviewSession): Modifier =
    onFocusChanged { session.typing = it.isFocused }
