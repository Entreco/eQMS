package com.entreco.eqms.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.entreco.eqms.feature.scaffold.DesktopChrome
import com.entreco.eqms.feature.scaffold.DesktopCommand
import java.awt.Desktop
import java.util.Properties

internal val isMacOS: Boolean get() = System.getProperty("os.name").startsWith("Mac")

internal fun desktopVersion(): String =
    Properties().apply {
        checkNotNull(DesktopChrome::class.java.getResourceAsStream("/dutchdox-build.properties")) {
            "DutchDox build metadata is missing."
        }.use { load(it) }
    }.getProperty("version").also { check(!it.isNullOrBlank()) { "DutchDox build version is missing." } }

@Composable
internal fun FrameWindowScope.DesktopMenus(chrome: DesktopChrome, onQuit: () -> Unit) {
    MenuBar {
        if (!isMacOS) {
            Menu("Application") {
                Item("About", enabled = chrome.enabled, onClick = { chrome.request(DesktopCommand.About) })
                Item("Settings", enabled = chrome.enabled, onClick = { chrome.request(DesktopCommand.Settings) })
            }
        }
        Menu("File") {
            Item("New project", enabled = chrome.enabled, onClick = { chrome.request(DesktopCommand.NewProject) })
            Item("Close project", enabled = chrome.enabled && chrome.hasProject, onClick = { chrome.request(DesktopCommand.CloseProject) })
        }
        Menu("Edit") {}
        Menu("View") {}
        Menu("Help") {
            Item("Check for updates", enabled = chrome.enabled, onClick = { chrome.request(DesktopCommand.CheckUpdates) })
        }
    }
    DisposableEffect(chrome, onQuit) {
        if (isMacOS) {
            val desktop = Desktop.getDesktop()
            check(desktop.isSupported(Desktop.Action.APP_ABOUT) &&
                desktop.isSupported(Desktop.Action.APP_PREFERENCES) &&
                desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) { "JBR native application menus are unavailable." }
            desktop.setAboutHandler { chrome.request(DesktopCommand.About) }
            desktop.setPreferencesHandler { chrome.request(DesktopCommand.Settings) }
            desktop.setQuitHandler { _, response ->
                response.cancelQuit()
                onQuit()
            }
            onDispose {
                desktop.setAboutHandler(null)
                desktop.setPreferencesHandler(null)
                desktop.setQuitHandler(null)
            }
        } else {
            onDispose {}
        }
    }
}
