package com.neojou.mykatago.go

object SgfWriter {
    fun write(tree: GameTree): String {
        val out = StringBuilder()
        writeTree(out, tree.root)
        return out.toString()
    }

    private fun writeTree(out: StringBuilder, start: GameNode) {
        out.append('(')
        var current = start
        while (true) {
            writeNode(out, current)
            val children = current.children
            when (children.size) {
                0 -> break
                1 -> current = children[0]
                else -> {
                    for (child in children) writeTree(out, child)
                    break
                }
            }
        }
        out.append(')')
    }

    private fun writeNode(out: StringBuilder, node: GameNode) {
        out.append(';')
        for ((key, values) in node.props) {
            out.append(key)
            for (value in values) {
                out.append('[')
                out.append(escape(value))
                out.append(']')
            }
        }
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            if (ch == '\\' || ch == ']') out.append('\\')
            out.append(ch)
        }
        return out.toString()
    }
}
