package com.entreco.eqms.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.application
import com.entreco.eqms.feature.scaffold.*
import java.awt.Robot
import java.awt.event.InputEvent
import kotlin.test.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopWindowNativeTest {
    @Test
    fun nativeMouseClickReachesTheUnifiedTitleBarAction() {
        assumeTrue("Native unified title bar is macOS-only.", isMacOS)
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
                    try {
                        delay(500)
                        window.toFront()
                        window.requestFocus()
                        delay(300)
                        assertTrue(actions.width > 0)
                        val origin = window.contentPane.locationOnScreen
                        val scale = window.graphicsConfiguration.defaultTransform.scaleX
                        val robot = Robot()
                        robot.mouseMove(origin.x + ((actions.right - 40) / scale).toInt(), origin.y + (actions.center.y / scale).toInt())
                        delay(100)
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                        withTimeout(5000) { while (chrome.dark) delay(50) }
                        assertFalse(chrome.dark)
                        val before = window.locationOnScreen
                        robot.mouseMove(before.x + 550, before.y + 20)
                        delay(100)
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                        delay(100)
                        repeat(8) { step ->
                            robot.mouseMove(before.x + 550 + (step + 1) * 5, before.y + 20 + (step + 1) * 3)
                            delay(50)
                        }
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                        withTimeout(5000) { while (window.locationOnScreen == before) delay(50) }
                        window.location = before
                        window.isMinimized = true
                        withTimeout(5000) { while (!window.isMinimized) delay(50) }
                        window.isMinimized = false
                        withTimeout(5000) { while (window.isMinimized) delay(50) }
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        exitApplication()
                    }
                }
            }
        }
        failure?.let { throw it }
    }
}
