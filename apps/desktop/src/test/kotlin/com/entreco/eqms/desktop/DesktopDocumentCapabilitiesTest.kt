package com.entreco.eqms.desktop

import java.io.IOException
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DesktopDocumentCapabilitiesTest {
    private val id = "DOC-${"0".repeat(32)}"
    private val revision = "a".repeat(40)
    private val document = """{"id":"$id","title":"Policy","body":"# Policy\n","status":"Draft","revision":"$revision","content_hash":"sha256:${"b".repeat(64)}","event_hash":"${"c".repeat(64)}","configured_author":"Test <test@example.invalid>","local_timestamp":"1.000000000"}"""
    private fun response(command: String, data: String) =
        """{"schema_version":1,"status":"ok","command":"document.$command","data":$data}"""

    @Test
    fun createAndSaveSendBodyOnStdinAndArgumentsWithoutShell() = runBlocking {
        val calls = mutableListOf<Pair<List<String>, String?>>()
        val capabilities = DesktopDocumentCapabilities("test-qms", DocumentExecutor { executable, args, input ->
            assertEquals("test-qms", executable)
            calls += args to input
            ProcessResult(0, response(args[1], document))
        })
        val draft = capabilities.createDocument("project with spaces", "Policy", "# Policy\n").getOrThrow()
        assertEquals(id, draft.id)
        assertEquals(
            listOf("document", "create", "--json", "project with spaces", "--title", "Policy") to "# Policy\n",
            calls.first(),
        )
        capabilities.saveDocument("project with spaces", id, revision, "Policy", "# Policy\n").getOrThrow()
        assertEquals(listOf(id, "--title", "Policy", "--expected", revision), calls.last().first.takeLast(5))
    }

    @Test
    fun listHistoryAndDiffDecodeExpectedShapes() = runBlocking {
        val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, args, _ ->
            ProcessResult(0, response(args[1], if (args[1] == "diff") "\"-old\\n+new\\n\"" else "[$document]"))
        })
        assertEquals(1, capabilities.listDocuments("project").getOrThrow().size)
        assertEquals(revision, capabilities.documentHistory("project", id).getOrThrow().single().revision)
        assertEquals("-old\n+new\n", capabilities.documentDiff("project", id, revision, revision).getOrThrow())
    }

    @Test
    fun malformedProtocolAndWrongDocumentCannotLookSuccessful() = runBlocking {
        val invalid = listOf(
            "not JSON",
            response("save", document).replace("\"schema_version\":1", "\"schema_version\":\"1\""),
            response("create", document),
            response("save", document.replace("\"Draft\"", "\"Approved\"")),
            response("save", document.replace(id, "DOC-${"1".repeat(32)}")),
            response("save", document.replace("\"revision\":\"$revision\"", "\"revision\":42")),
            """{"schema_version":1,"status":"ok","command":"document.save"}""",
        )
        invalid.forEach { output ->
            val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ -> ProcessResult(0, output) })
            assertTrue(capabilities.saveDocument("project", id, revision, "Policy", "edits").isFailure, output)
        }
    }

    @Test
    fun operationalAndLaunchErrorsAreVisible() = runBlocking {
        val failed = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ ->
            ProcessResult(1, """{"schema_version":1,"status":"error","error":{"code":"OPERATION_FAILED","message":"stale save"}}""")
        })
        assertEquals("stale save", failed.createDocument("project", "Policy", "").exceptionOrNull()?.message)
        val missing = DesktopDocumentCapabilities("missing", DocumentExecutor { _, _, _ -> throw IOException("not found") })
        assertEquals("not found", missing.listDocuments("project").exceptionOrNull()?.message)
    }
}
