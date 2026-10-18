package com.neojou.mykatago.go

/** GTP columns skip I. */
const val GTP_COLUMNS = "ABCDEFGHJKLMNOPQRST"

fun columnLabel(x: Int): String = GTP_COLUMNS[x].toString()

fun pointToGtp(point: Point, size: Int): String = "${columnLabel(point.x)}${size - point.y}"

fun gtpToPoint(gtp: String, size: Int): Point? {
    if (gtp.equals("pass", ignoreCase = true)) return null
    if (gtp.length < 2) return null
    val x = GTP_COLUMNS.indexOf(gtp[0].uppercaseChar())
    val row = gtp.substring(1).toIntOrNull() ?: return null
    val y = size - row
    if (x !in 0 until size || y !in 0 until size) return null
    return Point(x, y)
}

fun pointToSgf(point: Point): String = "${'a' + point.x}${'a' + point.y}"

sealed interface SgfCoord {
    data object Pass : SgfCoord
    data class At(val point: Point) : SgfCoord
}

/**
 * Empty value and `tt` on boards up to 19 are pass.
 * Any other coordinate outside the board throws [SgfParseException].
 */
fun parseSgfCoord(value: String, size: Int): SgfCoord {
    if (value.isEmpty()) return SgfCoord.Pass
    if (value.length != 2) throw SgfParseException("座標無效：$value")
    if (size <= 19 && value.equals("tt", ignoreCase = true)) return SgfCoord.Pass
    val x = value[0].lowercaseChar() - 'a'
    val y = value[1].lowercaseChar() - 'a'
    if (x !in 0 until size || y !in 0 until size) throw SgfParseException("座標超出棋盤：$value")
    return SgfCoord.At(Point(x, y))
}

fun starPoints(size: Int): List<Point> = when (size) {
    19 -> listOf(3, 9, 15).flatMap { x -> listOf(3, 9, 15).map { y -> Point(x, y) } }
    13 -> listOf(3, 9).flatMap { x -> listOf(3, 9).map { y -> Point(x, y) } } + Point(6, 6)
    9 -> listOf(2, 6).flatMap { x -> listOf(2, 6).map { y -> Point(x, y) } } + Point(4, 4)
    else -> emptyList()
}

class SgfParseException(message: String) : Exception(message)
