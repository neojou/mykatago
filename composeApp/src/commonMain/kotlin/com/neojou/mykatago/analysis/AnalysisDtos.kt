package com.neojou.mykatago.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Query JSON writes default values so KataGo sees [OverrideSettings.reportAnalysisWinratesAs]
 * and `includeOwnership`. Null fields are omitted.
 */
object AppJson {
    val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

@Serializable
data class OverrideSettings(
    val reportAnalysisWinratesAs: String = "BLACK",
    val wideRootNoise: Double? = null,
    val maxTime: Double? = null,
)

@Serializable
data class AnalysisQuery(
    val id: String,
    val moves: List<List<String>>,
    val rules: String,
    val komi: Double,
    val boardXSize: Int,
    val boardYSize: Int,
    val analyzeTurns: List<Int>,
    val overrideSettings: OverrideSettings,
    val initialStones: List<List<String>> = emptyList(),
    val initialPlayer: String? = null,
    val maxVisits: Int? = null,
    val includeOwnership: Boolean = true,
    val includePolicy: Boolean = true,
    val reportDuringSearchEvery: Double? = null,
    val priority: Int? = null,
)

@Serializable
data class MoveInfoDto(
    val move: String,
    val order: Int = 999,
    val visits: Int = 0,
    val winrate: Double = 0.0,
    val scoreLead: Double = 0.0,
    val prior: Double = 0.0,
    val pv: List<String> = emptyList(),
)

@Serializable
data class RootInfoDto(
    val currentPlayer: String? = null,
    val visits: Int = 0,
    val winrate: Double = 0.5,
    val scoreLead: Double = 0.0,
)

@Serializable
data class AnalysisResponse(
    val id: String? = null,
    val action: String? = null,
    val error: String? = null,
    val warning: String? = null,
    val field: String? = null,
    val isDuringSearch: Boolean? = null,
    val noResults: Boolean? = null,
    val turnNumber: Int? = null,
    val moveInfos: List<MoveInfoDto> = emptyList(),
    val rootInfo: RootInfoDto? = null,
    val ownership: List<Double>? = null,
    val policy: List<Double>? = null,
    val version: String? = null,
    val git_hash: String? = null,
)
