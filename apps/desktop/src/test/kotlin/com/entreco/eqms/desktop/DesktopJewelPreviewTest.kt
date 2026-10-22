package com.entreco.eqms.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.ui.ProjectScreen
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
                    "Review, approval, signatures, and publication are not available in this scaffold.\n",
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
        compose.setContent { ProjectScreen(projects, documents) }
        compose.onNodeWithText("Open project").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Directory path").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Directory path").performTextReplacement(project.toString())
        compose.onNodeWithText("Go").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Select directory") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Select directory").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Quality policy (sample) (Draft)").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Quality policy (sample) (Draft)").performClick()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("## Working practices", substring = true)
        fun capture(name: String) {
            compose.waitForIdle()
            check(ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(output, name)))
        }
        capture("jewel-dark.png")
        compose.onNodeWithText("Light theme").performClick()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("## Working practices", substring = true)
        capture("jewel-light.png")
        compose.onNodeWithText("History").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Compare").fetchSemanticsNodes().size == 1
        }
        capture("jewel-history.png")
        compose.onNodeWithText("Compare").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("+## Purpose", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Close history").assertIsEnabled()
        compose.onNodeWithText("+## Purpose", substring = true).performScrollTo()
        capture("jewel-diff.png")
    }
}
