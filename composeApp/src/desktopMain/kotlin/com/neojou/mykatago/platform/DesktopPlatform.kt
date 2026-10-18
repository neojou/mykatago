package com.neojou.mykatago.platform

import com.neojou.mykatago.analysis.AnalysisEngine
import com.neojou.mykatago.engine.KataGoPaths
import com.neojou.mykatago.engine.KataGoProcessEngine
import com.neojou.mykatago.settings.JsonFileSettingsStore
import com.neojou.mykatago.settings.SettingsStore
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class DesktopPlatformServices : PlatformServices {
    @Volatile
    var frame: Frame? = null

    override val engine: AnalysisEngine = KataGoProcessEngine()
    override val settings: SettingsStore = JsonFileSettingsStore()
    override val canOpenFiles: Boolean = true

    override fun pickOpenPath(): String? = browseForFile("開啟棋譜")

    override fun readBytes(path: String): ByteArray = File(path).readBytes()

    override fun decodeSgf(bytes: ByteArray): String = decodeSgfBytes(bytes)

    override fun detectEngine(): DetectedEngine? = KataGoPaths.detect()

    override fun installConfig(sourcePath: String, analysisThreads: Int?, searchThreads: Int?): String =
        KataGoPaths.installConfig(sourcePath, analysisThreads, searchThreads)

    override fun browseForFile(title: String): String? {
        // Call on the Swing EDT. FileDialog(null) is parentless; a Frame parent is 【未驗證】 on macOS.
        val dialog = FileDialog(frame, title, FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val directory = dialog.directory ?: return null
        return File(directory, name).absolutePath
    }
}

internal fun decodeSgfBytes(bytes: ByteArray): String {
    val charsetName = charsetFromCa(bytes)
    if (charsetName != null && charsetName.contains("big5", ignoreCase = true)) {
        return String(bytes, Charset.forName("Big5"))
    }
    if (charsetName != null && !charsetName.equals("UTF-8", ignoreCase = true) && !charsetName.equals("UTF8", ignoreCase = true)) {
        val charset = runCatching { Charset.forName(charsetName) }.getOrNull()
        if (charset != null) return String(bytes, charset)
    }
    return try {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("GB18030"))
    }
}

private fun charsetFromCa(bytes: ByteArray): String? {
    val head = String(bytes.copyOfRange(0, minOf(bytes.size, 512)), Charsets.ISO_8859_1)
    return Regex("CA\\[([^\\]]+)]").find(head)?.groupValues?.get(1)?.trim()
}
