package com.neojou.mykatago.platform

import com.neojou.mykatago.analysis.AnalysisEngine
import com.neojou.mykatago.settings.SettingsStore

data class DetectedEngine(
    val katagoPath: String,
    val modelPath: String,
    val configPath: String,
)

interface PlatformServices {
    val engine: AnalysisEngine
    val settings: SettingsStore
    val canOpenFiles: Boolean

    fun pickOpenPath(): String?
    fun readBytes(path: String): ByteArray
    fun decodeSgf(bytes: ByteArray): String
    fun detectEngine(): DetectedEngine?
    fun installConfig(sourcePath: String, analysisThreads: Int?, searchThreads: Int?): String
    fun browseForFile(title: String): String?
}
