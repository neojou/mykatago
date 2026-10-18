package com.neojou.mykatago.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoardStateTest {
    @Test
    fun capturesOneStone() {
        val board = placed(
            Stone.BLACK to Point(0, 1),
            Stone.BLACK to Point(1, 0),
            Stone.BLACK to Point(1, 2),
            Stone.WHITE to Point(1, 1),
        )
        val next = board.play(Move.Play(Stone.BLACK, Point(2, 1)), Ruleset.JAPANESE).getOrThrow()
        assertEquals(null, next.stoneAt(Point(1, 1)))
        assertEquals(Stone.BLACK, next.stoneAt(Point(2, 1)))
        assertEquals(1, next.capturesBlack)
    }

    @Test
    fun capturesTwoStones() {
        val board = placed(
            Stone.WHITE to Point(0, 0),
            Stone.WHITE to Point(1, 0),
            Stone.BLACK to Point(2, 0),
            Stone.BLACK to Point(0, 1),
        )
        val next = board.play(Move.Play(Stone.BLACK, Point(1, 1)), Ruleset.JAPANESE).getOrThrow()
        assertEquals(null, next.stoneAt(Point(0, 0)))
        assertEquals(null, next.stoneAt(Point(1, 0)))
        assertEquals(2, next.capturesBlack)
    }

    @Test
    fun simpleKoIsIllegal() {
        val board = placed(
            Stone.BLACK to Point(1, 0),
            Stone.BLACK to Point(1, 2),
            Stone.BLACK to Point(0, 1),
            Stone.WHITE to Point(2, 0),
            Stone.WHITE to Point(2, 2),
            Stone.WHITE to Point(3, 1),
            Stone.WHITE to Point(1, 1),
        )
        val afterBlack = board.play(Move.Play(Stone.BLACK, Point(2, 1)), Ruleset.JAPANESE).getOrThrow()
        assertEquals(1, afterBlack.lastCaptureCount)
        val ko = afterBlack.play(Move.Play(Stone.WHITE, Point(1, 1)), Ruleset.JAPANESE)
        assertTrue(ko.isFailure)
        val afterPass = afterBlack.play(Move.Pass(Stone.WHITE), Ruleset.JAPANESE).getOrThrow()
        assertTrue(afterPass.play(Move.Play(Stone.BLACK, Point(1, 1)), Ruleset.JAPANESE).isSuccess)
    }

    @Test
    fun snapbackIsNotKo() {
        // Black plays (0,0) and takes the white stone at (1,0). White plays back at
        // (1,0) and takes several black stones, so this is not a one-stone ko.
        val board = placed(
            Stone.WHITE to Point(1, 0),
            Stone.BLACK to Point(2, 0),
            Stone.BLACK to Point(0, 1),
            Stone.BLACK to Point(1, 1),
            Stone.WHITE to Point(2, 1),
            Stone.WHITE to Point(0, 2),
            Stone.WHITE to Point(1, 2),
        )
        val afterBlack = board.play(Move.Play(Stone.BLACK, Point(0, 0)), Ruleset.JAPANESE).getOrThrow()
        assertEquals(1, afterBlack.capturesBlack)
        val recapture = afterBlack.play(Move.Play(Stone.WHITE, Point(1, 0)), Ruleset.JAPANESE)
        assertTrue(recapture.isSuccess, recapture.exceptionOrNull()?.message)
        assertTrue(recapture.getOrThrow().capturesWhite >= 2)
    }

    @Test
    fun singleStoneSuicideIsIllegal() {
        val board = placed(
            Stone.WHITE to Point(0, 1),
            Stone.WHITE to Point(1, 0),
        )
        val played = board.play(Move.Play(Stone.BLACK, Point(0, 0)), Ruleset.JAPANESE)
        assertTrue(played.isFailure)
        assertIs<IllegalMoveException>(played.exceptionOrNull())
    }

    @Test
    fun occupiedPointIsIllegal() {
        val board = placed(Stone.BLACK to Point(0, 0))
        assertTrue(board.play(Move.Play(Stone.WHITE, Point(0, 0)), Ruleset.JAPANESE).isFailure)
    }

    private fun placed(vararg stones: Pair<Stone, Point>): BoardState {
        val black = stones.filter { it.first == Stone.BLACK }.map { it.second }
        val white = stones.filter { it.first == Stone.WHITE }.map { it.second }
        return BoardState.empty(9).withSetup(Setup(black, white, emptyList()))
    }
}
