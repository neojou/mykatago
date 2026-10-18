package com.neojou.mykatago.go

data class Setup(
    val black: List<Point> = emptyList(),
    val white: List<Point> = emptyList(),
    val empty: List<Point> = emptyList(),
) {
    fun isEmpty(): Boolean = black.isEmpty() && white.isEmpty() && empty.isEmpty()
}

/**
 * Immutable board. Grid index is `y * size + x`, with y = 0 at the top.
 * [capturesBlack] counts stones Black has taken.
 */
class BoardState(
    val size: Int,
    private val grid: IntArray,
    val capturesBlack: Int,
    val capturesWhite: Int,
    val lastCaptureCount: Int,
    val lastCapturePoint: Point?,
    val lastMove: Move?,
) {
    fun stoneAt(point: Point): Stone? {
        if (point.x !in 0 until size || point.y !in 0 until size) return null
        return Stone.fromCode(grid[index(point)])
    }

    fun withSetup(setup: Setup): BoardState {
        if (setup.isEmpty()) return this
        val next = grid.copyOf()
        for (point in setup.empty) next[index(point)] = 0
        for (point in setup.black) next[index(point)] = Stone.BLACK_CODE
        for (point in setup.white) next[index(point)] = Stone.WHITE_CODE
        return copy(grid = next)
    }

    /**
     * @param ignoreKo keep SGF moves that retake a ko immediately
     * @param ignoreSuicide keep SGF moves that would be suicide under [rules]
     */
    fun play(
        move: Move,
        rules: Ruleset,
        ignoreKo: Boolean = false,
        ignoreSuicide: Boolean = false,
    ): Result<BoardState> {
        val point = when (move) {
            is Move.Pass -> {
                return Result.success(copy(lastCaptureCount = 0, lastCapturePoint = null, lastMove = move))
            }
            is Move.Play -> move.point
        }
        if (point.x !in 0 until size || point.y !in 0 until size) {
            return Result.failure(IllegalMoveException("座標超出棋盤"))
        }
        if (grid[index(point)] != 0) {
            return Result.failure(IllegalMoveException("此處已有棋子"))
        }
        val next = grid.copyOf()
        next[index(point)] = move.player.code
        val captured = ArrayList<Point>()
        for (neighbor in neighbors(point)) {
            if (next[index(neighbor)] == move.player.opposite().code && liberties(next, neighbor) == 0) {
                captured += removeGroup(next, neighbor)
            }
        }
        val simpleKo = lastCaptureCount == 1 && lastCapturePoint == point && captured.size == 1
        if (simpleKo && !ignoreKo) {
            return Result.failure(IllegalMoveException("劫爭，不能立即回提"))
        }
        if (liberties(next, point) == 0) {
            val allowed = ignoreSuicide || (rules.allowsMultiSuicide && groupSize(next, point) > 1)
            if (!allowed) return Result.failure(IllegalMoveException("禁止自殺"))
        }
        val takenByBlack = if (move.player == Stone.BLACK) captured.size else 0
        val takenByWhite = if (move.player == Stone.WHITE) captured.size else 0
        return Result.success(
            BoardState(
                size = size,
                grid = next,
                capturesBlack = capturesBlack + takenByBlack,
                capturesWhite = capturesWhite + takenByWhite,
                lastCaptureCount = captured.size,
                lastCapturePoint = if (captured.size == 1) captured[0] else null,
                lastMove = move,
            ),
        )
    }

    private fun index(point: Point): Int = point.y * size + point.x

    private fun neighbors(point: Point): List<Point> {
        val out = ArrayList<Point>(4)
        if (point.x > 0) out += Point(point.x - 1, point.y)
        if (point.x + 1 < size) out += Point(point.x + 1, point.y)
        if (point.y > 0) out += Point(point.x, point.y - 1)
        if (point.y + 1 < size) out += Point(point.x, point.y + 1)
        return out
    }

    private fun liberties(grid: IntArray, start: Point): Int {
        val color = grid[index(start)]
        if (color == 0) return 0
        val seen = BooleanArray(grid.size)
        val stack = ArrayDeque<Point>()
        val counted = HashSet<Int>()
        stack.add(start)
        seen[index(start)] = true
        var libs = 0
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            for (neighbor in neighbors(current)) {
                val at = index(neighbor)
                when (grid[at]) {
                    0 -> if (counted.add(at)) libs++
                    color -> if (!seen[at]) {
                        seen[at] = true
                        stack.add(neighbor)
                    }
                }
            }
        }
        return libs
    }

    private fun groupSize(grid: IntArray, start: Point): Int {
        val color = grid[index(start)]
        val seen = BooleanArray(grid.size)
        val stack = ArrayDeque<Point>()
        stack.add(start)
        seen[index(start)] = true
        var count = 0
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            count++
            for (neighbor in neighbors(current)) {
                val at = index(neighbor)
                if (grid[at] == color && !seen[at]) {
                    seen[at] = true
                    stack.add(neighbor)
                }
            }
        }
        return count
    }

    private fun removeGroup(grid: IntArray, start: Point): List<Point> {
        val color = grid[index(start)]
        val stack = ArrayDeque<Point>()
        val removed = ArrayList<Point>()
        stack.add(start)
        grid[index(start)] = 0
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            removed += current
            for (neighbor in neighbors(current)) {
                if (grid[index(neighbor)] == color) {
                    grid[index(neighbor)] = 0
                    stack.add(neighbor)
                }
            }
        }
        return removed
    }

    private fun copy(
        grid: IntArray = this.grid.copyOf(),
        capturesBlack: Int = this.capturesBlack,
        capturesWhite: Int = this.capturesWhite,
        lastCaptureCount: Int = this.lastCaptureCount,
        lastCapturePoint: Point? = this.lastCapturePoint,
        lastMove: Move? = this.lastMove,
    ): BoardState = BoardState(
        size, grid, capturesBlack, capturesWhite, lastCaptureCount, lastCapturePoint, lastMove,
    )

    companion object {
        fun empty(size: Int): BoardState = BoardState(
            size = size,
            grid = IntArray(size * size),
            capturesBlack = 0,
            capturesWhite = 0,
            lastCaptureCount = 0,
            lastCapturePoint = null,
            lastMove = null,
        )
    }
}
