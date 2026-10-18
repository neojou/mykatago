package com.neojou.mykatago.engine

import com.neojou.mykatago.platform.DetectedEngine
import java.io.File

/**
 * Homebrew layout used by the katago formula.
 *
 * Apple Silicon prefix `/opt/homebrew` and Intel prefix `/usr/local` are the usual
 * defaults. This machine was not probed with `brew --prefix`, so both are 【未驗證】
 * until 引擎設定 → 自動偵測 runs.
 */
object KataGoPaths {
    private val prefixes = listOf("/opt/homebrew", "/usr/local")
    private const val preferredModelPrefix = "kata1-b18c384nbt"

    fun detect(): DetectedEngine? {
        for (prefix in prefixes) {
            val binary = File(prefix, "bin/katago")
            if (!binary.canExecute()) continue
            return found(binary, File(prefix, "share/katago"))
        }
        val which = whichKatago() ?: return null
        val binary = File(which)
        if (!binary.isFile) return null
        val share = File(binary.parentFile?.parentFile, "share/katago")
        return found(binary, share)
    }

    fun supportDir(): File {
        val dir = File(System.getProperty("user.home"), "Library/Application Support/mykatago")
        dir.mkdirs()
        return dir
    }

    /**
     * Copies [sourcePath] into the app-support analysis.cfg when it is not already that file,
     * then rewrites logDir and homeDataDir. Thread keys are written only when non-null,
     * so a detected Homebrew config keeps its own thread counts until the user edits them.
     */
    fun installConfig(sourcePath: String, analysisThreads: Int?, searchThreads: Int?): String {
        val root = supportDir()
        val logs = File(root, "katago_logs").apply { mkdirs() }
        val home = File(root, "katago_home").apply { mkdirs() }
        val target = File(root, "analysis.cfg")
        val source = File(sourcePath)
        if (source.isFile && source.canonicalPath != target.canonicalPath) {
            source.copyTo(target, overwrite = true)
        }
        if (!target.isFile) target.writeText("# mykatago analysis.cfg\n")
        var text = target.readText()
        text = upsert(text, "logDir", logs.absolutePath)
        text = upsert(text, "homeDataDir", home.absolutePath)
        if (analysisThreads != null) text = upsert(text, "numAnalysisThreads", analysisThreads.toString())
        if (searchThreads != null) {
            text = upsert(text, "numSearchThreadsPerAnalysisThread", searchThreads.toString())
        }
        target.writeText(text)
        return target.absolutePath
    }

    private fun found(binary: File, share: File): DetectedEngine {
        val model = share.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".bin.gz") }
            .orEmpty()
            .sortedWith(compareBy<File>({ if (it.name.startsWith(preferredModelPrefix)) 0 else 1 }, { it.name }))
            .firstOrNull()
        val config = File(share, "configs/analysis_example.cfg")
        return DetectedEngine(
            katagoPath = binary.absolutePath,
            modelPath = model?.absolutePath.orEmpty(),
            configPath = if (config.isFile) config.absolutePath else "",
        )
    }

    private fun whichKatago(): String? {
        val path = prefixes.joinToString(":") { "$it/bin" } + ":" + System.getenv("PATH").orEmpty()
        val process = ProcessBuilder("/usr/bin/which", "katago")
            .apply { environment()["PATH"] = path }
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return text.lineSequence().firstOrNull { it.startsWith("/") }
    }

    private fun upsert(text: String, key: String, value: String): String {
        val rendered = if (value.any { it.isWhitespace() }) "$key = \"$value\"" else "$key = $value"
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=.*$")
        if (pattern.containsMatchIn(text)) return pattern.replace(text, rendered)
        return text.trimEnd() + "\n" + rendered + "\n"
    }
}
