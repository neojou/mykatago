package com.neojou.mykatago.go

enum class Stone {
    BLACK,
    WHITE,
    ;

    fun opposite(): Stone = if (this == BLACK) WHITE else BLACK

    /** KataGo analysis uses "B" and "W". */
    fun katago(): String = if (this == BLACK) "B" else "W"

    val code: Int get() = if (this == BLACK) BLACK_CODE else WHITE_CODE

    companion object {
        const val BLACK_CODE = 1
        const val WHITE_CODE = 2

        fun fromCode(code: Int): Stone? = when (code) {
            BLACK_CODE -> BLACK
            WHITE_CODE -> WHITE
            else -> null
        }
    }
}

/** Intersection. [y] is 0 on the top row, matching SGF and KataGo arrays. */
data class Point(val x: Int, val y: Int)

sealed interface Move {
    val player: Stone

    data class Play(override val player: Stone, val point: Point) : Move
    data class Pass(override val player: Stone) : Move
}

data class NodeId(val value: Int)

class IllegalMoveException(message: String) : Exception(message)
