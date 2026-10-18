package com.neojou.mykatago.settings

import com.neojou.mykatago.analysis.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
enum class GraphMode {
    WINRATE,
    POINT_LOSS,
}

@Serializable
data class BoardToggles(
    val nextMoves: Boolean = true,
    val showDots: Boolean = true,
    val topMoves: Boolean = true,
    val policy: Boolean = false,
    val territory: Boolean = false,
)

@Serializable
data class AppSettings(
    val katagoPath: String = "",
    val modelPath: String = "",
    val configPath: String = "",
    val maxVisits: Int = 500,
    val fastVisits: Int = 25,
    val maxTimeSeconds: Double = 8.0,
    val wideRootNoise: Double = 0.04,
    val enableOwnership: Boolean = true,
    val numAnalysisThreads: Int? = null,
    val numSearchThreadsPerAnalysisThread: Int? = null,
    val loadFastAnalysis: Boolean = false,
    val loadSgfRewind: Boolean = true,
    val toggles: BoardToggles = BoardToggles(),
    val graphCollapsed: Boolean = false,
    val statsCollapsed: Boolean = false,
    val infoCollapsed: Boolean = false,
    val graphMode: GraphMode = GraphMode.WINRATE,
)

fun encodeSettings(settings: AppSettings): String = AppJson.format.encodeToString(settings)

fun decodeSettings(text: String): AppSettings = AppJson.format.decodeFromString(text)
