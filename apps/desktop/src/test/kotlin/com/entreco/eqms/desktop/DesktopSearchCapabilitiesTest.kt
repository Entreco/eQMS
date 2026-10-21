package com.entreco.eqms.desktop

import java.io.IOException
import com.entreco.eqms.ui.SearchIndexOutOfDateException
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class DesktopSearchCapabilitiesTest {
    private val id = "DOC-${"0".repeat(32)}"
    private val revision = "a".repeat(40)
    private val hit = """{"id":"$id","title":"Policy","revision":"$revision","configured_author":"A","local_timestamp":"1","excerpt":"<literal> & text"}"""
    private val emptyPage = """{"hits":[],"next_offset":null}"""
    private val results = """{"snapshot":"token","historical":true,"documents":{"hits":[$hit],"next_offset":null},"authors":$emptyPage,"content":$emptyPage}"""
    private fun response(command: String, data: String) =
        """{"schema_version":2,"status":"ok","command":"$command","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":$data,"error":null}"""

    @Test
    fun prepareQueryAndIndependentOffsetsUseDirectArgv() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val capabilities = DesktopDocumentCapabilities("native path", DocumentExecutor { executable, args, input ->
            assertEquals("native path", executable)
            assertNull(input)
            calls += args
            ProcessResult(0, if ("--prepare" in args) response("search.prepare", """{"snapshot":"token","indexed_revisions":17}""") else response("search", results))
        })
        assertEquals(17, capabilities.prepareSearch("project path").getOrThrow().indexedRevisions)
        assertEquals(listOf("search", "--json", "project path", "--prepare"), calls[0])
        val query = "literal \" OR *; $(ignored)"
        val result = capabilities.searchDocuments("project path", query, "token", 0, 5, 0).getOrThrow()
        assertTrue(result.historical)
        assertEquals("<literal> & text", result.documents.hits.single().excerpt)
        assertEquals(
            listOf("search", "--json", "project path", "--query", query, "--snapshot", "token",
                "--documents-offset", "0", "--authors-offset", "5", "--content-offset", "0"),
            calls[1],
        )
        assertTrue(capabilities.searchDocuments("project", "x", authorsOffset = 5).isFailure)
        assertEquals(2, calls.size)
    }

    @Test
    fun invalidSearchProtocolAndPaginationAreFailures() = runBlocking {
        val invalid = listOf(
            "not JSON",
            response("document.list", results),
            response("search", results).replace("\"schema_version\":2", "\"schema_version\":\"2\""),
            response("search", results).replace("\"historical\":true", "\"historical\":\"true\""),
            response("search", results).replace("\"snapshot\":\"token\"", "\"snapshot\":\"\""),
            response("search", results).replace("\"next_offset\":null", "\"next_offset\":1"),
            response("search", results).replace(id, "wrong"),
            response("search", results).replace(revision, "wrong"),
            response("search", results).replace("\"excerpt\":\"<literal> & text\"", "\"excerpt\":42"),
            response("search", results).replace("\"content\":$emptyPage", "\"other\":$emptyPage"),
            response("search", results.replace("[$hit]", "[${List(6) { hit }.joinToString(",")}]")),
        )
        invalid.forEach { output ->
            val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ -> ProcessResult(0, output) })
            assertTrue(capabilities.searchDocuments("project", "x").isFailure, output)
        }
        val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ -> ProcessResult(0, response("search", results)) })
        assertTrue(capabilities.searchDocuments("project", "x", "different").isFailure)
    }

    @Test
    fun preparationErrorsLaunchFailuresAndCancellationRemainDistinct() = runBlocking {
        val failed = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ ->
            ProcessResult(1, """{"schema_version":2,"status":"error","command":"search","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":null,"error":{"code":"OPERATION_FAILED","message":"Index stale; prepare search."}}""")
        })
        assertEquals("Index stale; prepare search.", failed.searchDocuments("project", "x").exceptionOrNull()?.message)
        val missing = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ -> throw IOException("not found") })
        assertEquals("not found", missing.prepareSearch("project").exceptionOrNull()?.message)
        val cancelled = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ -> throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { cancelled.prepareSearch("project") }
        listOf(
            """{"snapshot":"s","indexed_revisions":-1}""",
            """{"snapshot":"s","indexed_revisions":"1"}""",
            """{"snapshot":"","indexed_revisions":1}""",
        ).forEach { data ->
            val invalid = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ -> ProcessResult(0, response("search.prepare", data)) })
            assertTrue(invalid.prepareSearch("project").isFailure)
        }
    }

    @Test
    fun exactRevisionReadChecksIdentityAndRevision() = runBlocking {
        val data = """{"id":"$id","title":"Old policy","body":"old","status":"Draft","revision":"$revision","content_hash":"sha256:${"b".repeat(64)}","event_hash":"${"c".repeat(64)}","configured_author":"Test","local_timestamp":"1"}"""
        var arguments = emptyList<String>()
        val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, args, _ ->
            arguments = args
            ProcessResult(0, response("document.show", data))
        })
        assertEquals("old", capabilities.showDocument("project path", id, revision).getOrThrow().body)
        assertEquals(listOf("document", "show", "--json", "project path", id, "--revision", revision), arguments)
        assertTrue(capabilities.showDocument("project", id, "d".repeat(40)).isFailure)
        assertEquals("old", capabilities.showLatestDocument("project path", id).getOrThrow().body)
        assertEquals(listOf("document", "show", "--json", "project path", id), arguments)
        assertTrue(capabilities.showLatestDocument("project", "DOC-${"1".repeat(32)}").isFailure)
    }

    @Test
    fun onlyDefinitiveStaleIndexFailureIsRetryable() = runBlocking {
        val message = "Search index is out of date. Run qms search <path> --prepare."
        fun capabilities(code: String, text: String) = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ ->
            ProcessResult(1, """{"schema_version":2,"status":"error","command":"search","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":null,"error":{"code":"$code","message":"$text"}}""")
        })
        assertIs<SearchIndexOutOfDateException>(capabilities("OPERATION_FAILED", message).searchDocuments("project", "x").exceptionOrNull())
        assertIs<IOException>(capabilities("OPERATION_FAILED", "Please prepare search.").searchDocuments("project", "x").exceptionOrNull())
        assertIs<IOException>(capabilities("OTHER", message).searchDocuments("project", "x").exceptionOrNull())
        Unit
    }
}
