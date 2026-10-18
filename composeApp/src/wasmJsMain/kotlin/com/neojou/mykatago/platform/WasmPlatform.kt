package com.neojou.mykatago.platform

import com.neojou.mykatago.analysis.AnalysisEngine
import com.neojou.mykatago.analysis.AnalysisResponse
import com.neojou.mykatago.analysis.EngineCommand
import com.neojou.mykatago.analysis.EngineStatus
import com.neojou.mykatago.settings.MemorySettingsStore
import com.neojou.mykatago.settings.SettingsStore
import com.neojou.mykatago.ui.Strings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class WasmPlatformServices : PlatformServices {
    override val engine: AnalysisEngine = UnsupportedAnalysisEngine()
    override val settings: SettingsStore = MemorySettingsStore()
    override val canOpenFiles: Boolean = false

    override fun pickOpenPath(): String? = null

    override fun readBytes(path: String): ByteArray = ByteArray(0)

    override fun decodeSgf(bytes: ByteArray): String = ""

    override fun detectEngine(): DetectedEngine? = null

    override fun installConfig(sourcePath: String, analysisThreads: Int?, searchThreads: Int?): String = ""

    override fun browseForFile(title: String): String? = null
}

private class UnsupportedAnalysisEngine : AnalysisEngine {
    private var generation = 0L
    override val status: StateFlow<EngineStatus> =
        MutableStateFlow(EngineStatus.Failed(Strings.wasmOnly))

    override fun start(command: EngineCommand) = Unit

    override fun analyze(
        id: String,
        json: String,
        generation: Long,
        expectedTurns: Set<Int>,
    ): Flow<AnalysisResponse> = emptyFlow()

    override fun terminateAll() = Unit

    override fun invalidate(): Long = ++generation

    override suspend fun queryVersion(): AnalysisResponse? = null

    override fun stop() = Unit
}
