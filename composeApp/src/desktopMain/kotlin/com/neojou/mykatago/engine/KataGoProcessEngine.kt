package com.neojou.mykatago.engine

import com.neojou.mykatago.analysis.AnalysisEngine
import com.neojou.mykatago.analysis.AnalysisResponse
import com.neojou.mykatago.analysis.AppJson
import com.neojou.mykatago.analysis.EngineCommand
import com.neojou.mykatago.analysis.EngineStatus
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * KataGo analysis engine over stdin/stdout JSON lines.
 *
 * Three coroutines share one process: a writer, a stdout reader, and a stderr reader.
 * The writer never takes the same lock the reader needs. Each [start] owns a new write
 * channel so a restart cannot deliver lines to the previous process.
 *
 * [Process.destroy] sends SIGTERM on macOS. Whether KataGo exits cleanly on that signal
 * is 【未驗證】. [stopBlocking] waits about two seconds, then calls [Process.destroyForcibly].
 */
class KataGoProcessEngine : AnalysisEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statusFlow = MutableStateFlow<EngineStatus>(EngineStatus.Stopped)
    override val status: StateFlow<EngineStatus> = statusFlow.asStateFlow()

    private val generation = AtomicLong(0)
    private val pending = ConcurrentHashMap<String, Slot>()
    private val gate = Any()

    private var running: Running? = null
    private var versionId: String? = null
    private var versionDeferred: CompletableDeferred<AnalysisResponse?>? = null
    private var versionSeq = 0

    init {
        live += this
    }

    override fun start(command: EngineCommand) {
        scope.launch { startProcess(command) }
    }

    override fun analyze(
        id: String,
        json: String,
        generation: Long,
        expectedTurns: Set<Int>,
    ): Flow<AnalysisResponse> = channelFlow {
        if (generation != this@KataGoProcessEngine.generation.get()) return@channelFlow
        val slot = Slot(generation, expectedTurns.toMutableSet(), Channel(Channel.UNLIMITED))
        pending[id] = slot
        val writer = synchronized(gate) { running?.write }
        if (writer == null) {
            pending.remove(id, slot)
            return@channelFlow
        }
        writer.send(Outgoing.Query(generation, json))
        try {
            for (response in slot.channel) send(response)
        } finally {
            if (pending.remove(id, slot)) {
                val current = synchronized(gate) { running?.write }
                current?.trySend(Outgoing.Bypass("""{"id":${jsonString(id)},"action":"terminate"}"""))
            }
        }
    }

    override fun terminateAll() {
        synchronized(gate) { running?.write }?.trySend(Outgoing.Bypass("""{"action":"terminate_all"}"""))
    }

    override fun invalidate(): Long {
        val next = generation.incrementAndGet()
        val slots = pending.values.toList()
        pending.clear()
        slots.forEach { it.channel.close() }
        terminateAll()
        return next
    }

    override suspend fun queryVersion(): AnalysisResponse? {
        val id = "V${++versionSeq}"
        val deferred = CompletableDeferred<AnalysisResponse?>()
        synchronized(gate) {
            versionId = id
            versionDeferred = deferred
        }
        val deadline = System.currentTimeMillis() + 30_000
        while (synchronized(gate) { running?.write } == null && System.currentTimeMillis() < deadline) {
            if (statusFlow.value is EngineStatus.Failed) return null
            delay(50)
        }
        val writer = synchronized(gate) { running?.write } ?: return null
        writer.trySend(Outgoing.Bypass("""{"id":"$id","action":"query_version"}"""))
        // First OpenCL launch can spend minutes autotuning before it answers.
        return withTimeoutOrNull(360_000) { deferred.await() }
    }

    override fun stop() {
        stopBlocking()
    }

    fun stopBlocking() {
        val process = detach() ?: return
        // Process.destroy sends SIGTERM. KataGo 1.15.3 on this Mac exited within a second.
        process.destroy()
        val started = System.currentTimeMillis()
        while (process.isAlive && System.currentTimeMillis() - started < 2_000) {
            Thread.sleep(50)
        }
        if (process.isAlive) process.destroyForcibly()
        statusFlow.value = EngineStatus.Stopped
        live.remove(this)
    }

    private suspend fun startProcess(command: EngineCommand) {
        statusFlow.value = EngineStatus.Starting
        val previous = synchronized(gate) {
            val old = running
            running = null
            old
        }
        previous?.write?.close()
        previous?.job?.cancel()
        previous?.process?.destroy()
        pending.values.toList().forEach { it.channel.close() }
        pending.clear()

        val process = try {
            ProcessBuilder(
                command.katagoPath,
                "analysis",
                "-model",
                command.modelPath,
                "-config",
                command.configPath,
            ).redirectErrorStream(false).start()
        } catch (error: Exception) {
            statusFlow.value = EngineStatus.Failed(error.message ?: "無法啟動 KataGo")
            MyLog.add(TAG, "start failed: ${error.message}", LogLevel.ERROR)
            return
        }

        val write = Channel<Outgoing>(Channel.UNLIMITED)
        val job = SupervisorJob()
        val current = Running(process, write, job)
        synchronized(gate) { running = current }
        scope.launch(job) { writeLoop(current) }
        scope.launch(job) { readStdout(current) }
        scope.launch(job) { readStderr(current) }
        scope.launch(job) {
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            val stillCurrent = synchronized(gate) { running == current }
            if (stillCurrent) {
                synchronized(gate) { if (running == current) running = null }
                statusFlow.value = EngineStatus.Failed("KataGo 已結束（$code）")
            }
        }

        val response = queryVersion()
        if (statusFlow.value is EngineStatus.Failed) return
        when {
            response?.version != null -> {
                statusFlow.value = EngineStatus.Ready(response.version)
                MyLog.add(TAG, "ready ${response.version}", LogLevel.INFO)
            }
            response?.error != null -> {
                statusFlow.value = EngineStatus.Failed(response.error)
                MyLog.add(TAG, "version error ${response.error}", LogLevel.ERROR)
            }
            response != null -> {
                val label = response.git_hash ?: "KataGo"
                statusFlow.value = EngineStatus.Ready(label)
                MyLog.add(TAG, "ready $label", LogLevel.INFO)
            }
            else -> {
                statusFlow.value = EngineStatus.Failed("KataGo 沒有回應版本查詢")
                MyLog.add(TAG, "version query timed out", LogLevel.ERROR)
            }
        }
    }

    private suspend fun writeLoop(current: Running) {
        val writer = current.process.outputStream.bufferedWriter()
        try {
            for (message in current.write) {
                if (message is Outgoing.Query && message.generation != generation.get()) continue
                writer.write(message.json)
                writer.write("\n")
                writer.flush()
            }
        } catch (error: Exception) {
            MyLog.add(TAG, "writer: ${error.message}", LogLevel.WARN)
        }
    }

    private fun readStdout(current: Running) {
        val reader = current.process.inputStream.bufferedReader()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (synchronized(gate) { running } != current) break
                handleLine(line)
            }
        } catch (error: Exception) {
            MyLog.add(TAG, "stdout: ${error.message}", LogLevel.WARN)
        }
    }

    private fun readStderr(current: Running) {
        val reader = current.process.errorStream.bufferedReader()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                MyLog.add(TAG, line, LogLevel.INFO)
                if (line.contains("Uncaught exception") || line.contains("what():")) {
                    statusFlow.value = EngineStatus.Failed(line.take(180))
                }
            }
        } catch (error: Exception) {
            MyLog.add(TAG, "stderr: ${error.message}", LogLevel.WARN)
        }
    }

    private fun handleLine(line: String) {
        val response = try {
            AppJson.format.decodeFromString<AnalysisResponse>(line)
        } catch (error: Exception) {
            MyLog.add(TAG, "json: ${line.take(160)}", LogLevel.WARN)
            return
        }
        val id = response.id
        if (id != null && id == versionId) {
            versionDeferred?.complete(response)
            return
        }
        if (id == null) {
            if (response.error != null) statusFlow.value = EngineStatus.Failed(response.error)
            return
        }
        val slot = pending[id] ?: return
        if (slot.generation != generation.get()) {
            pending.remove(id, slot)
            slot.channel.close()
            return
        }
        slot.channel.trySend(response)
        if (response.error != null || response.isDuringSearch != true) {
            val turn = response.turnNumber
            if (turn != null) slot.expected.remove(turn) else slot.expected.clear()
            if (slot.expected.isEmpty() || response.error != null) {
                pending.remove(id, slot)
                slot.channel.close()
            }
        }
    }

    private fun detach(): Process? {
        val current = synchronized(gate) {
            val old = running
            running = null
            old
        } ?: return null
        current.write.close()
        current.job.cancel()
        pending.values.forEach { it.channel.close() }
        pending.clear()
        return current.process
    }

    private class Slot(
        val generation: Long,
        val expected: MutableSet<Int>,
        val channel: Channel<AnalysisResponse>,
    )

    private class Running(
        val process: Process,
        val write: Channel<Outgoing>,
        val job: Job,
    )

    private sealed interface Outgoing {
        val json: String

        data class Query(val generation: Long, override val json: String) : Outgoing
        data class Bypass(override val json: String) : Outgoing
    }

    companion object {
        private const val TAG = "engine"
        private val live = CopyOnWriteArrayList<KataGoProcessEngine>()

        fun stopAll() {
            live.toList().forEach { it.stopBlocking() }
        }
    }
}

private fun jsonString(value: String): String =
    buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(char)
            }
        }
        append('"')
    }
