package com.entreco.eqms.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.application
import com.entreco.eqms.feature.scaffold.*
import java.awt.Robot
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.InputEvent
import kotlin.test.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class DesktopWindowNativeTest {
    init {
        requireNativeIntegrationEnvironment()
    }

    @Test
    fun nativeMouseClickReachesTheUnifiedTitleBarAction() {
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(true), { Result.success(Unit) },
            { error("No update check") }, { error("No browser") },
        )
        val projects = object : ProjectCapabilities {
            override suspend fun listDirectory(path: String?): Result<DirectoryListing> = error("Not expected")
            override suspend fun createFolder(parent: String, name: String): Result<String> = error("Not expected")
            override suspend fun openProject(path: String, create: Boolean): Result<String> = error("Not expected")
        }
        var actions = Rect.Zero
        var failure: Throwable? = null
        application(exitProcessOnExit = false) {
            DesktopWindow(chrome, ::exitApplication) { layout ->
                ProjectScreen(
                    projects, chrome = chrome, layout = layout,
                    headerActionsModifier = workspaceActionsModifier().onGloballyPositioned {
                        actions = it.boundsInWindow()
                    },
                )
                LaunchedEffect(Unit) {
                    var robot: Robot? = null
                    try {
                        awaitNative("header action layout") { actions.width > 0 && actions.height > 0 }
                        val screen = window.graphicsConfiguration.bounds
                        val insets = Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
                        val available = Rectangle(
                            screen.x + insets.left, screen.y + insets.top,
                            screen.width - insets.left - insets.right,
                            screen.height - insets.top - insets.bottom,
                        )
                        val minimum = window.minimumSize
                        assertTrue(minimum.width > 0 && minimum.height > 0)
                        assertTrue(available.width >= minimum.width && available.height >= minimum.height,
                            "CI display must fit the minimum native window: available=$available minimum=$minimum")
                        window.setSize(minimum)
                        window.setLocation(
                            available.x + (available.width - minimum.width) / 2,
                            available.y + (available.height - minimum.height) / 2,
                        )
                        val scale = window.graphicsConfiguration.defaultTransform.scaleX
                        awaitNative("minimum-size window and action layout") {
                            window.size == minimum && actions.right / scale <= minimum.width
                        }
                        window.toFront()
                        window.requestFocus()
                        awaitNative("native window focus") { window.isFocused }
                        val origin = window.contentPane.locationOnScreen
                        val input = Robot().also { robot = it }
                        val target = Point(
                            origin.x + (actions.center.x / scale).toInt(),
                            origin.y + (actions.center.y / scale).toInt(),
                        )
                        println("Native click: screen=$screen available=$available window=${window.bounds} origin=$origin scale=$scale actions=$actions target=$target focused=${window.isFocused}")
                        assertTrue(available.contains(target), "Native click target must be visible: target=$target available=$available")
                        input.mouseMove(target.x, target.y)
                        awaitNative("OS pointer positioning; check native input permissions",
                            diagnostics = { "target=$target actual=${MouseInfo.getPointerInfo()?.location} screen=$screen" }) {
                            MouseInfo.getPointerInfo()?.location == target
                        }
                        input.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                        input.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                        awaitNative("native title-bar click changes appearance") { !chrome.dark }
                        assertFalse(chrome.dark)
                        val before = window.locationOnScreen
                        input.mouseMove(before.x + 550, before.y + 20)
                        delay(100)
                        input.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                        delay(100)
                        repeat(8) { step ->
                            input.mouseMove(before.x + 550 + (step + 1) * 5, before.y + 20 + (step + 1) * 3)
                            delay(50)
                        }
                        input.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                        awaitNative("native title-bar drag moves window") { window.locationOnScreen != before }
                        window.location = before
                        window.isMinimized = true
                        awaitNative("native minimize") { window.isMinimized }
                        window.isMinimized = false
                        awaitNative("native restore") { !window.isMinimized }
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        try {
                            robot?.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                        } finally {
                            exitApplication()
                        }
                    }
                }
            }
        }
        failure?.let { throw it }
    }

    private suspend fun awaitNative(stage: String, diagnostics: () -> String = { "" }, ready: () -> Boolean) {
        assertNotNull(withTimeoutOrNull(5000) {
            while (!ready()) delay(50)
            true
        }, "Timed out waiting for $stage. ${diagnostics()}")
    }
}
