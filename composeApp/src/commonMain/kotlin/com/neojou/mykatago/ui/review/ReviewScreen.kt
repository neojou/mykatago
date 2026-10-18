package com.neojou.mykatago.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp

@Composable
fun ReviewScreen(session: ReviewSession, onAbout: () -> Unit) {
    val state = session.state
    val node = session.node(state.cursorId)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent(session::onKey),
    ) {
        AnalysisToolbar(state, session, onAbout)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.Center) {
                GoBoard(state, node, session::playAt, Modifier.fillMaxSize())
            }
            RightPanel(state, node, session, Modifier.width(340.dp))
        }
        state.banner?.let { message ->
            Text(
                message,
                modifier = Modifier.fillMaxWidth().background(BoardPalette.panelLight).padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        BottomNavBar(state, session)
    }
    if (state.showEngineDialog) EngineSettingsDialog(session, state.settings)
    if (state.showAnalysisDialog) AnalysisSettingsDialog(session, state.settings)
    if (state.showWholeGameDialog) WholeGameDialog(session, state.mainlineLength)
}
