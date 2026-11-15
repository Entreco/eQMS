package com.entreco.eqms.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import javax.swing.JRootPane
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

private val isMacOS = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

/** Height of the unified title region reserved under the native window buttons. */
private val titleRegionHeight = 28.dp

/**
 * Shared chrome for every secondary window (dialogs, About): the native title bar takes the theme's
 * background, like the main window, instead of the system's fixed light/dark colour. On other platforms
 * the platform title bar is kept.
 */
@Composable
fun WindowScope.ThemedWindowContent(title: String, content: @Composable () -> Unit) {
    val dark = JewelTheme.isDark
    val background = JewelTheme.globalColors.panelBackground
    if (!isMacOS) {
        content()
        return
    }
    SideEffect { (window as? javax.swing.RootPaneContainer)?.rootPane?.applyUnifiedTitleBar(dark) }
    Column(Modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxWidth().height(titleRegionHeight), contentAlignment = Alignment.Center) {
            Text(title, color = JewelTheme.globalColors.text.info, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

private fun JRootPane.applyUnifiedTitleBar(dark: Boolean) {
    putClientProperty("apple.awt.fullWindowContent", true)
    putClientProperty("apple.awt.transparentTitleBar", true)
    putClientProperty("apple.awt.windowTitleVisible", false)
    putClientProperty("apple.awt.windowAppearance", if (dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua")
}
