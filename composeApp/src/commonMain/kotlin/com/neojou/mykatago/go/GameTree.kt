package com.neojou.mykatago.go

class GameNode(
    val id: NodeId,
    val parent: GameNode?,
    val props: MutableMap<String, MutableList<String>>,
    val setup: Setup,
    val move: Move?,
    val depth: Int,
) {
    val children: MutableList<GameNode> = mutableListOf()

    /** Written only on the UI coroutine. */
    var analysis: com.neojou.mykatago.analysis.NodeAnalysis? = null

    fun mainChild(): GameNode? = children.firstOrNull()

    fun comment(): String = props["C"].orEmpty().joinToString("\n")
}

class GameTree(
    val root: GameNode,
    val boardSize: Int,
    val komi: Double,
    val komiDefaulted: Boolean,
    val rules: Ruleset,
    val rulesRaw: String,
    val handicap: Int,
    val playerBlack: String,
    val playerWhite: String,
    val rankBlack: String,
    val rankWhite: String,
    val hasMidGameSetup: Boolean,
    nextId: Int,
) {
    private var nextId: Int = nextId

    fun allocId(): NodeId = NodeId(nextId++)

    fun addMove(parent: GameNode, move: Move): GameNode {
        val key = if (move.player == Stone.BLACK) "B" else "W"
        val value = when (move) {
            is Move.Pass -> ""
            is Move.Play -> pointToSgf(move.point)
        }
        val props = linkedMapOf(key to mutableListOf(value))
        val child = GameNode(
            id = allocId(),
            parent = parent,
            props = props,
            setup = Setup(),
            move = move,
            depth = parent.depth + 1,
        )
        parent.children += child
        return child
    }

    companion object {
        fun empty(size: Int = 19): GameTree {
            val props = linkedMapOf(
                "GM" to mutableListOf("1"),
                "FF" to mutableListOf("4"),
                "CA" to mutableListOf("UTF-8"),
                "SZ" to mutableListOf(size.toString()),
                "KM" to mutableListOf("6.5"),
                "RU" to mutableListOf("Japanese"),
            )
            val root = GameNode(NodeId(1), null, props, Setup(), null, 0)
            return GameTree(
                root = root,
                boardSize = size,
                komi = 6.5,
                komiDefaulted = false,
                rules = Ruleset.JAPANESE,
                rulesRaw = "Japanese",
                handicap = 0,
                playerBlack = "",
                playerWhite = "",
                rankBlack = "",
                rankWhite = "",
                hasMidGameSetup = false,
                nextId = 2,
            )
        }
    }
}

fun redo(node: GameNode, steps: Int): GameNode {
    var current = node
    repeat(steps) { current = current.mainChild() ?: return current }
    return current
}

fun undo(node: GameNode, steps: Int): GameNode {
    var current = node
    repeat(steps) { current = current.parent ?: return current }
    return current
}

fun mainlineAt(root: GameNode, depth: Int): GameNode {
    var current = root
    var left = depth
    while (left > 0) {
        current = current.mainChild() ?: return current
        left--
    }
    return current
}

fun mainlineLength(root: GameNode): Int {
    var current = root
    var length = 0
    while (true) {
        current = current.mainChild() ?: return length
        length++
    }
}

fun onMainline(node: GameNode): Boolean {
    var current = node
    while (true) {
        val parent = current.parent ?: return true
        if (parent.children.firstOrNull() != current) return false
        current = parent
    }
}

/** Switch to the next sibling variation, walking up to the nearest fork. */
fun branchDown(node: GameNode): GameNode {
    var current = node
    while (true) {
        val parent = current.parent ?: return node
        val index = parent.children.indexOf(current)
        if (index >= 0 && index + 1 < parent.children.size) return parent.children[index + 1]
        current = parent
    }
}

fun branchUp(node: GameNode): GameNode {
    var current = node
    while (true) {
        val parent = current.parent ?: return node
        val index = parent.children.indexOf(current)
        if (index > 0) return parent.children[index - 1]
        current = parent
    }
}

/** Move up to the parent of the nearest fork. */
fun undoToBranch(node: GameNode): GameNode {
    var current = node
    while (true) {
        val parent = current.parent ?: return current
        if (parent.children.size > 1) return parent
        current = parent
    }
}

fun toMainline(node: GameNode): GameNode {
    val root = generateSequence(node) { it.parent }.last()
    return mainlineAt(root, node.depth)
}

fun playerToMove(node: GameNode, tree: GameTree): Stone {
    node.move?.let { return it.player.opposite() }
    node.props["PL"]?.firstOrNull()?.let { pl ->
        return if (pl.startsWith("W", ignoreCase = true)) Stone.WHITE else Stone.BLACK
    }
    val parent = node.parent
    if (parent != null) return playerToMove(parent, tree)
    return if (tree.handicap >= 2) Stone.WHITE else Stone.BLACK
}

fun replay(tree: GameTree, target: GameNode): BoardState {
    val path = ArrayList<GameNode>()
    var current: GameNode? = target
    while (current != null) {
        path += current
        current = current.parent
    }
    path.reverse()
    var board = BoardState.empty(tree.boardSize)
    for (node in path) {
        if (!node.setup.isEmpty()) board = board.withSetup(node.setup)
        val move = node.move ?: continue
        board = board.play(move, tree.rules, ignoreKo = true, ignoreSuicide = true).getOrElse { board }
    }
    return board
}

fun pathFromRoot(node: GameNode): List<GameNode> {
    val path = ArrayList<GameNode>()
    var current: GameNode? = node
    while (current != null) {
        path += current
        current = current.parent
    }
    path.reverse()
    return path
}
