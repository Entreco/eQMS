package com.entreco.eqms.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.feature.scaffold.*
import java.awt.Robot
import java.awt.event.WindowEvent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.jewel.window.defaultTitleBarStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.junit.Rule
import org.junit.Test

class DesktopWindowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun productionHostUnifiesHeaderAndKeepsWindowAndWorkspaceAcrossThemes() {
        val projects = object : ProjectCapabilities {
            override suspend fun listDirectory(path: String?) =
                Result.success(DirectoryListing("/synthetic-project", null, emptyList()))
            override suspend fun createFolder(parent: String, name: String): Result<String> = error("Not expected")
            override suspend fun openProject(path: String, create: Boolean) = Result.success(path)
        }
        var saved: Boolean? = null
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(false), { saved = it; Result.success(Unit) },
            { error("No automatic update check") }, { error("No browser expected") },
        )
        var frame: ComposeWindow? = null
        var headerColor = Color.Unspecified
        var density = 1f
        var closeRequested by mutableStateOf(false)
        var closed = false
        compose.setContent {
            DesktopWindow(chrome, onCloseRequest = { closeRequested = true }) { layout ->
                val color = if (isMacOS) JewelTheme.defaultTitleBarStyle.colors.background else Color.Unspecified
                val scale = LocalDensity.current.density
                SideEffect {
                    frame = window
                    headerColor = color
                    density = scale
                }
                DesktopMenus(chrome) { closeRequested = true }
                ProjectScreen(
                    projects, closeRequested = closeRequested,
                    onCloseHandled = { closeRequested = false },
                    onCloseConfirmed = { closed = true },
                    chrome = chrome,
                    headerActionsModifier = workspaceActionsModifier(), layout = layout,
                )
            }
        }
        compose.onAllNodesWithText("DutchDoxx").assertCountEquals(1)
        compose.onAllNodesWithText("Open project").assertCountEquals(1)
        compose.onAllNodesWithText("Create project").assertCountEquals(1)
        compose.onNodeWithText("Dark theme").assertIsDisplayed()
        val original = checkNotNull(frame)
        val lightColor = headerColor
        compose.runOnIdle {
            assertEquals(java.awt.Dimension(960, 720), original.minimumSize)
            assertFalse(original.isUndecorated)
            assertNotNull(original.menuBar)
            assertEquals("DutchDoxx", original.title)
        }
        click("Open project")
        click("Select directory")
        waitForText("Project verified and opened.")
        compose.onNodeWithText("Project verified and opened.").assertExists()
        click("Dark theme")
        waitForText("Light theme")
        compose.onNodeWithText("Light theme").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(original, frame)
            assertTrue(chrome.dark)
            assertEquals(true, saved)
            if (isMacOS) {
                assertNotEquals(lightColor, headerColor)
                assertTrue(headerColor.red < lightColor.red)
            }
        }
        compose.onNodeWithText("Project verified and opened.").assertExists()
        if (isMacOS) {
            compose.runOnIdle { original.setSize(960, 720) }
            compose.waitUntil(5000) {
                compose.onNodeWithText("Light theme").fetchSemanticsNode().boundsInRoot.right / density < 960
            }
            capture(original, "unified-layout.png")
            val label = compose.onNodeWithText("DutchDoxx").fetchSemanticsNode().boundsInRoot
            assertTrue(label.top / density < 30, "App label must be in the native title region")
            assertTrue(label.left / density >= 70, "Native traffic lights must have reserved space: $label, density=$density")
            val button = compose.onNodeWithText("Light theme").fetchSemanticsNode().boundsInRoot
            assertTrue(button.right / density < original.width, "Theme action must fit inside the window")
            click("Light theme")
            compose.waitUntil(5000) { !chrome.dark }
            waitForText("Dark theme")
            compose.onNodeWithText("Dark theme").assertIsDisplayed()
            compose.runOnIdle { assertEquals(lightColor, headerColor); assertSame(original, frame) }
            capture(original, "unified-light.png")
            click("Dark theme")
            waitForText("Light theme")
            compose.onNodeWithText("Light theme").assertIsDisplayed()
            capture(original, "unified-dark.png")
        }
        compose.runOnIdle {
            original.dispatchEvent(WindowEvent(original, WindowEvent.WINDOW_CLOSING))
        }
        compose.waitUntil(3000) { closed }
    }

    private fun click(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 }
    }

    private fun capture(window: ComposeWindow, name: String) {
        val directory = System.getenv("EQMS_CHROME_PREVIEW_DIR") ?: return
        compose.waitForIdle()
        val output = File(directory).apply { mkdirs(); check(isDirectory) }
        val bounds = java.awt.Rectangle(window.locationOnScreen, window.size)
        check(ImageIO.write(Robot().createScreenCapture(bounds), "png", File(output, name)))
    }
}
