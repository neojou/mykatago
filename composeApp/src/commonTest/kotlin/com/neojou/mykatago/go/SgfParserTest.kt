package com.neojou.mykatago.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SgfParserTest {
    @Test
    fun straightGame() {
        val tree = parseOk("(;FF[4]SZ[19]KM[6.5];B[pd];W[dd])")
        val black = tree.root.mainChild()!!
        val white = black.mainChild()!!
        assertEquals(Move.Play(Stone.BLACK, Point(15, 3)), black.move)
        assertEquals(Move.Play(Stone.WHITE, Point(3, 3)), white.move)
        assertEquals(6.5, tree.komi)
    }

    @Test
    fun firstChildIsMainLine() {
        val tree = parseOk("(;FF[4]SZ[9];B[aa](;W[bb])(;W[cc]))")
        val black = tree.root.mainChild()!!
        assertEquals(2, black.children.size)
        assertEquals(Point(1, 1), (black.children[0].move as Move.Play).point)
        assertEquals(Point(2, 2), (black.children[1].move as Move.Play).point)
    }

    @Test
    fun escapesAndSoftBreak() {
        val escaped = parseOk("(;FF[4]C[a\\]b])")
        assertEquals("a]b", escaped.root.comment())
        val soft = parseOk("(;FF[4]C[foo\\\nbar])")
        assertEquals("foobar", soft.root.comment())
    }

    @Test
    fun setupAndChineseText() {
        val tree = parseOk("(;FF[4]SZ[9]PB[黑方]C[註解]AB[aa]AW[bb]AE[cc])")
        assertEquals("黑方", tree.playerBlack)
        assertEquals("註解", tree.root.comment())
        assertEquals(listOf(Point(0, 0)), tree.root.setup.black)
        assertEquals(listOf(Point(1, 1)), tree.root.setup.white)
        assertEquals(listOf(Point(2, 2)), tree.root.setup.empty)
    }

    @Test
    fun passMoves() {
        val tree = parseOk("(;FF[4]SZ[19];B[];W[tt])")
        assertIs<Move.Pass>(tree.root.mainChild()!!.move)
        assertIs<Move.Pass>(tree.root.mainChild()!!.mainChild()!!.move)
    }

    @Test
    fun handicapWithoutAbPlacesStones() {
        val tree = parseOk("(;FF[4]SZ[19]HA[2])")
        assertEquals(listOf(Point(15, 3), Point(3, 15)), tree.root.setup.black)
    }

    @Test
    fun rectangularBoardIsRejected() {
        val error = SgfParser.parse("(;FF[4]SZ[19:13])").exceptionOrNull()
        assertIs<SgfParseException>(error)
        assertTrue(error.message!!.contains("長方形"))
    }

    private fun parseOk(text: String): GameTree = SgfParser.parse(text).getOrThrow()
}
