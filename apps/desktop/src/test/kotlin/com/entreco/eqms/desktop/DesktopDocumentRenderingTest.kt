package com.entreco.eqms.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.document.DocumentRenderTiming
import com.entreco.eqms.feature.document.DocumentScreen
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.imageio.ImageIO
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ceil
import kotlin.test.*

class DesktopDocumentRenderingTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun nativeModelsSavedProvenanceAndWarmInputToLayoutBudgets() {
        val executable = System.getenv("EQMS_QMS_PATH")
        assumeTrue("Set EQMS_QMS_PATH to exercise the real renderer and measured UI.", !executable.isNullOrBlank())
        val root = temporary.newFolder("renderer")
        for (args in listOf(listOf("init", "--quiet"), listOf("config", "user.name", "Renderer Test"),
            listOf("config", "user.email", "renderer@example.invalid"))) {
            val result = executeProcess("git", listOf("-C", root.path) + args)
            assertEquals(0, result.exitCode, result.output)
        }
        val capabilities = DesktopDocumentCapabilities(requireNotNull(executable))
        val saved = runBlocking {
            DesktopProjectCapabilities(WorkspaceCli(executable = executable)).openProject(root.path, create = true).getOrThrow()
            val assets = File(root, "assets").apply { mkdir(); check(isDirectory) }
            val image = java.awt.image.BufferedImage(640, 200, java.awt.image.BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply {
                color = java.awt.Color(35, 44, 64); fillRect(0, 0, 640, 200)
                color = java.awt.Color(70, 125, 245); fillRoundRect(30, 50, 130, 100, 18, 18)
                color = java.awt.Color.WHITE; font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 24)
                drawString("Draft  >  Review  >  Effective", 185, 110); dispose()
            }
            check(ImageIO.write(image, "png", File(assets, "workflow.png")))
            capabilities.createDocument(root.path, "Rendering fixture",
                "# Purpose\n\nKeep quality work **clear**, traceable and easy to find.\n\n" +
                    "## Responsibilities\n\n| Role | Activity |\n| --- | --- |\n| Author | Prepare content |\n| Reviewer | Inspect the saved revision |\n\n" +
                    "![Synthetic workflow](assets/workflow.png)\n\n" +
                    "See [purpose](#purpose) and the supporting note[^scope].\n\n[^scope]: Synthetic content only, not approved evidence.\n").getOrThrow()
        }
        runBlocking {
            val model = capabilities.renderSaved(root.path, saved).getOrThrow()
            assertEquals(saved.contentHash, model.source.contentHash)
            assertEquals(saved.revision, model.source.revision)
            assertEquals("purpose", model.toc.first().anchor)
            val dirty = capabilities.renderBuffer(root.path, "Dirty", "## New\n\n**Changed**").getOrThrow()
            assertEquals("unsaved", dirty.source.kind)
            assertEquals("", dirty.source.contentHash)
            assertTrue(dirty.nodes.any { it.kind == "paragraph" })
            assertTrue(capabilities.renderBuffer(root.path, "", "x".repeat(1024 * 1024 + 1)).isFailure)
            assertTrue(capabilities.readDocumentImage(root.path, "../secret").isFailure)
            assertTrue(capabilities.openDocumentLink("javascript:bad").isFailure)
        }
        val timings = CopyOnWriteArrayList<DocumentRenderTiming>()
        var dark by mutableStateOf(true)
        compose.setContent {
            IntUiTheme(isDark = dark) {
                Box(Modifier.requiredSize(1000.dp, 720.dp)) {
                    DocumentScreen(root.path, capabilities, { _, _ -> }, onDocumentRendered = timings::add)
                }
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Rendering fixture (Draft)").fetchSemanticsNodes().isNotEmpty()
        }
        val openStarted = System.nanoTime()
        compose.onNodeWithText("Rendering fixture (Draft)").performClick()
        compose.waitUntil(30_000) { timings.isNotEmpty() }
        val coldOpenMillis = (System.nanoTime() - openStarted) / 1_000_000.0
        System.getenv("EQMS_JEWEL_PREVIEW_DIR")?.let { destination ->
            val directory = File(destination).apply { mkdirs(); check(isDirectory) }
            compose.onNodeWithContentDescription("Toggle Project panel").performClick()
            compose.onNodeWithContentDescription("Toggle Review panel").performClick()
            fun capture(name: String) {
                compose.waitForIdle()
                val image = compose.onNodeWithContentDescription("Document editor panel").captureToImage()
                check(ImageIO.write(image.toAwtImage(), "png", File(directory, name)))
            }
            for (theme in listOf(true, false)) {
                compose.runOnIdle { dark = theme }
                val suffix = if (theme) "dark" else "light"
                for (mode in listOf("Preview", "Split", "Source")) {
                    compose.onNodeWithContentDescription("View $mode").performClick()
                    capture("document-${mode.lowercase()}-$suffix.png")
                }
            }
            compose.onNodeWithContentDescription("View Preview").performClick()
            compose.onNodeWithContentDescription("Rendered document content").performScrollToIndex(5)
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Synthetic workflow").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Synthetic workflow").performScrollTo()
            capture("document-image-light.png")
            compose.onNodeWithContentDescription("Rendered document content").performScrollToIndex(0)
        }
        compose.runOnIdle { dark = true }
        compose.onNodeWithContentDescription("View Split").performClick()
        val section = "## Working practices\n\n" +
            "Keep quality work traceable and readable. ".repeat(18) +
            "\n\nA **strong** statement, a [reference](https://example.invalid), and `code`.\n\n" +
            "- Preserve the compact source.\n- Save explicitly.\n\n"
        val reports = mutableListOf<JsonObject>()
        for ((bytes, budget) in listOf(50 * 1024 to 150.0, 1024 * 1024 to 500.0)) {
            val fixture = section.repeat(bytes / section.length + 1).take(bytes - 16)
            val observations = mutableListOf<Double>()
            repeat(33) { sample ->
                val body = ("Sample ${sample.toString().padStart(3, '0')}\n\n$fixture").padEnd(bytes, ' ')
                assertEquals(bytes, body.toByteArray(Charsets.UTF_8).size)
                val digest = bodyDigest(body)
                compose.onNodeWithContentDescription("Markdown body").performSemanticsAction(SemanticsActions.SetText) {
                    assertTrue(it(androidx.compose.ui.text.AnnotatedString(body)))
                }
                compose.waitUntil(30_000) { timings.any { it.inputDigest == digest } }
                if (sample >= 3) observations += timings.last { it.inputDigest == digest }.elapsedMillis
            }
            val sorted = observations.sorted()
            reports += buildJsonObject {
                put("fixture_version", 1); put("body_bytes", bytes); put("samples", observations.size)
                put("warmups", 3); put("coalescing_ms", 100); put("budget_p95_ms", budget)
                put("p50_ms", sorted[ceil(sorted.size * .5).toInt() - 1])
                put("p95_ms", sorted[ceil(sorted.size * .95).toInt() - 1])
                put("maximum_ms", sorted.last())
                put("observations_ms", JsonArray(observations.map(::JsonPrimitive)))
            }
        }
        System.getenv("EQMS_RENDER_REPORT_DIR")?.let { destination ->
            val directory = File(destination).apply { mkdirs(); check(isDirectory) }
            File(directory, "render-latency.json").writeText(buildJsonObject {
                put("os", System.getProperty("os.name")); put("architecture", System.getProperty("os.arch"))
                put("java", System.getProperty("java.version")); put("results", JsonArray(reports))
                put("cold_open_click_to_observed_layout_ms", coldOpenMillis)
            }.toString())
        }
        for (report in reports) {
            val p95 = report.getValue("p95_ms").jsonPrimitive.double
            val budget = report.getValue("budget_p95_ms").jsonPrimitive.double
            assertTrue(p95 <= budget, "Input-to-layout p95 $p95 ms exceeds $budget ms: $report")
        }
    }
}
