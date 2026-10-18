package com.neojou.mykatago.go

object SgfParser {
    fun parse(text: String): Result<GameTree> = runCatching { parseGame(text) }
        .fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    if (error is SgfParseException) error
                    else SgfParseException(error.message ?: "無法解析 SGF"),
                )
            },
        )

    private fun parseGame(text: String): GameTree {
        val cursor = Cursor(text)
        if (cursor.peek() != '(') throw SgfParseException("找不到棋譜")
        val rawRoot = parseGameTree(cursor)
        return convert(rawRoot)
    }

    private fun parseGameTree(cursor: Cursor): RawNode {
        cursor.eat('(')
        val nodes = mutableListOf<RawNode>()
        while (cursor.peek() == ';') nodes += parseNode(cursor)
        if (nodes.isEmpty()) throw SgfParseException("遊戲樹沒有節點")
        for (index in 0 until nodes.lastIndex) {
            nodes[index].children += nodes[index + 1]
        }
        val tail = nodes.last()
        while (cursor.peek() == '(') tail.children += parseGameTree(cursor)
        cursor.eat(')')
        return nodes.first()
    }

    private fun parseNode(cursor: Cursor): RawNode {
        cursor.eat(';')
        val node = RawNode()
        while (true) {
            val next = cursor.peek() ?: break
            if (next !in 'A'..'Z') break
            val ident = readIdent(cursor)
            if (cursor.peek() != '[') throw SgfParseException("屬性 $ident 沒有值")
            val values = mutableListOf<String>()
            while (cursor.peek() == '[') values += readValue(cursor)
            node.props.getOrPut(ident) { mutableListOf() }.addAll(values)
        }
        return node
    }

    private fun readIdent(cursor: Cursor): String {
        cursor.skipWs()
        val start = cursor.index
        while (cursor.index < cursor.text.length && cursor.text[cursor.index] in 'A'..'Z') {
            cursor.index++
        }
        if (cursor.index == start) throw SgfParseException("缺少屬性名稱")
        return cursor.text.substring(start, cursor.index)
    }

    private fun readValue(cursor: Cursor): String {
        cursor.eat('[')
        val out = StringBuilder()
        while (cursor.index < cursor.text.length) {
            val ch = cursor.text[cursor.index]
            if (ch == '\\') {
                cursor.index++
                if (cursor.index >= cursor.text.length) break
                val escaped = cursor.text[cursor.index]
                if (escaped == '\n') {
                    cursor.index++
                    continue
                }
                if (escaped == '\r') {
                    cursor.index++
                    if (cursor.index < cursor.text.length && cursor.text[cursor.index] == '\n') cursor.index++
                    continue
                }
                out.append(escaped)
                cursor.index++
                continue
            }
            if (ch == ']') {
                cursor.index++
                return out.toString()
            }
            out.append(ch)
            cursor.index++
        }
        throw SgfParseException("屬性值沒有結束")
    }

    private fun convert(raw: RawNode): GameTree {
        val sizeText = raw.props["SZ"]?.firstOrNull() ?: "19"
        if (':' in sizeText) throw SgfParseException("不支援長方形棋盤")
        val size = sizeText.toIntOrNull() ?: throw SgfParseException("棋盤尺寸無效")
        if (size !in 2..19) throw SgfParseException("不支援 ${size} 路棋盤")
        val game = raw.props["GM"]?.firstOrNull()
        if (game != null && game != "1") throw SgfParseException("不是圍棋棋譜")
        val rulesRaw = raw.props["RU"]?.firstOrNull().orEmpty()
        val rules = rulesetFromRu(rulesRaw.ifBlank { null })
        val komiText = raw.props["KM"]?.firstOrNull()
        val komi = komiText?.toDoubleOrNull() ?: defaultKomi(rules)
        val handicap = raw.props["HA"]?.firstOrNull()?.toIntOrNull() ?: 0
        var next = 1
        var midGameSetup = false

        fun build(source: RawNode, parent: GameNode?, depth: Int): GameNode {
            var setup = setupOf(source, size)
            if (parent == null && handicap >= 2 && setup.black.isEmpty()) {
                val stones = standardHandicap(size, handicap)
                if (stones.isNotEmpty()) {
                    setup = setup.copy(black = stones)
                    source.props.getOrPut("AB") { mutableListOf() }.addAll(stones.map(::pointToSgf))
                }
            }
            if (depth > 0 && !setup.isEmpty()) midGameSetup = true
            val node = GameNode(
                id = NodeId(next++),
                parent = parent,
                props = copyProps(source),
                setup = setup,
                move = moveOf(source, size),
                depth = depth,
            )
            for (child in source.children) node.children += build(child, node, depth + 1)
            return node
        }

        val root = build(raw, null, 0)
        return GameTree(
            root = root,
            boardSize = size,
            komi = komi,
            komiDefaulted = komiText == null,
            rules = rules,
            rulesRaw = rulesRaw.ifBlank { rules.label },
            handicap = handicap,
            playerBlack = raw.props["PB"]?.firstOrNull().orEmpty(),
            playerWhite = raw.props["PW"]?.firstOrNull().orEmpty(),
            rankBlack = raw.props["BR"]?.firstOrNull().orEmpty(),
            rankWhite = raw.props["WR"]?.firstOrNull().orEmpty(),
            hasMidGameSetup = midGameSetup,
            nextId = next,
        )
    }

    private fun setupOf(raw: RawNode, size: Int): Setup {
        fun points(key: String): List<Point> = raw.props[key].orEmpty().map { value ->
            when (val coord = parseSgfCoord(value, size)) {
                SgfCoord.Pass -> throw SgfParseException("擺子不能是 pass")
                is SgfCoord.At -> coord.point
            }
        }
        return Setup(points("AB"), points("AW"), points("AE"))
    }

    private fun moveOf(raw: RawNode, size: Int): Move? {
        val black = raw.props["B"]?.firstOrNull()
        val white = raw.props["W"]?.firstOrNull()
        if (black != null) return moveFrom(Stone.BLACK, black, size)
        if (white != null) return moveFrom(Stone.WHITE, white, size)
        return null
    }

    private fun moveFrom(player: Stone, value: String, size: Int): Move =
        when (val coord = parseSgfCoord(value, size)) {
            SgfCoord.Pass -> Move.Pass(player)
            is SgfCoord.At -> Move.Play(player, coord.point)
        }

    private fun copyProps(raw: RawNode): MutableMap<String, MutableList<String>> {
        val copy = LinkedHashMap<String, MutableList<String>>()
        for ((key, values) in raw.props) copy[key] = values.toMutableList()
        return copy
    }

    private class RawNode {
        val props = LinkedHashMap<String, MutableList<String>>()
        val children = mutableListOf<RawNode>()
    }

    private class Cursor(val text: String) {
        var index: Int = 0

        fun skipWs() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun peek(): Char? {
            skipWs()
            return text.getOrNull(index)
        }

        fun eat(expected: Char) {
            val found = peek() ?: throw SgfParseException("棋譜在 $expected 之前結束")
            if (found != expected) throw SgfParseException("預期 $expected，卻遇到 $found")
            index++
        }
    }
}
