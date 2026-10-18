package com.neojou.mykatago.analysis

import com.neojou.mykatago.go.GameNode
import com.neojou.mykatago.go.GameTree
import com.neojou.mykatago.go.Move
import com.neojou.mykatago.go.Stone
import com.neojou.mykatago.go.pathFromRoot
import com.neojou.mykatago.go.pointToGtp
import kotlinx.serialization.encodeToString

object QueryBuilder {
    const val PRIORITY_POSITION = 1000
    const val PRIORITY_DEEPER = 100
    const val PRIORITY_GAME = -100
    const val PRIORITY_REANALYZE = -1_000_000

    fun positionJson(
        tree: GameTree,
        node: GameNode,
        id: String,
        maxVisits: Int?,
        maxTime: Double?,
        wideRootNoise: Double,
        includeOwnership: Boolean,
        reportEvery: Double?,
        priority: Int,
    ): String {
        val path = pathFromRoot(node)
        val moves = path.mapNotNull { it.move }
        val query = AnalysisQuery(
            id = id,
            moves = moves.map { move -> listOf(move.player.katago(), gtpOf(move, tree.boardSize)) },
            initialStones = initialStones(tree).map { (stone, point) ->
                listOf(stone.katago(), pointToGtp(point, tree.boardSize))
            },
            initialPlayer = initialPlayer(tree, path),
            rules = tree.rules.katago,
            komi = tree.komi,
            boardXSize = tree.boardSize,
            boardYSize = tree.boardSize,
            analyzeTurns = listOf(moves.size),
            maxVisits = maxVisits,
            includeOwnership = includeOwnership,
            includePolicy = true,
            reportDuringSearchEvery = reportEvery,
            priority = priority,
            overrideSettings = OverrideSettings(
                reportAnalysisWinratesAs = "BLACK",
                wideRootNoise = wideRootNoise,
                maxTime = maxTime,
            ),
        )
        return AppJson.format.encodeToString(query)
    }

    fun gtpOf(move: Move, size: Int): String = when (move) {
        is Move.Pass -> "pass"
        is Move.Play -> pointToGtp(move.point, size)
    }

    fun initialStones(tree: GameTree): List<Pair<Stone, com.neojou.mykatago.go.Point>> {
        val setup = tree.root.setup
        return setup.black.map { Stone.BLACK to it } + setup.white.map { Stone.WHITE to it }
    }

    fun initialPlayer(tree: GameTree, path: List<GameNode>): String {
        val pl = tree.root.props["PL"]?.firstOrNull()
        if (pl != null) return if (pl.startsWith("W", ignoreCase = true)) "W" else "B"
        if (tree.handicap >= 2) return "W"
        val first = path.firstNotNullOfOrNull { it.move }
        return if (first?.player == Stone.WHITE) "W" else "B"
    }
}
