package com.neojou.mykatago.analysis

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface EngineStatus {
    data object Stopped : EngineStatus
    data object Starting : EngineStatus
    data class Ready(val version: String) : EngineStatus
    data class Failed(val message: String) : EngineStatus
}

data class EngineCommand(
    val katagoPath: String,
    val modelPath: String,
    val configPath: String,
)

/**
 * KataGo analysis process. Implementations live in desktopMain and wasmJsMain.
 * [analyze] completes after every expected turn has one `isDuringSearch == false` response.
 */
interface AnalysisEngine {
    val status: StateFlow<EngineStatus>

    fun start(command: EngineCommand)

    fun analyze(
        id: String,
        json: String,
        generation: Long,
        expectedTurns: Set<Int>,
    ): Flow<AnalysisResponse>

    fun terminateAll()

    /**
     * Drop queued analysis for the previous game, send terminate_all, and bump the generation.
     * Returns the generation that later queries must use.
     */
    fun invalidate(): Long

    suspend fun queryVersion(): AnalysisResponse?

    fun stop()
}
