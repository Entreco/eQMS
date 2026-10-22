package com.entreco.eqms.desktop

import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.ui.*
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class DesktopSearchBenchmarkTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun preparedReferenceWorkspaceFinalInputToLayout() {
        val project = System.getenv("EQMS_SEARCH_BENCHMARK_WORKSPACE")
        assumeTrue("Set EQMS_SEARCH_BENCHMARK_WORKSPACE to run the real 10k-document search benchmark.", !project.isNullOrBlank())
        val executable = System.getenv("EQMS_QMS_PATH")
        require(!executable.isNullOrBlank()) { "Set EQMS_QMS_PATH to the release qms executable." }
        val native = DesktopDocumentCapabilities(executable)
        val fixture = Json.parseToJsonElement(File(project, ".eqms/search-benchmark.json").readText()).jsonObject
        assertEquals(10_000, fixture.getValue("documents").jsonPrimitive.int)
        assertTrue(fixture.getValue("current_body_bytes").jsonPrimitive.long >= 100L * 1024 * 1024)
        val preparationStarted = System.nanoTime()
        val preparation = runBlocking { native.prepareSearch(project!!).getOrThrow() }
        val preparationMs = (System.nanoTime() - preparationStarted) / 1_000_000.0
        assertEquals(10_000 + fixture.getValue("historical_revisions").jsonPrimitive.int, preparation.indexedRevisions)
        val failure = AtomicReference<Throwable?>()
        val timings = Collections.synchronizedList(mutableListOf<SearchRenderTiming>())
        val nativeTimings = Collections.synchronizedMap(mutableMapOf<String, MutableList<Double>>())
        val capabilities = object : DocumentCapabilities by native {
            override suspend fun listDocuments(project: String) = Result.success(emptyList<DocumentSnapshot>())
            override suspend fun searchDocuments(
                project: String, query: String, snapshot: String?,
                documentsOffset: Int, authorsOffset: Int, contentOffset: Int,
            ): Result<SearchResults> {
                val started = System.nanoTime()
                return native.searchDocuments(project, query, snapshot, documentsOffset, authorsOffset, contentOffset)
                .also { nativeTimings.getOrPut(query) { mutableListOf() }.add((System.nanoTime() - started) / 1_000_000.0) }
                .onFailure { failure.set(it) }
                .onSuccess {
                    check(!it.historical) { "Warm current query unexpectedly fell back to history." }
                    check(it.content.hits.isNotEmpty()) { "Reference query did not produce expected body matches." }
                }
            }
        }
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen(
                    project!!, capabilities, { it() }, { _, _ -> },
                    onSearchResultsLaidOut = { timings += it },
                )
            }
        }
        val queries = listOf("And", "risk", "managment", "trace")
        fun query(text: String): SearchRenderTiming {
            val previousGeneration = synchronized(timings) { timings.maxOfOrNull { it.generation } ?: -1 }
            compose.onNodeWithContentDescription("Search saved drafts").performTextReplacement(text)
            fun matches() = synchronized(timings) { timings.filter { it.query == text && it.generation > previousGeneration } }
            compose.waitUntil(30_000) { failure.get() != null || matches().isNotEmpty() }
            failure.get()?.let { throw AssertionError("Real native search failed", it) }
            return matches().single().also {
                assertEquals(text, it.query)
                assertTrue(it.generation > previousGeneration)
                synchronized(timings) { assertEquals(1, timings.count { row -> row.generation == it.generation }) }
            }
        }
        repeat(20) { queries.forEach { query(it) } }
        val samples = queries.associateWith { mutableListOf<SearchRenderTiming>() }
        repeat(100) { queries.forEach { samples.getValue(it) += query(it) } }
        fun percentile(values: List<Double>, percentile: Double): Double =
            values.sorted()[(ceil(values.size * percentile).toInt() - 1).coerceAtLeast(0)]
        fun command(vararg args: String): String = try {
            val process = ProcessBuilder(*args).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText() }.trim().also { process.waitFor() }
        } catch (_: java.io.IOException) { "unavailable" }
        val report = buildJsonObject {
            put("benchmark", "prepared-search-final-input-to-compose-layout")
            put("workspace", project)
            put("indexed_revisions", preparation.indexedRevisions)
            put("fixture", fixture)
            put("snapshot", preparation.snapshot)
            put("initial_preparation_ms", preparationMs)
            put("debounce_ms", 10)
            put("warmups_per_query", 20)
            put("runs_per_query", 100)
            put("initial_list", "empty override; real search and no-op preparation")
            put("os", "${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            put("arch", System.getProperty("os.arch"))
            put("cpu", if (System.getProperty("os.name") == "Mac OS X") command("sysctl", "-n", "machdep.cpu.brand_string") else System.getenv("PROCESSOR_IDENTIFIER") ?: "unavailable")
            put("physical_memory_bytes", if (System.getProperty("os.name") == "Mac OS X") command("sysctl", "-n", "hw.memsize") else "unavailable")
            put("available_processors", Runtime.getRuntime().availableProcessors())
            put("java", System.getProperty("java.runtime.version"))
            put("gc_collection_time_ms", java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0) })
            put("revision", command("git", "rev-parse", "HEAD"))
            put("working_tree", command("git", "status", "--porcelain"))
            put("binary", executable)
            put("binary_version", command(executable, "--version"))
            put("binary_sha256", File(executable).inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65536)
                var count = input.read(buffer)
                while (count != -1) { digest.update(buffer, 0, count); count = input.read(buffer) }
                digest.digest().joinToString("") { "%02x".format(it) }
            })
            put("queries", buildJsonObject {
                samples.forEach { (text, rows) ->
                    put(text, buildJsonObject {
                        val values = rows.map { it.elapsedMilliseconds }
                        put("p50_ms", percentile(values, 0.50))
                        put("p95_ms", percentile(values, 0.95))
                        put("historical", rows.any { it.historical })
                        put("samples_ms", buildJsonArray { values.forEach { add(it) } })
                        put("native_adapter_samples_ms", buildJsonArray { nativeTimings.getValue(text).takeLast(rows.size).forEach { add(it) } })
                    })
                }
            })
        }
        println(report)
        samples.forEach { (text, rows) ->
            assertTrue(percentile(rows.map { it.elapsedMilliseconds }, 0.95) <= 150.0, "$text p95 exceeds 150 ms; see JSON report.")
        }
    }
}
