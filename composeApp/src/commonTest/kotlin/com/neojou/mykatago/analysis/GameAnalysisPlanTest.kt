package com.neojou.mykatago.analysis

import com.neojou.mykatago.go.GameTree
import com.neojou.mykatago.go.Move
import com.neojou.mykatago.go.Point
import com.neojou.mykatago.go.Stone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GameAnalysisPlanTest {
    @Test
    fun forwardMistakeStopsOnParentAndSkipsTheCurrentOne() {
        val nodes = line()
        setLead(nodes[0], 0.0, "B")
        setLead(nodes[1], -4.0, "W")
        setLead(nodes[2], -4.0, "B")
        setLead(nodes[3], -9.0, "W")
        val found = findMistake(nodes[0], forward = true)
        assertSame(nodes[2], found)
        assertSame(nodes[3], findMistake(nodes[2], forward = true))
    }

    @Test
    fun backwardMistakeStopsOnEarlierParent() {
        val nodes = line()
        setLead(nodes[0], 0.0, "B")
        setLead(nodes[1], -4.0, "W")
        setLead(nodes[2], -4.0, "B")
        setLead(nodes[3], -9.0, "W")
        assertSame(nodes[0], findMistake(nodes[2], forward = false))
        assertSame(nodes[0], findMistake(nodes[0], forward = false))
    }

    @Test
    fun rangeAndMistakeFilter() {
        val nodes = line()
        setLead(nodes[0], 0.0, "B")
        setLead(nodes[1], -4.0, "W")
        setLead(nodes[2], -4.0, "B")
        setLead(nodes[3], -1.0, "W")
        val all = planWholeGame(nodes[0], fromDepth = 1, toDepth = 2, mistakesOnly = false)
        assertEquals(listOf(1, 2), all.map { it.depth })
        val mistakes = planWholeGame(nodes[0], fromDepth = 0, toDepth = 99, mistakesOnly = true)
        assertEquals(setOf(0, 1), mistakes.map { it.depth }.toSet())
    }

    @Test
    fun graphAxes() {
        assertEquals(10.0, scoreAxisMax(7.0))
        assertEquals(20.0, winrateAxisMax(12.0))
        assertEquals(5.0, scoreAxisMax(0.0))
    }

    private fun line(): List<com.neojou.mykatago.go.GameNode> {
        val tree = GameTree.empty()
        val nodes = ArrayList<com.neojou.mykatago.go.GameNode>()
        nodes += tree.root
        var current = tree.root
        val moves = listOf(
            Move.Play(Stone.BLACK, Point(0, 0)),
            Move.Play(Stone.WHITE, Point(1, 0)),
            Move.Play(Stone.BLACK, Point(2, 0)),
        )
        for (move in moves) {
            current = tree.addMove(current, move)
            nodes += current
        }
        return nodes
    }

    private fun setLead(node: com.neojou.mykatago.go.GameNode, lead: Double, player: String) {
        node.analysis = NodeAnalysis(
            root = RootInfo(player, 10, 0.5, lead),
            moves = emptyMap(),
            ownership = null,
            policy = null,
            visitsRequested = 10,
            completed = true,
        )
    }
}
