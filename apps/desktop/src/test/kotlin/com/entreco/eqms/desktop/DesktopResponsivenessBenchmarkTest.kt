package com.entreco.eqms.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.feature.document.DocumentCapabilities
import com.entreco.eqms.feature.document.DocumentHistorySnapshot
import com.entreco.eqms.feature.document.DocumentScreen
import com.entreco.eqms.feature.document.DocumentSnapshot
import com.entreco.eqms.feature.search.SearchPreparation
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Collections
import kotlin.math.ceil
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class DesktopResponsivenessBenchmarkTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun projectOpenAndHistoryReadiness() {
        val project = System.getenv("EQMS_RESPONSIVENESS_WORKSPACE")
        assumeTrue(
            "Set EQMS_RESPONSIVENESS_WORKSPACE to a generated EQMS-094 fixture.",
            !project.isNullOrBlank(),
        )
        val executable = System.getenv("EQMS_QMS_PATH")
        require(!executable.isNullOrBlank()) { "Set EQMS_QMS_PATH to the release qms executable." }
        val fixture = Json.parseToJsonElement(File(project, ".eqms/search-benchmark.json").readText()).jsonObject
        val expectedDocuments = fixture.getValue("documents").jsonPrimitive.int
        val expectedRevisions = fixture.getValue("revisions_for_first_document").jsonPrimitive.int
        val processSamples = Collections.synchronizedMap(mutableMapOf<String, MutableList<Double>>())
        val documentExecutor = DocumentExecutor { command, arguments, input ->
            val started = System.nanoTime()
            executeProcess(command, arguments, input).also {
                val operation = arguments.getOrNull(1) ?: arguments.firstOrNull() ?: "unknown"
                processSamples.getOrPut(operation) { Collections.synchronizedList(mutableListOf()) }
                    .add((System.nanoTime() - started) / 1_000_000.0)
            }
        }
        val native = DesktopDocumentCapabilities(executable, documentExecutor)
        val adapterSamples = Collections.synchronizedMap(mutableMapOf<String, MutableList<Double>>())
        val capabilities = object : DocumentCapabilities by native {
            override suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>> =
                measured("list", adapterSamples) { native.listDocuments(project) }

            override suspend fun documentHistorySnapshot(
                project: String,
                id: String,
            ): Result<DocumentHistorySnapshot> =
                measured("history-snapshot", adapterSamples) {
                    native.documentHistorySnapshot(project, id)
                }

            override suspend fun prepareSearch(project: String): Result<SearchPreparation> =
                measured("search-prepare", adapterSamples) { native.prepareSearch(project) }
        }
        val workspaceSamples = Collections.synchronizedList(mutableListOf<Double>())
        val projects = DesktopProjectCapabilities(
            WorkspaceCli(
                executable,
                ProcessExecutor { command, arguments ->
                    val started = System.nanoTime()
                    executeProcess(command, arguments).also {
                        workspaceSamples += (System.nanoTime() - started) / 1_000_000.0
                    }
                },
            ),
        )
        var generation by mutableIntStateOf(-1)
        compose.setContent {
            IntUiTheme(isDark = true) {
                if (generation >= 0) {
                    key(generation) {
                        DocumentScreen(project!!, capabilities, { _, _ -> })
                    }
                }
            }
        }

        val openToList = mutableListOf<Double>()
        val historyReady = mutableListOf<Double>()
        val documentLabel = "And - Some Document 0 (Draft)"
        val runs = System.getenv("EQMS_RESPONSIVENESS_RUNS")?.toIntOrNull() ?: 30
        require(runs >= 20) { "EQMS_RESPONSIVENESS_RUNS must be at least 20 for p95 reporting." }
        repeat(runs) {
            val started = System.nanoTime()
            runBlocking { projects.openProject(project!!, create = false).getOrThrow() }
            compose.runOnIdle { generation++ }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(documentLabel).fetchSemanticsNodes().isNotEmpty()
            }
            openToList += (System.nanoTime() - started) / 1_000_000.0

            compose.onNodeWithText(documentLabel).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Show history").fetchSemanticsNodes().isNotEmpty()
            }
            val historySample = adapterSamples["history-snapshot"]?.size ?: 0
            val historyStarted = System.nanoTime()
            compose.onNodeWithText("Show history").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Older revision").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("No lifecycle events recorded.").fetchSemanticsNodes().isNotEmpty() &&
                    (adapterSamples["history-snapshot"]?.size ?: 0) > historySample
            }
            compose.waitForIdle()
            historyReady += (System.nanoTime() - historyStarted) / 1_000_000.0
        }
        assertTrue(openToList.size == runs && historyReady.size == runs)
        assertTrue(adapterSamples["list"]?.size == runs)
        assertTrue(adapterSamples["history-snapshot"]?.size == runs)
        assertTrue(adapterSamples["search-prepare"].isNullOrEmpty())
        assertTrue(expectedDocuments > 0)

        fun percentile(values: List<Double>, percentile: Double): Double =
            values.sorted()[ceil(values.size * percentile).toInt() - 1]

        val projectOpenP95 = percentile(openToList, 0.95)
        val historyP95 = percentile(historyReady, 0.95)
        val budgets = when (expectedDocuments to expectedRevisions) {
            8 to 2 -> 1_050.0 to 700.0
            16 to 4 -> 1_900.0 to 1_100.0
            else -> null
        }
        budgets?.let { (projectBudget, historyBudget) ->
            assertTrue(
                projectOpenP95 <= projectBudget,
                "Project/list p95 $projectOpenP95 ms exceeds $projectBudget ms.",
            )
            assertTrue(
                historyP95 <= historyBudget,
                "History p95 $historyP95 ms exceeds $historyBudget ms.",
            )
        }

        fun samples(values: List<Double>) = buildJsonArray { values.forEach { add(it) } }
        fun stage(name: String, values: List<Double>) = buildJsonObject {
            put("name", name)
            put("samples_ms", samples(values))
            put("p50_ms", percentile(values, 0.50))
            put("p95_ms", percentile(values, 0.95))
        }

        val binaryFile = File(executable)
        val report = buildJsonObject {
            put("benchmark", "EQMS-094 project-open-and-history-ui-readiness")
            put("dataset", fixture)
            put("sample_count", runs)
            put("profile", "release")
            put("source_revision", git("rev-parse", "HEAD"))
            put("worktree", git("status", "--porcelain"))
            put("os", "${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            put("architecture", System.getProperty("os.arch"))
            put("cpu", command("sysctl", "-n", "machdep.cpu.brand_string"))
            put("memory_bytes", command("sysctl", "-n", "hw.memsize").toLong())
            put("java", System.getProperty("java.runtime.version"))
            put("qms_version", command(executable, "--version"))
            put("qms_sha256", sha256(binaryFile))
            put("first_project_open_ms", workspaceSamples.first())
            put("workspace_verify_process_ms", samples(workspaceSamples))
            put("project_open_to_first_list_content", stage("project-open-to-first-list-content", openToList))
            put("history_toggle_to_usable_content", stage("history-toggle-to-usable-content", historyReady))
            put("process_samples_ms", buildJsonObject {
                processSamples.forEach { (name, values) -> put(name, samples(values)) }
            })
            put("adapter_samples_ms", buildJsonObject {
                adapterSamples.forEach { (name, values) -> put(name, samples(values)) }
            })
            put("limitations", "First sample follows fixture generation; OS page cache is not controlled. Search preparation may overlap later reads. Git child counts are captured separately with GIT_TRACE2_EVENT.")
        }
        println("EQMS-094-BENCHMARK=${report}")
        val output = System.getenv("EQMS_RESPONSIVENESS_REPORT")
        if (!output.isNullOrBlank()) Path.of(output).toFile().writeText(report.toString())
    }

    private suspend fun <T> measured(
        name: String,
        samples: MutableMap<String, MutableList<Double>>,
        action: suspend () -> Result<T>,
    ): Result<T> {
        val started = System.nanoTime()
        return action().also {
            samples.getOrPut(name) { Collections.synchronizedList(mutableListOf()) }
                .add((System.nanoTime() - started) / 1_000_000.0)
        }
    }

    private fun git(vararg arguments: String): String =
        command("git", *arguments)

    private fun command(vararg arguments: String): String =
        executeProcess(arguments.first(), arguments.drop(1)).output.trim()

    private fun sha256(file: File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            var count = input.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
}
