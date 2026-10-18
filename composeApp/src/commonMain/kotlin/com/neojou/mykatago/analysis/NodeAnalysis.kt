package com.neojou.mykatago.analysis

import com.neojou.mykatago.go.GameNode
import com.neojou.mykatago.go.Stone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

data class RootInfo(
    val currentPlayer: String,
    val visits: Int,
    val winrate: Double,
    val scoreLead: Double,
)

data class CandidateMove(
    val move: String,
    val order: Int,
    val visits: Int,
    val winrate: Double,
    val scoreLead: Double,
    val prior: Double,
    val pv: List<String>,
    val pointsLost: Double,
)

class NodeAnalysis(
    val root: RootInfo?,
    val moves: Map<String, CandidateMove>,
    val ownership: FloatArray?,
    val policy: FloatArray?,
    val visitsRequested: Int,
    val completed: Boolean,
) {
    fun candidates(): List<CandidateMove> =
        moves.values.sortedWith(compareBy({ it.order }, { it.pointsLost }))
}

val EVAL_THRESHOLDS = doubleArrayOf(12.0, 6.0, 3.0, 1.5, 0.5, 0.0)
const val MISTAKE_POINTS = 3.0
const val LOW_VISITS = 25

/** 0 is the worst class. A loss of 3.0 is class 2. */
fun evaluationClass(pointsLost: Double): Int {
    var index = 0
    while (index < 5 && pointsLost < EVAL_THRESHOLDS[index]) index++
    return index
}

fun signOfPlayer(currentPlayer: String?): Double = if (currentPlayer == "W") -1.0 else 1.0

fun pointsLost(nextPlayer: String?, rootLead: Double, moveLead: Double): Double =
    signOfPlayer(nextPlayer) * (rootLead - moveLead)

/**
 * Point loss of the move stored on [node], using the two nodes' rootInfo.
 * Positive means the mover gave points away.
 */
fun pointsLostOfPlayedMove(node: GameNode): Double? {
    val parentLead = node.parent?.analysis?.root?.scoreLead ?: return null
    val lead = node.analysis?.root?.scoreLead ?: return null
    val player = node.move?.player ?: return null
    val sign = if (player == Stone.BLACK) 1.0 else -1.0
    return sign * (parentLead - lead)
}

fun mergeAnalysis(old: NodeAnalysis?, response: AnalysisResponse, visitsRequested: Int): NodeAnalysis {
    val root = response.rootInfo?.let {
        RootInfo(
            currentPlayer = it.currentPlayer ?: old?.root?.currentPlayer ?: "B",
            visits = it.visits,
            winrate = it.winrate,
            scoreLead = it.scoreLead,
        )
    } ?: old?.root
    val merged = LinkedHashMap<String, CandidateMove>()
    old?.moves?.forEach { (key, value) -> merged[key] = value.copy(order = 999) }
    val rootLead = root?.scoreLead ?: 0.0
    for (info in response.moveInfos) {
        val incoming = CandidateMove(
            move = info.move,
            order = info.order,
            visits = info.visits,
            winrate = info.winrate,
            scoreLead = info.scoreLead,
            prior = info.prior,
            pv = info.pv,
            pointsLost = pointsLost(root?.currentPlayer, rootLead, info.scoreLead),
        )
        val previous = merged[info.move]
        if (previous == null || incoming.visits >= previous.visits) merged[info.move] = incoming
    }
    val during = response.isDuringSearch == true
    return NodeAnalysis(
        root = root,
        moves = merged,
        ownership = response.ownership?.map { it.toFloat() }?.toFloatArray() ?: old?.ownership,
        policy = response.policy?.map { it.toFloat() }?.toFloatArray() ?: old?.policy,
        visitsRequested = maxOf(old?.visitsRequested ?: 0, visitsRequested),
        completed = !during && response.noResults != true,
    )
}

