package com.neojou.mykatago.ui.review

import androidx.compose.ui.graphics.Color
import com.neojou.mykatago.analysis.evaluationClass

object BoardPalette {
    val board = Color(0xFFD9AD66)
    val line = Color(0xFF1A1A1A)
    val blackStone = Color(0xFF0D0D0D)
    val whiteStone = Color(0xFFF2F2F2)
    val topMoveBorder = Color(0xFF0AC8FA)
    val winrate = Color(0xFF1ACC1A)
    val score = Color(0xFF4CB2E6)
    val pointLoss = Color(0xFFCCCC1A)
    val graphDot = Color(0xFFD94D4D)
    val ready = Color(0xFF1E9600)
    val busy = Color(0xFFE6661A)
    val failed = Color(0xFFCC0000)
    val pondering = Color(0xFFCCCC1A)
    val panel = Color(0xFF2E4158)
    val panelLight = Color(0xFF40556E)

    fun evalColor(pointsLost: Double): Color = evalColor(evaluationClass(pointsLost))

    fun evalColor(klass: Int): Color = when (klass) {
        0 -> Color(0xFF72216B)
        1 -> Color(0xFFCC0000)
        2 -> Color(0xFFE6661A)
        3 -> Color(0xFFF2F200)
        4 -> Color(0xFFABE62E)
        else -> Color(0xFF1E9600)
    }
}
