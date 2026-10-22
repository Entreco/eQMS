package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun AboutWindow(chrome: DesktopChrome, dark: Boolean, request: Int, onClose: () -> Unit) {
    val state = rememberWindowState(
        width = 620.dp, height = 360.dp,
        position = WindowPosition(Alignment.Center),
    )
    Window(
        onCloseRequest = onClose,
        title = "About DutchDoxx",
        state = state,
        resizable = false,
        undecorated = false,
    ) {
        LaunchedEffect(request) {
            state.isMinimized = false
            window.extendedState = window.extendedState and java.awt.Frame.ICONIFIED.inv()
            window.toFront()
            window.requestFocus()
        }
        IntUiTheme(isDark = dark) {
            Column(
                Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                chrome.aboutLogo()
                Text("DutchDoxx ${chrome.version}")
                Text("An eQMS quality workspace. Drafts are not approvals.")
                Spacer(Modifier.weight(1f))
                DefaultButton(modifier = Modifier.align(Alignment.End), onClick = onClose) { Text("Close") }
            }
        }
    }
}
