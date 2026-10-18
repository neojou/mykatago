package com.neojou.mykatago.go

import kotlin.test.Test
import kotlin.test.assertEquals

class SgfWriterTest {
    @Test
    fun roundTripKeepsMovesAndVariation() {
        val source = "(;FF[4]GM[1]SZ[9]PB[黑方];B[aa];W[bb](;B[cc])(;B[dd]C[變化]))"
        val once = SgfParser.parse(source).getOrThrow()
        val twice = SgfParser.parse(SgfWriter.write(once)).getOrThrow()
        val first = movesOf(once)
        val second = movesOf(twice)
        assertEquals(first, second)
        assertEquals("黑方", twice.playerBlack)
        val branch = twice.root.mainChild()!!.mainChild()!!
        assertEquals(2, branch.children.size)
        assertEquals("變化", branch.children[1].comment())
    }

    private fun movesOf(tree: GameTree): List<String> {
        val out = ArrayList<String>()
        fun walk(node: GameNode) {
            node.move?.let { move ->
                out += when (move) {
                    is Move.Pass -> "${move.player}-pass"
                    is Move.Play -> "${move.player}-${pointToSgf(move.point)}"
                }
            }
            node.children.forEach(::walk)
        }
        walk(tree.root)
        return out
    }
}
