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

    private fun sourceField(name: String): SemanticsNodeInteraction {
        if (compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithContentDescription("View Source").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("View Source").activate()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isNotEmpty()
            }
        }
        return compose.onNodeWithContentDescription(name)
    }

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
        runBlocking { projects.openProject(project.toString(), create = true).getOrThrow() }
        Files.createDirectories(project.resolve("assets"))
        val illustration = java.awt.image.BufferedImage(320, 120, java.awt.image.BufferedImage.TYPE_INT_RGB)
        illustration.createGraphics().apply {
            color = java.awt.Color(35, 44, 64); fillRect(0, 0, 320, 120)
            color = java.awt.Color(70, 125, 245); fillRoundRect(20, 30, 80, 60, 12, 12)
            color = java.awt.Color.WHITE; drawString("Draft  >  Review  >  Effective", 115, 65)
            dispose()
        }
        check(ImageIO.write(illustration, "png", project.resolve("assets/workflow.png").toFile()))
        runBlocking {
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
                    "## Responsibilities\n\n| Role | Activity |\n| --- | --- |\n| Author | Prepare content |\n| Reviewer | Inspect the saved revision |\n\n" +
                    "![Synthetic workflow](assets/workflow.png)\n\n" +
                    "See the [working practices](#working-practices) and supporting note[^scope].\n\n[^scope]: Synthetic content only, not approved evidence.\n\n" +
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
            compose.onAllNodes(hasContentDescription("Directory path") and isEnabled()).fetchSemanticsNodes().size == 1
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
            compose.onAllNodesWithContentDescription("View Source").fetchSemanticsNodes().size == 1
        }
        sourceField("Markdown body").assertTextContains("## Working practices", substring = true)
        compose.onNodeWithText("Risk management plan (sample) (Draft)").activate()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Document tab Risk management plan (sample)").fetchSemanticsNodes().size == 1
        }
        sourceField("Markdown body").performTextReplacement("Unsaved sample risk notes - retained in a separate tab.")
        compose.onNodeWithContentDescription("Document tab Quality policy (sample)").activate()
        val host = checkNotNull(frame)
        val robot = Robot().apply { autoDelay = 50 }
        fun findCanvas(component: java.awt.Component): java.awt.Canvas? =
            if (component is java.awt.Canvas) component
            else (component as? java.awt.Container)?.components?.firstNotNullOfOrNull(::findCanvas)
        val canvas = checkNotNull(findCanvas(host.contentPane))
        javax.swing.SwingUtilities.invokeAndWait { host.toFront() }
        fun nativeShortcut(digit: Int) {
            val requestFocus = sourceField("Markdown body")
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
        sourceField("Markdown body").assertTextContains("## Working practices", substring = true)
        nativeShortcut(KeyEvent.VK_1)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Quality policy (sample) (Draft)").fetchSemanticsNodes().size == 1
        }
        fun move(type: Int, x: Int, y: Int) {
            javax.swing.SwingUtilities.invokeAndWait {
                val point = javax.swing.SwingUtilities.convertPoint(host.contentPane, x, y, canvas)
                canvas.dispatchEvent(MouseEvent(canvas, type, System.currentTimeMillis(), 0, point.x, point.y, 0, false))
            }
        }
        for (split in listOf("Resize left panel", "Resize right panel")) {
            val bounds = compose.onNodeWithContentDescription(split).fetchSemanticsNode().boundsInRoot
            move(MouseEvent.MOUSE_ENTERED, (bounds.center.x / density).toInt(), (bounds.center.y / density).toInt())
            move(MouseEvent.MOUSE_MOVED, (bounds.center.x / density).toInt(), (bounds.center.y / density).toInt())
            compose.waitUntil(5_000) { canvas.cursor.type == java.awt.Cursor.E_RESIZE_CURSOR }
        }
        fun capture(name: String, awaitIdle: Boolean = true) {
            if (awaitIdle) { compose.waitForIdle(); robot.delay(150) }
            check(ImageIO.write(robot.createScreenCapture(java.awt.Rectangle(host.locationOnScreen, host.size)),
                "png", File(output, name)))
        }
        fun selectMode(name: String) {
            compose.onNodeWithContentDescription("View $name").activate()
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasContentDescription("View $name") and isSelected()).fetchSemanticsNodes().size == 1 &&
                    (compose.onAllNodesWithContentDescription("Markdown body").fetchSemanticsNodes().isNotEmpty() == (name != "Preview"))
            }
            compose.waitForIdle()
        }
        fun captureTooltip(name: String, tool: String = "Project", number: Int = 1) {
            compose.mainClock.autoAdvance = false
            val bounds = compose.onNodeWithContentDescription("Toggle $tool panel").fetchSemanticsNode().boundsInRoot
            val shortcut = if (isMacOS) "\u2318$number" else "Alt+$number"
            move(MouseEvent.MOUSE_EXITED, -10, -10)
            move(MouseEvent.MOUSE_ENTERED, (bounds.center.x / density).toInt(), (bounds.center.y / density).toInt())
            move(MouseEvent.MOUSE_MOVED, (bounds.center.x / density).toInt(), (bounds.center.y / density).toInt())
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(shortcut).fetchSemanticsNodes().isNotEmpty()
            }
            robot.delay(150)
            capture(name, awaitIdle = false)
            move(MouseEvent.MOUSE_EXITED, -10, -10)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(shortcut).fetchSemanticsNodes().isEmpty()
            }
            compose.mainClock.autoAdvance = true
        }
        capture("jewel-dark.png")
        val inactiveTab = compose.onNodeWithContentDescription("Document tab Risk management plan (sample)").fetchSemanticsNode().boundsInRoot
        move(MouseEvent.MOUSE_MOVED, ((inactiveTab.left + 30) / density).toInt(),
            ((inactiveTab.top + 4) / density).toInt())
        capture("jewel-tab-hover-dark.png")
        move(MouseEvent.MOUSE_EXITED, -10, -10)
        captureTooltip("jewel-tooltip-dark.png")
        captureTooltip("jewel-tooltip-right-dark.png", "History", 4)
        compose.onNodeWithText("Light theme").activate()
        sourceField("Markdown body").assertTextContains("## Working practices", substring = true)
        capture("jewel-light.png")
        move(MouseEvent.MOUSE_MOVED, ((inactiveTab.left + 30) / density).toInt(),
            ((inactiveTab.top + 4) / density).toInt())
        capture("jewel-tab-hover-light.png")
        move(MouseEvent.MOUSE_EXITED, -10, -10)
        captureTooltip("jewel-tooltip-light.png")
        captureTooltip("jewel-tooltip-right-light.png", "History", 4)
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
        sourceField("Markdown body").assertTextContains("Unsaved sample risk notes", substring = true)
        compose.onNodeWithContentDescription("Document tab Quality policy (sample)").activate()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Document tab Quality policy (sample)") and isSelected()).fetchSemanticsNodes().size == 1
        }
        for (theme in listOf("light", "dark")) {
            if (theme == "dark") compose.onNodeWithText("Dark theme").activate()
            selectMode("Preview")
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Table of contents").fetchSemanticsNodes().isNotEmpty() }
            capture("document-preview-$theme.png")
            selectMode("Split")
            capture("document-split-$theme.png")
            selectMode("Source")
            capture("document-source-$theme.png")
        }
        selectMode("Preview")
        compose.onNodeWithText("Table of contents").activate()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Go to Responsibilities").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Go to Responsibilities").activate()
        compose.onNodeWithContentDescription("Rendered document content").performScrollToNode(hasText("[Image: Synthetic workflow]"))
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Synthetic workflow").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Synthetic workflow").performScrollTo()
        capture("document-image-dark.png")
    }
}
