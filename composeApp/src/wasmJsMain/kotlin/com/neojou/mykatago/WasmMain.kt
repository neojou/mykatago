package com.neojou.mykatago

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.neojou.mykatago.platform.WasmPlatformServices
import com.neojou.mykatago.ui.review.ReviewSession
import kotlinx.browser.document

/**
 * Browser / Wasm entry. Analysis and file dialogs are desktop-only; the shared
 * review screen shows [com.neojou.mykatago.ui.Strings.wasmOnly].
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.title = AppVersion.APP_NAME
    ComposeViewport(document.body!!) {
        val about = remember { AboutRequest() }
        val services = remember { WasmPlatformServices() }
        val scope = rememberCoroutineScope()
        val session = remember { ReviewSession(services, scope) }
        App(about, session)
    }
}
