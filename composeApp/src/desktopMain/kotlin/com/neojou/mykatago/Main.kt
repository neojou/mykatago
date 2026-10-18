package com.neojou.mykatago

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neojou.mykatago.engine.KataGoProcessEngine
import com.neojou.mykatago.platform.DesktopPlatformServices
import com.neojou.mykatago.ui.Strings
import com.neojou.mykatago.ui.review.ReviewSession
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Frame

/**
 * Desktop entry — opens the review window.
 *
 * On macOS the system application menu uses [AppVersion.APP_NAME], and its About
 * item opens the same dialog as before. File and analysis commands live on the
 * window menu bar. ⌘O, ⌃H, and F2 are menu shortcuts so they are not also handled
 * by the board key listener.
 */
fun main() {
    System.setProperty("apple.awt.application.name", AppVersion.APP_NAME)
    Runtime.getRuntime().addShutdownHook(Thread { KataGoProcessEngine.stopAll() })

    application {
        val about = remember { AboutRequest() }
        val services = remember { DesktopPlatformServices() }
        val scope = rememberCoroutineScope()
        val session = remember { ReviewSession(services, scope) }
        DisposableEffect(about) {
            val remove = installMacAboutHandler(about::show)
            onDispose(remove)
        }

        Window(
            onCloseRequest = {
                session.close()
                exitApplication()
            },
            title = AppVersion.APP_NAME,
            state = rememberWindowState(size = DpSize(1280.dp, 840.dp)),
        ) {
            SideEffect { services.frame = window as? Frame }
            ReviewMenu(session)
            App(about, session)
        }
    }
}

@Composable
private fun FrameWindowScope.ReviewMenu(session: ReviewSession) {
    MenuBar {
        Menu(Strings.fileMenu) {
            Item(
                Strings.openSgf,
                onClick = session::openSgf,
                shortcut = KeyShortcut(Key.O, meta = true),
            )
        }
        Menu(Strings.analysisMenu) {
            Item(Strings.deeper, onClick = session::deeper)
            Item(
                Strings.reset,
                onClick = session::resetAnalysis,
                shortcut = KeyShortcut(Key.H, ctrl = true),
            )
            Item(
                Strings.wholeGameDeep,
                onClick = session::openWholeGameDialog,
                shortcut = KeyShortcut(Key.F2),
            )
            Item(Strings.togglePonder, onClick = session::togglePonder)
            Item(Strings.stopAnalysis, onClick = session::stopAll)
            Item(Strings.analyzeGame, onClick = session::analyzeGame)
            Separator()
            Item("${Strings.equalize} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.sweep} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.alternatives} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.region} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.insert} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.selfplay} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.report} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.tsumego} ${Strings.notYet}", enabled = false, onClick = {})
            Item("${Strings.forceAi} ${Strings.notYet}", enabled = false, onClick = {})
        }
        Menu(Strings.settingsMenu) {
            Item(Strings.engineSettings, onClick = session::openEngineDialog)
            Item(Strings.analysisSettings, onClick = session::openAnalysisDialog)
        }
    }
}

/**
 * Routes the macOS application-menu About item to [onAbout].
 *
 * Returns a remover. On other systems About is not a desktop action, so this does nothing.
 */
private fun installMacAboutHandler(onAbout: () -> Unit): () -> Unit {
    if (!Desktop.isDesktopSupported()) return {}
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.APP_ABOUT)) return {}
    desktop.setAboutHandler { _ ->
        EventQueue.invokeLater { onAbout() }
    }
    return { desktop.setAboutHandler(null) }
}
