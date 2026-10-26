package com.entreco.eqms.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.entreco.eqms.feature.scaffold.DesktopChrome
import com.entreco.eqms.feature.scaffold.ProjectScreenLayout
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.jetbrains.jewel.window.utils.clientRegion

@Composable
internal fun DesktopWindow(
    chrome: DesktopChrome,
    onCloseRequest: () -> Unit,
    content: @Composable FrameWindowScope.(ProjectScreenLayout?) -> Unit,
) {
    val state = rememberWindowState(width = 1100.dp, height = 800.dp)
    val icon = painterResource("branding/icon.png")
    if (isMacOS) {
        IntUiTheme(
            theme = if (chrome.dark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
            styling = ComponentStyling.default().decoratedWindow(
                titleBarStyle = if (chrome.dark) TitleBarStyle.dark() else TitleBarStyle.lightWithLightHeader(),
            ),
        ) {
            DecoratedWindow(onCloseRequest, state = state, title = "DutchDoxx", icon = icon) {
                SideEffect { window.minimumSize = java.awt.Dimension(960, 720) }
                val scope = this
                content { title, actions, body ->
                    scope.TitleBar(Modifier.newFullscreenControls()) {
                        // Jewel measures children before native insets; avoid full-width title-bar children.
                        Box(Modifier.align(Alignment.Start).padding(start = 40.dp, end = 12.dp).width(360.dp)) { title() }
                        Box(Modifier.align(Alignment.End).padding(end = 16.dp)) { actions() }
                    }
                    body()
                }
            }
        }
    } else {
        Window(onCloseRequest, state = state, title = "DutchDoxx", icon = icon) {
            SideEffect { window.minimumSize = java.awt.Dimension(960, 720) }
            content(null)
        }
    }
}

internal fun workspaceActionsModifier(): Modifier =
    if (isMacOS) Modifier.clientRegion("workspace-actions") else Modifier
