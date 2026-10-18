package com.neojou.mykatago.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.neojou.mykatago.analysis.NodeAnalysis
import com.neojou.mykatago.analysis.formatLoss
import com.neojou.mykatago.analysis.formatVisits
import com.neojou.mykatago.analysis.hintsDrawn
import com.neojou.mykatago.analysis.pointsLostOfPlayedMove
import com.neojou.mykatago.go.GameNode
import com.neojou.mykatago.go.GameTree
import com.neojou.mykatago.go.Move
import com.neojou.mykatago.go.Point
import com.neojou.mykatago.go.Stone
import com.neojou.mykatago.go.columnLabel
import com.neojou.mykatago.go.gtpToPoint
import com.neojou.mykatago.go.pathFromRoot
import com.neojou.mykatago.go.starPoints
import com.neojou.mykatago.settings.BoardToggles
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun GoBoard(
    state: ReviewState,
    node: GameNode?,
    onPlay: (Point) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val tree = state.tree
    val board = state.board
    val toggles = state.toggles
    val analysis = node?.analysis
    val revision = state.revision
    val numbers = remember(node, state.showNumbers, revision) {
        if (!state.showNumbers || node == null) emptyMap() else moveNumbers(node)
    }
    Box(modifier.aspectRatio(1f)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(tree, node?.id) {
                    detectTapGestures { offset ->
                        pointAt(offset.x, offset.y, size.width.toFloat(), size.height.toFloat(), tree.boardSize)
                            ?.let(onPlay)
                    }
                },
        ) {
            val side = min(size.width, size.height)
            val label = side * 0.045f
            val cell = (side - label * 2f) / tree.boardSize
            val origin = label + cell / 2f
            val stoneRadius = cell * 0.505f
            fun center(point: Point) = Offset(origin + point.x * cell, origin + point.y * cell)

            drawRect(BoardPalette.board, size = Size(side, side))
            val span = tree.boardSize - 1
            val gridEnd = origin + span * cell
            for (index in 0 until tree.boardSize) {
                val x = origin + index * cell
                val y = origin + index * cell
                drawLine(BoardPalette.line, Offset(x, origin), Offset(x, gridEnd), strokeWidth = 1.2f)
                drawLine(BoardPalette.line, Offset(origin, y), Offset(gridEnd, y), strokeWidth = 1.2f)
            }
            for (star in starPoints(tree.boardSize)) {
                drawCircle(BoardPalette.line, radius = cell * 0.1f, center = center(star))
            }
            if (state.showCoords) {
                val style = TextStyle(color = Color(0xFF1A1A1A), fontSize = 11.sp)
                for (x in 0 until tree.boardSize) {
                    val labelText = columnLabel(x)
                    val measured = measurer.measure(labelText, style)
                    val cx = origin + x * cell - measured.size.width / 2f
                    drawText(measurer, labelText, topLeft = Offset(cx, 2f), style = style)
                    drawText(measurer, labelText, topLeft = Offset(cx, side - label + 2f), style = style)
                }
                for (y in 0 until tree.boardSize) {
                    val labelText = (tree.boardSize - y).toString()
                    val measured = measurer.measure(labelText, style)
                    val cy = origin + y * cell - measured.size.height / 2f
                    drawText(measurer, labelText, topLeft = Offset(2f, cy), style = style)
                    drawText(
                        measurer,
                        labelText,
                        topLeft = Offset(side - label + 4f, cy),
                        style = style,
                    )
                }
            }

            if (toggles.territory) drawTerritory(analysis, tree.boardSize, board, ::center, cell)
            if (toggles.policy) drawPolicy(analysis, tree.boardSize, board, ::center, cell, measurer)

            for (y in 0 until tree.boardSize) {
                for (x in 0 until tree.boardSize) {
                    val point = Point(x, y)
                    val stone = board.stoneAt(point) ?: continue
                    val color = if (stone == Stone.BLACK) BoardPalette.blackStone else BoardPalette.whiteStone
                    drawCircle(color, stoneRadius, center(point))
                    val mark = numbers[point]
                    if (mark != null) {
                        val ink = if (stone == Stone.BLACK) Color.White else Color.Black
                        val text = mark.toString()
                        val style = TextStyle(color = ink, fontSize = 10.sp)
                        val measured = measurer.measure(text, style)
                        val at = center(point)
                        drawText(
                            measurer,
                            text,
                            topLeft = Offset(at.x - measured.size.width / 2f, at.y - measured.size.height / 2f),
                            style = style,
                        )
                    }
                }
            }

            val last = board.lastMove
            if (last is Move.Play) {
                val stone = board.stoneAt(last.point)
                val ink = if (stone == Stone.WHITE) BoardPalette.blackStone else BoardPalette.whiteStone
                drawCircle(ink, stoneRadius * 0.28f, center(last.point), style = Stroke(width = 1.6f))
            }

            if (toggles.showDots && node != null) {
                for ((point, lost) in recentDots(node)) {
                    drawCircle(BoardPalette.evalColor(lost), stoneRadius * 0.28f, center(point))
                }
            }

            if (toggles.nextMoves && node != null) {
                for (child in node.children) {
                    val move = child.move as? Move.Play ?: continue
                    val ink = if (move.player == Stone.BLACK) Color(0xFF222222) else Color(0xFFF7F7F7)
                    drawCircle(ink, stoneRadius * 0.92f, center(move.point), style = Stroke(width = 2.2f))
                }
            }

            val current = node
            val info = current?.analysis
            if (current != null && info != null && hintsDrawn(toggles.topMoves, toggles.policy)) {
                drawHints(info, current, tree.boardSize, stoneRadius, ::center, measurer)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHints(
    analysis: NodeAnalysis,
    node: GameNode,
    boardSize: Int,
    stoneRadius: Float,
    center: (Point) -> Offset,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val childPoints = node.children.mapNotNull { child ->
        when (val move = child.move) {
            is Move.Play -> move.point
            else -> null
        }
    }.toSet()
    for (candidate in analysis.candidates()) {
        val point = gtpToPoint(candidate.move, boardSize) ?: continue
        val low = candidate.visits < com.neojou.mykatago.analysis.LOW_VISITS &&
            candidate.order != 0 &&
            point !in childPoints
        val radius = stoneRadius * if (low) 0.7f else 0.98f
        val alpha = if (low) 0.6f else 0.85f
        val at = center(point)
        drawCircle(BoardPalette.board, radius, at)
        drawCircle(BoardPalette.evalColor(candidate.pointsLost).copy(alpha = alpha), radius * 0.92f, at)
        if (candidate.order == 0) {
            drawCircle(BoardPalette.topMoveBorder, radius, at, style = Stroke(width = 2.4f))
        }
        if (low) continue
        val ink = if (com.neojou.mykatago.analysis.evaluationClass(candidate.pointsLost) <= 1) {
            Color.White
        } else {
            Color.Black
        }
        val loss = formatLoss(candidate.pointsLost)
        val visits = formatVisits(candidate.visits)
        val style = TextStyle(color = ink, fontSize = 10.sp)
        val first = measurer.measure(loss, style)
        val second = measurer.measure(visits, style)
        drawText(measurer, loss, Offset(at.x - first.size.width / 2f, at.y - first.size.height), style)
        drawText(measurer, visits, Offset(at.x - second.size.width / 2f, at.y), style)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolicy(
    analysis: NodeAnalysis?,
    boardSize: Int,
    board: com.neojou.mykatago.go.BoardState,
    center: (Point) -> Offset,
    cell: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val policy = analysis?.policy ?: return
    var max = 0f
    for (value in policy) if (value > max) max = value
    if (max <= 0f) return
    var best = -1
    for (index in policy.indices) if (policy[index] > (policy.getOrNull(best) ?: -1f)) best = index
    for (index in policy.indices) {
        val value = policy[index]
        if (value < 0f) continue
        val x = index % boardSize
        val y = index / boardSize
        val point = Point(x, y)
        if (board.stoneAt(point) != null) continue
        val at = center(point)
        val alpha = (value / max * 0.5f).coerceIn(0f, 0.5f)
        drawRoundRect(
            color = BoardPalette.winrate.copy(alpha = alpha),
            topLeft = Offset(at.x - cell * 0.45f, at.y - cell * 0.45f),
            size = Size(cell * 0.9f, cell * 0.9f),
            cornerRadius = CornerRadius(4f, 4f),
        )
        if (index == best) {
            drawCircle(BoardPalette.topMoveBorder, cell * 0.46f, at, style = Stroke(2f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTerritory(
    analysis: NodeAnalysis?,
    boardSize: Int,
    board: com.neojou.mykatago.go.BoardState,
    center: (Point) -> Offset,
    cell: Float,
) {
    val ownership = analysis?.ownership ?: return
    val count = min(ownership.size, boardSize * boardSize)
    for (index in 0 until count) {
        val x = index % boardSize
        val y = index / boardSize
        val point = Point(x, y)
        if (board.stoneAt(point) != null) continue
        val value = ownership[index]
        val mag = kotlin.math.abs(value).toDouble().pow(1.33).toFloat().coerceIn(0f, 1f)
        if (mag < 0.02f) continue
        val color = if (value >= 0f) Color.Black.copy(alpha = mag * 0.55f) else Color.White.copy(alpha = mag * 0.7f)
        val at = center(point)
        drawRect(color, Offset(at.x - cell * 0.38f, at.y - cell * 0.38f), Size(cell * 0.76f, cell * 0.76f))
    }
}

private fun recentDots(node: GameNode): List<Pair<Point, Double>> {
    val dots = ArrayList<Pair<Point, Double>>(3)
    var current: GameNode? = node
    while (current != null && dots.size < 3) {
        val move = current.move
        val lost = pointsLostOfPlayedMove(current)
        if (move is Move.Play && lost != null) dots += move.point to lost
        current = current.parent
    }
    return dots
}

private fun moveNumbers(node: GameNode): Map<Point, Int> {
    val numbers = LinkedHashMap<Point, Int>()
    for (step in pathFromRoot(node)) {
        val move = step.move
        if (move is Move.Play) numbers[move.point] = step.depth
    }
    return numbers
}

private fun pointAt(x: Float, y: Float, width: Float, height: Float, boardSize: Int): Point? {
    val side = min(width, height)
    val label = side * 0.045f
    val cell = (side - label * 2f) / boardSize
    val origin = label + cell / 2f
    val gx = ((x - origin) / cell).roundToInt()
    val gy = ((y - origin) / cell).roundToInt()
    if (gx !in 0 until boardSize || gy !in 0 until boardSize) return null
    val dx = x - (origin + gx * cell)
    val dy = y - (origin + gy * cell)
    if (dx * dx + dy * dy > cell * cell * 0.36f) return null
    return Point(gx, gy)
}
