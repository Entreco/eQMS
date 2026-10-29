package com.entreco.eqms.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.feature.scaffold.ProjectScreen
import com.entreco.eqms.feature.scaffold.DesktopChrome
import java.awt.Robot
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class DesktopJewelPreviewTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.activate() =
        performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test
    fun realNativeWorkspaceRendersInBothThemes() {
        val destination = System.getenv("EQMS_JEWEL_PREVIEW_DIR")
        assumeTrue("Set EQMS_JEWEL_PREVIEW_DIR to capture an isolated real-native preview.", !destination.isNullOrBlank())
        val executable = System.getenv("EQMS_QMS_PATH")
        require(!executable.isNullOrBlank()) { "Set EQMS_QMS_PATH to the release qms executable." }
        val output = File(destination!!).apply { mkdirs(); check(isDirectory) }
        val project = Files.createDirectories(Files.createTempDirectory(output.toPath(), "preview-").resolve("jewel-preview"))
        fun git(vararg arguments: String) {
            val result = executeProcess("git", listOf("-C", project.toString()) + arguments)
            assertEquals(0, result.exitCode, result.output)
        }
        git("init", "--quiet")
        git("config", "user.name", "Jewel Preview")
        git("config", "user.email", "preview@example.invalid")
        val projects = DesktopProjectCapabilities(WorkspaceCli(executable))
        val documents = DesktopDocumentCapabilities(executable)
        runBlocking {
            projects.openProject(project.toString(), create = true).getOrThrow()
            val policy = documents.createDocument(
                project.toString(), "Quality policy (sample)",
                "# Quality policy\n\nSynthetic preview content, not an approved quality record.\n",
            ).getOrThrow()
            documents.saveDocument(
                project.toString(), policy.id, policy.revision, policy.title,
                "# Quality policy\n\nSynthetic preview content, not an approved quality record.\n\n" +
                    "## Purpose\n\nKeep quality work clear, traceable, and easy to find.\n\n" +
                    "## Working practices\n\n- Write concise Markdown drafts.\n- Save changes explicitly.\n" +
                    "- Inspect previous revisions in History.\n- Search saved drafts from the Project panel.\n\n" +
                    "Lifecycle approval is a local action, not a signature. Effective is internal publication only.\n",
            ).getOrThrow()
            documents.createDocument(
                project.toString(), "Risk management plan (sample)",
                "# Risk management plan\n\nSynthetic sample draft. Record hazards, controls, and verification evidence.\n",
            ).getOrThrow()
            documents.createDocument(
                project.toString(), "Verification strategy (sample)",
                "# Verification strategy\n\nSynthetic sample draft. Link requirements to observed test results.\n",
            ).getOrThrow()
        }
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(true), { Result.success(Unit) },
            { error("No update check") }, { error("No browser") },
        )
        var frame: ComposeWindow? = null
        var density = 1f
        compose.setContent {
            DesktopWindow(chrome, onCloseRequest = {}) { layout ->
                val scale = LocalDensity.current.density
                SideEffect { frame = window; density = scale }
                DesktopMenus(chrome) {}
                ProjectScreen(projects, documents, chrome = chrome, layout = layout,
                    headerActionsModifier = workspaceActionsModifier())
            }
        }
        compose.onNodeWithText("Open project").activate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Directory path").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Directory path").performTextReplacement(project.toString())
        compose.onNodeWithText("Go").activate()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Select directory") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Select directory").activate()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Quality policy (sample) (Draft)").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Quality policy (sample) (Draft)").activate()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Markdown body").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("## Working practices", substring = true)
        compose.onNodeWithText("Risk management plan (sample) (Draft)").activate()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Document tab Risk management plan (sample)").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Markdown body").performTextReplacement("Unsaved sample risk notes - retained in a separate tab.")
        compose.onNodeWithContentDescription("Document tab Quality policy (sample)").activate()
        val host = checkNotNull(frame)
        val robot = Robot().apply { autoDelay = 50 }
        fun findCanvas(component: java.awt.Component): java.awt.Canvas? =
            if (component is java.awt.Canvas) component
            else (component as? java.awt.Container)?.components?.firstNotNullOfOrNull(::findCanvas)
        val canvas = checkNotNull(findCanvas(host.contentPane))
        javax.swing.SwingUtilities.invokeAndWait { host.toFront() }
        fun nativeShortcut(digit: Int) {
            val requestFocus = compose.onNodeWithContentDescription("Markdown body")
                .fetchSemanticsNode().config[SemanticsActions.RequestFocus].action
            javax.swing.SwingUtilities.invokeAndWait {
                check(checkNotNull(requestFocus).invoke())
                val modifier = if (isMacOS) KeyEvent.META_DOWN_MASK else KeyEvent.ALT_DOWN_MASK
                val time = System.currentTimeMillis()
                val focus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                focus.redispatchEvent(canvas, KeyEvent(canvas, KeyEvent.KEY_PRESSED, time, modifier, digit, KeyEvent.CHAR_UNDEFINED))
                focus.redispatchEvent(canvas, KeyEvent(canvas, KeyEvent.KEY_RELEASED, time + 1, modifier, digit, KeyEvent.CHAR_UNDEFINED))
            }
        }
        nativeShortcut(KeyEvent.VK_2)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Search saved documents").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("## Working practices", substring = true)
        nativeShortcut(KeyEvent.VK_1)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Quality policy (sample) (Draft)").fetchSemanticsNodes().size == 1
        }
        fun capture(name: String, awaitIdle: Boolean = true) {
            if (awaitIdle) compose.waitForIdle()
            check(ImageIO.write(robot.createScreenCapture(java.awt.Rectangle(host.locationOnScreen, host.size)),
                "png", File(output, name)))
        }
        fun captureTooltip(name: String) {
            compose.mainClock.autoAdvance = false
            val bounds = compose.onNodeWithContentDescription("Toggle Project panel").fetchSemanticsNode().boundsInRoot
            fun move(type: Int, x: Int, y: Int) {
                javax.swing.SwingUtilities.invokeAndWait {
                    val point = javax.swing.SwingUtilities.convertPoint(host.contentPane, x, y, canvas)
                    canvas.dispatchEvent(MouseEvent(canvas, type, System.currentTimeMillis(), 0, point.x, point.y, 0, false))
                }
            }
            move(MouseEvent.MOUSE_EXITED, -10, -10)
            move(MouseEvent.MOUSE_ENTERED, (bounds.center.x / density).toInt(), (bounds.center.y / density).toInt())
            move(MouseEvent.MOUSE_MOVED, (bounds.center.x / density).toInt(), (bounds.center.y / density).toInt())
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(if (isMacOS) "\u23181" else "Alt+1").fetchSemanticsNodes().isNotEmpty()
            }
            robot.delay(150)
            capture(name, awaitIdle = false)
            move(MouseEvent.MOUSE_EXITED, -10, -10)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(if (isMacOS) "\u23181" else "Alt+1").fetchSemanticsNodes().isEmpty()
            }
            compose.mainClock.autoAdvance = true
        }
        capture("jewel-dark.png")
        captureTooltip("jewel-tooltip-dark.png")
        compose.onNodeWithText("Light theme").activate()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("## Working practices", substring = true)
        capture("jewel-light.png")
        captureTooltip("jewel-tooltip-light.png")
        compose.onNodeWithContentDescription("Toggle History panel").activate()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Compare") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        capture("jewel-history.png")
        compose.onNodeWithText("Compare").activate()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("+## Purpose", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("+## Purpose", substring = true).assertExists()
        capture("jewel-diff.png")
        compose.onNodeWithContentDescription("Toggle Review panel").activate()
        for ((action, captureName) in listOf(
            "Submit for review" to "jewel-in-review.png",
            "Approve" to "jewel-approved.png",
            "Publish" to "jewel-effective.png",
        )) {
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText(action) and isEnabled()).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithText(action).activate()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Confirm").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithText("Confirm").activate()
            val status = when (action) {
                "Submit for review" -> "In Review"
                "Approve" -> "Approved"
                else -> "Effective"
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Document is now $status.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Save").assertIsNotEnabled()
            capture(captureName)
        }
        compose.onNodeWithContentDescription("Hide Project panel").activate()
        compose.onNodeWithContentDescription("Hide Review panel").activate()
        capture("jewel-collapsed.png")
        compose.onNodeWithContentDescription("Document tab Risk management plan (sample)").activate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Unsaved sample risk notes", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("Unsaved sample risk notes", substring = true)
    }
}
