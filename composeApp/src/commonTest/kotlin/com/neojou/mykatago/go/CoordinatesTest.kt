package com.neojou.mykatago.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CoordinatesTest {
    @Test
    fun sgfPdIsQ16On19() {
        val coord = parseSgfCoord("pd", 19)
        val point = assertIs<SgfCoord.At>(coord).point
        assertEquals(Point(15, 3), point)
        assertEquals("Q16", pointToGtp(point, 19))
        assertEquals(point, gtpToPoint("Q16", 19))
    }

    @Test
    fun column8IsJ() {
        assertEquals("J", columnLabel(8))
        assertEquals("I", "ABCDEFGHI"[8].toString())
        assertEquals(null, gtpToPoint("I16", 19))
    }

    @Test
    fun aaIsTopLeft() {
        assertEquals(Point(0, 0), (parseSgfCoord("aa", 19) as SgfCoord.At).point)
    }

    @Test
    fun passForms() {
        assertIs<SgfCoord.Pass>(parseSgfCoord("", 19))
        assertIs<SgfCoord.Pass>(parseSgfCoord("tt", 19))
        assertIs<SgfCoord.Pass>(parseSgfCoord("tt", 9))
        assertNull(gtpToPoint("pass", 19))
    }

    @Test
    fun nineAndThirteenRoundTrip() {
        for (size in listOf(9, 13)) {
            val point = Point(size - 1, 0)
            val gtp = pointToGtp(point, size)
            assertEquals(point, gtpToPoint(gtp, size))
            val sgf = pointToSgf(point)
            assertEquals(point, (parseSgfCoord(sgf, size) as SgfCoord.At).point)
        }
    }
}
