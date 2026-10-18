package com.neojou.mykatago.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavigationTest {
    @Test
    fun redoFollowsFirstChild() {
        val tree = parse("(;FF[4]SZ[9];B[aa](;W[bb];B[cc])(;W[dd]))")
        val end = redo(tree.root, 3)
        assertEquals(Point(2, 2), (end.move as Move.Play).point)
        assertSame(tree.root, undo(end, 9))
    }

    @Test
    fun branchUpAndDown() {
        val tree = parse("(;FF[4]SZ[9];B[aa](;W[bb])(;W[cc]))")
        val main = tree.root.mainChild()!!.children[0]
        val other = branchDown(main)
        assertEquals(Point(2, 2), (other.move as Move.Play).point)
        assertSame(main, branchUp(other))
    }

    @Test
    fun goToDepthStaysOnMainline() {
        val tree = parse("(;FF[4]SZ[9];B[aa];W[bb](;B[cc])(;B[dd]))")
        assertEquals(3, mainlineLength(tree.root))
        val at2 = mainlineAt(tree.root, 2)
        assertEquals(Point(1, 1), (at2.move as Move.Play).point)
        val variation = at2.children[1]
        assertFalse(onMainline(variation))
        assertTrue(onMainline(toMainline(variation)))
    }

    private fun parse(text: String) = SgfParser.parse(text).getOrThrow()
}