/** Copy the child position's rootInfo into the parent's candidate for that move. */
fun syncChildRoot(parent: NodeAnalysis, move: String, childRoot: RootInfo): NodeAnalysis {
    val parentRoot = parent.root ?: return parent
    val existing = parent.moves[move]
    val updated = CandidateMove(
        move = move,
        order = existing?.order ?: 999,
        visits = maxOf(existing?.visits ?: 0, childRoot.visits),
        winrate = childRoot.winrate,
        scoreLead = childRoot.scoreLead,
        prior = existing?.prior ?: 0.0,
        pv = existing?.pv ?: listOf(move),
        pointsLost = pointsLost(parentRoot.currentPlayer, parentRoot.scoreLead, childRoot.scoreLead),
    )
    return NodeAnalysis(
        root = parent.root,
        moves = parent.moves + (move to updated),
        ownership = parent.ownership,
        policy = parent.policy,
        visitsRequested = parent.visitsRequested,
        completed = parent.completed,
    )
}

fun shouldApply(responseGeneration: Long, currentGeneration: Long): Boolean =
    responseGeneration == currentGeneration

fun deeperVisits(alreadyRequested: Int, maxVisits: Int): Int = alreadyRequested + maxVisits

fun hintsDrawn(topMoves: Boolean, policy: Boolean): Boolean = topMoves && !policy

fun formatSigned1(value: Double): String {
    val tenths = round(value * 10.0).toInt()
    val negative = tenths < 0
    val absTenths = abs(tenths)
    val body = "${absTenths / 10}.${absTenths % 10}"
    return if (negative) "-$body" else "+$body"
}

fun formatUnsigned1(value: Double): String {
    val tenths = round(abs(value) * 10.0).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** Best move (0 loss) is `+0.0`. A 0.1 loss is `-0.1`. */
fun formatLoss(pointsLost: Double): String = formatSigned1(-pointsLost)

fun formatVisits(visits: Int): String = when {
    visits < 1_000 -> visits.toString()
    visits < 100_000 -> {
        val tenths = round(visits / 100.0).toInt()
        val whole = tenths / 10
        val frac = tenths % 10
        if (frac == 0) "${whole}k" else "$whole.${frac}k"
    }
    visits < 1_000_000 -> "${visits / 1000}k"
    else -> "${visits / 1_000_000}M"
}

fun formatWinrate(winrateBlack: Double): String {
    val who = if (winrateBlack >= 0.5) "黑" else "白"
    val percent = max(winrateBlack, 1.0 - winrateBlack) * 100.0
    return "$who ${formatUnsigned1(percent)}%"
}

fun formatScore(scoreLeadBlack: Double): String {
    val who = if (scoreLeadBlack >= 0.0) "黑" else "白"
    return "$who+${formatUnsigned1(scoreLeadBlack)}"
}

fun scoreAxisMax(maxAbsScore: Double): Double {
    if (maxAbsScore <= 0.0) return 5.0
    return max(5.0, kotlin.math.ceil(maxAbsScore / 5.0) * 5.0)
}

fun winrateAxisMax(maxAbsDeviationPoints: Double): Double {
    if (maxAbsDeviationPoints <= 0.0) return 10.0
    return max(10.0, kotlin.math.ceil(maxAbsDeviationPoints / 10.0) * 10.0)
}

fun findMistake(from: GameNode, forward: Boolean): GameNode {
    if (forward) {
        var node = from.mainChild()
        var end = from
        while (node != null) {
            end = node
            val lost = pointsLostOfPlayedMove(node)
            if (lost != null && lost >= MISTAKE_POINTS) {
                val stop = node.parent ?: node
                if (stop != from) return stop
            }
            node = node.mainChild()
        }
        return end
    }
    var node = from.parent ?: return from
    while (true) {
        val lost = pointsLostOfPlayedMove(node)
        if (lost != null && lost >= MISTAKE_POINTS) return node.parent ?: node
        node = node.parent ?: return node
    }
}

fun planWholeGame(
    root: GameNode,
    fromDepth: Int,
    toDepth: Int,
    mistakesOnly: Boolean,
): List<GameNode> {
    val line = ArrayList<GameNode>()
    var node: GameNode? = root
    while (node != null) {
        if (node.depth in fromDepth..toDepth) line += node
        node = node.mainChild()
    }
    if (!mistakesOnly) return line
    return line.filter { candidate ->
        val self = pointsLostOfPlayedMove(candidate)
        val child = candidate.mainChild()?.let(::pointsLostOfPlayedMove)
        (self != null && self >= MISTAKE_POINTS) || (child != null && child >= MISTAKE_POINTS)
    }
}
