package com.neojou.mykatago.analysis

import com.neojou.mykatago.go.GameTree
import com.neojou.mykatago.go.Move
import com.neojou.mykatago.go.Point
import com.neojou.mykatago.go.Stone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeAnalysisTest {
    @Test
    fun blackPointLossUsesRootLead() {
        val lost = pointsLost("B", rootLead = 0.4, moveLead = 0.3)
        assertEquals(0.1, lost, 1e-9)
        val whiteToMove = pointsLost("W", rootLead = 0.4, moveLead = 0.3)
        assertEquals(-0.1, whiteToMove, 1e-9)
    }

    @Test
    fun playedMovePointLoss() {
        val tree = GameTree.empty()
        val black = tree.addMove(tree.root, Move.Play(Stone.BLACK, Point(0, 0)))
        tree.root.analysis = rootLead(0.0, "B")
        black.analysis = rootLead(-4.0, "W")
        assertEquals(4.0, pointsLostOfPlayedMove(black)!!, 1e-9)
    }

    @Test
    fun classBoundaries() {
        assertEquals(0, evaluationClass(12.0))
        assertEquals(1, evaluationClass(11.9))
        assertEquals(1, evaluationClass(6.0))
        assertEquals(2, evaluationClass(5.9))
        assertEquals(2, evaluationClass(3.0))
        assertEquals(3, evaluationClass(2.9))
        assertEquals(3, evaluationClass(1.5))
        assertEquals(4, evaluationClass(1.4))
        assertEquals(4, evaluationClass(0.5))
        assertEquals(5, evaluationClass(0.49))
        assertEquals(5, evaluationClass(0.0))
    }

    @Test
    fun mergeKeepsHigherVisitsAndResetsOldOrder() {
        val old = NodeAnalysis(
            root = RootInfo("B", 10, 0.5, 0.0),
            moves = mapOf("Q16" to candidate("Q16", order = 0, visits = 10)),
            ownership = floatArrayOf(0.2f),
            policy = null,
            visitsRequested = 10,
            completed = true,
        )
        val response = AnalysisResponse(
            isDuringSearch = false,
            rootInfo = RootInfoDto("B", 50, 0.52, 0.4),
            moveInfos = listOf(
                MoveInfoDto("Q16", order = 1, visits = 40, winrate = 0.51, scoreLead = 0.3, prior = 0.2),
                MoveInfoDto("D4", order = 0, visits = 50, winrate = 0.52, scoreLead = 0.4, prior = 0.3),
            ),
            ownership = listOf(0.9),
        )
        val merged = mergeAnalysis(old, response, visitsRequested = 50)
        assertEquals(40, merged.moves.getValue("Q16").visits)
        assertEquals(1, merged.moves.getValue("Q16").order)
        assertEquals(50, merged.moves.getValue("D4").visits)
        assertEquals(0.9f, merged.ownership!![0])
        assertTrue(merged.completed)
        val partial = mergeAnalysis(
            merged,
            AnalysisResponse(
                isDuringSearch = true,
                moveInfos = listOf(MoveInfoDto("D16", order = 2, visits = 1, scoreLead = 0.0)),
            ),
            visitsRequested = 80,
        )
        assertEquals(999, partial.moves.getValue("Q16").order)
        assertEquals(40, partial.moves.getValue("Q16").visits)
        assertEquals(false, partial.completed)
    }

    @Test
    fun childRootIsCopiedOntoParentMove() {
        val parent = NodeAnalysis(
            root = RootInfo("B", 20, 0.6, 1.0),
            moves = emptyMap(),
            ownership = null,
            policy = null,
            visitsRequested = 20,
            completed = true,
        )
        val synced = syncChildRoot(parent, "Q16", RootInfo("W", 30, 0.55, 0.7))
        val move = synced.moves.getValue("Q16")
        assertEquals(0.7, move.scoreLead, 1e-9)
        assertEquals(0.3, move.pointsLost, 1e-9)
        assertEquals(30, move.visits)
    }

    @Test
    fun formats() {
        assertEquals("+0.0", formatLoss(0.0))
        assertEquals("-0.1", formatLoss(0.1))
        assertEquals("52", formatVisits(52))
        assertEquals("1.2k", formatVisits(1200))
        assertEquals("黑 52.5%", formatWinrate(0.525))
        assertEquals("白+0.3", formatScore(-0.3))
    }

    @Test
    fun ownershipIndexZeroIsTopLeft() {
        val analysis = mergeAnalysis(
            null,
            AnalysisResponse(ownership = List(361) { index -> if (index == 0) 0.8 else 0.0 }),
            visitsRequested = 1,
        )
        assertEquals(0.8f, analysis.ownership!![0])
    }

    @Test
    fun generationMustMatch() {
        assertTrue(shouldApply(3, 3))
        assertEquals(false, shouldApply(2, 3))
    }

    private fun rootLead(lead: Double, player: String) = NodeAnalysis(
        root = RootInfo(player, 10, 0.5, lead),
        moves = emptyMap(),
        ownership = null,
        policy = null,
        visitsRequested = 10,
        completed = true,
    )

    private fun candidate(move: String, order: Int, visits: Int) = CandidateMove(
        move = move,
        order = order,
        visits = visits,
        winrate = 0.5,
        scoreLead = 0.0,
        prior = 0.1,
        pv = listOf(move),
        pointsLost = 0.0,
    )
}
