package com.neojou.mykatago.analysis

import com.neojou.mykatago.go.GameTree
import com.neojou.mykatago.go.Move
import com.neojou.mykatago.go.Point
import com.neojou.mykatago.go.SgfParser
import com.neojou.mykatago.go.Stone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryBuilderTest {
    @Test
    fun everyQueryReportsBlackWinrate() {
        val tree = SgfParser.parse("(;FF[4]SZ[19]KM[6.5]RU[Japanese];B[pd];W[dd])").getOrThrow()
        val node = tree.root.mainChild()!!.mainChild()!!
        val normal = QueryBuilder.positionJson(
            tree, node, "g1:pos:1",
            maxVisits = 500,
            maxTime = 8.0,
            wideRootNoise = 0.04,
            includeOwnership = true,
            reportEvery = 1.0,
            priority = QueryBuilder.PRIORITY_POSITION,
        )
        val ponder = QueryBuilder.positionJson(
            tree, node, "g1:ponder:1",
            maxVisits = 10_000_000,
            maxTime = null,
            wideRootNoise = 0.04,
            includeOwnership = true,
            reportEvery = 0.25,
            priority = QueryBuilder.PRIORITY_POSITION,
        )
        val deeper = QueryBuilder.positionJson(
            tree, node, "g1:deep:1",
            maxVisits = deeperVisits(500, 500),
            maxTime = null,
            wideRootNoise = 0.04,
            includeOwnership = true,
            reportEvery = 1.0,
            priority = QueryBuilder.PRIORITY_DEEPER,
        )
        for (json in listOf(normal, ponder, deeper)) {
            val parsed = AppJson.format.decodeFromString<AnalysisQuery>(json)
            assertEquals("BLACK", parsed.overrideSettings.reportAnalysisWinratesAs)
            assertEquals(0.04, parsed.overrideSettings.wideRootNoise)
        }
        val normalQuery = AppJson.format.decodeFromString<AnalysisQuery>(normal)
        assertEquals(8.0, normalQuery.overrideSettings.maxTime)
        assertEquals(listOf(listOf("B", "Q16"), listOf("W", "D16")), normalQuery.moves)
        assertEquals(listOf(2), normalQuery.analyzeTurns)
        assertTrue(!ponder.contains("\"maxTime\""))
        assertTrue(!deeper.contains("\"maxTime\""))
        assertEquals(1000, deeperVisits(500, 500))
    }

    @Test
    fun hintsAreHiddenWhilePolicyIsOn() {
        assertEquals(false, hintsDrawn(topMoves = true, policy = true))
        assertEquals(true, hintsDrawn(topMoves = true, policy = false))
    }

    @Test
    fun emptyBoardQueryHasNoMoves() {
        val tree = GameTree.empty()
        val json = QueryBuilder.positionJson(
            tree, tree.root, "q",
            maxVisits = 25,
            maxTime = null,
            wideRootNoise = 0.04,
            includeOwnership = false,
            reportEvery = null,
            priority = QueryBuilder.PRIORITY_GAME,
        )
        val query = AppJson.format.decodeFromString<AnalysisQuery>(json)
        assertEquals(emptyList(), query.moves)
        assertEquals(listOf(0), query.analyzeTurns)
        assertEquals("japanese", query.rules)
        assertEquals(Move.Play(Stone.BLACK, Point(0, 0)).player.katago(), "B")
    }
}
