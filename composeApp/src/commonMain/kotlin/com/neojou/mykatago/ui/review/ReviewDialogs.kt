package com.neojou.mykatago.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neojou.mykatago.analysis.QueryBuilder
import com.neojou.mykatago.settings.AppSettings
import com.neojou.mykatago.ui.Strings

@Composable
fun EngineSettingsDialog(session: ReviewSession, settings: AppSettings) {
    var katago by remember { mutableStateOf(settings.katagoPath) }
    var model by remember { mutableStateOf(settings.modelPath) }
    var config by remember { mutableStateOf(settings.configPath) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(settings.katagoPath, settings.modelPath, settings.configPath) {
        if (katago.isEmpty()) katago = settings.katagoPath
        if (model.isEmpty()) model = settings.modelPath
        if (config.isEmpty()) config = settings.configPath
    }
    SettingsDialog(Strings.engineSettings, onDismiss = session::dismissDialogs) {
        PathField(Strings.katagoPath, katago, { katago = it }, session)
        Row {
            TextButton(onClick = { session.browse(Strings.katagoPath) { katago = it } }) { Text(Strings.browse) }
        }
        PathField(Strings.modelPath, model, { model = it }, session)
        Row {
            TextButton(onClick = { session.browse(Strings.modelPath) { model = it } }) { Text(Strings.browse) }
        }
        PathField(Strings.configPath, config, { config = it }, session)
        Row {
            TextButton(onClick = { session.browse(Strings.configPath) { config = it } }) { Text(Strings.browse) }
        }
        Row {
            TextButton(onClick = {
                session.autodetect { detected ->
                    katago = detected.katagoPath
                    model = detected.modelPath
                    config = detected.configPath
                    message = Strings.found
                }
            }) { Text(Strings.autoDetect) }
            TextButton(onClick = {
                message = Strings.engineStarting
                session.testEngine(settings.copy(katagoPath = katago, modelPath = model, configPath = config)) {
                    message = it
                }
            }) { Text(Strings.testEngine) }
        }
        if (message.isNotBlank()) Text(message)
        Row {
            TextButton(onClick = session::dismissDialogs) { Text(Strings.cancel) }
            TextButton(onClick = {
                session.applyEngine(settings.copy(katagoPath = katago, modelPath = model, configPath = config))
            }) { Text(Strings.save) }
        }
    }
}

@Composable
fun AnalysisSettingsDialog(session: ReviewSession, settings: AppSettings) {
    var maxVisits by remember { mutableStateOf(settings.maxVisits.toString()) }
    var fastVisits by remember { mutableStateOf(settings.fastVisits.toString()) }
    var maxTime by remember { mutableStateOf(settings.maxTimeSeconds.toString()) }
    var noise by remember { mutableStateOf(settings.wideRootNoise.toString()) }
    var analysisThreads by remember { mutableStateOf(settings.numAnalysisThreads?.toString().orEmpty()) }
    var searchThreads by remember { mutableStateOf(settings.numSearchThreadsPerAnalysisThread?.toString().orEmpty()) }
    var ownership by remember { mutableStateOf(settings.enableOwnership) }
    var fastLoad by remember { mutableStateOf(settings.loadFastAnalysis) }
    var rewind by remember { mutableStateOf(settings.loadSgfRewind) }
    SettingsDialog(Strings.analysisSettings, onDismiss = session::dismissDialogs) {
        NumberField(Strings.maxVisits, maxVisits, session) { maxVisits = it }
        NumberField(Strings.fastVisits, fastVisits, session) { fastVisits = it }
        NumberField(Strings.maxTime, maxTime, session) { maxTime = it }
        NumberField(Strings.wideRoot, noise, session) { noise = it }
        CheckRow(Strings.ownershipToggle, ownership) { ownership = it }
        CheckRow(Strings.loadFast, fastLoad) { fastLoad = it }
        CheckRow(Strings.loadRewind, rewind) { rewind = it }
        NumberField(Strings.analysisThreads, analysisThreads, session) { analysisThreads = it }
        NumberField(Strings.searchThreads, searchThreads, session) { searchThreads = it }
        Text("執行緒改動會重寫 analysis.cfg 並重新啟動引擎。", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = session::dismissDialogs) { Text(Strings.cancel) }
            TextButton(onClick = {
                session.applyAnalysis(
                    settings.copy(
                        maxVisits = maxVisits.toIntOrNull()?.coerceAtLeast(1) ?: settings.maxVisits,
                        fastVisits = fastVisits.toIntOrNull()?.coerceAtLeast(1) ?: settings.fastVisits,
                        maxTimeSeconds = maxTime.toDoubleOrNull()?.coerceAtLeast(0.1) ?: settings.maxTimeSeconds,
                        wideRootNoise = noise.toDoubleOrNull() ?: settings.wideRootNoise,
                        enableOwnership = ownership,
                        loadFastAnalysis = fastLoad,
                        loadSgfRewind = rewind,
                        numAnalysisThreads = analysisThreads.toIntOrNull(),
                        numSearchThreadsPerAnalysisThread = searchThreads.toIntOrNull(),
                    ),
                )
            }) { Text(Strings.save) }
        }
    }
}

@Composable
fun WholeGameDialog(session: ReviewSession, length: Int) {
    var visits by remember { mutableStateOf("2500") }
    var limit by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf("0") }
    var to by remember { mutableStateOf(length.toString()) }
    var mistakes by remember { mutableStateOf(false) }
    SettingsDialog(Strings.wholeGameDeep, onDismiss = session::dismissDialogs) {
        NumberField(Strings.visitsPerMove, visits, session) { visits = it }
        CheckRow(Strings.limitRange, limit) { limit = it }
        if (limit) {
            NumberField(Strings.fromMove, from, session) { from = it }
            NumberField(Strings.toMove, to, session) { to = it }
        }
        CheckRow(Strings.mistakesOnly, mistakes) { mistakes = it }
        Row {
            TextButton(onClick = session::dismissDialogs) { Text(Strings.cancel) }
            TextButton(onClick = {
                val start = if (limit) from.toIntOrNull() ?: 0 else 0
                val end = if (limit) to.toIntOrNull() ?: length else length
                session.dismissDialogs()
                session.analyzeWhole(
                    visits = visits.toIntOrNull()?.coerceAtLeast(1) ?: 2500,
                    priority = QueryBuilder.PRIORITY_REANALYZE,
                    from = start,
                    to = end,
                    mistakesOnly = mistakes,
                )
            }) { Text(Strings.analyze) }
        }
    }
}

@Composable
private fun SettingsDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 420.dp, max = 640.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        }
    }
}

@Composable
private fun PathField(label: String, value: String, onValue: (String) -> Unit, session: ReviewSession) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { session.typing = it.isFocused },
    )
}

@Composable
private fun NumberField(label: String, value: String, session: ReviewSession, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { session.typing = it.isFocused },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label)
    }
}
