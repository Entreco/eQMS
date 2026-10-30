package com.entreco.eqms.desktop

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import com.entreco.eqms.feature.document.DocumentStatus

class DesktopDocumentCapabilitiesTest {
    private val id = "DOC-${"0".repeat(32)}"
    private val revision = "a".repeat(40)
    private val document = """{"id":"$id","title":"Policy","body":"# Policy\n","status":"Draft","revision":"$revision","content_hash":"sha256:${"b".repeat(64)}","event_hash":"${"c".repeat(64)}","configured_author":"Test <test@example.invalid>","local_timestamp":"1.000000000"}"""
    private fun response(command: String, data: String) =
        """{"schema_version":2,"status":"ok","command":"document.$command","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":$data,"error":null}"""

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
    fun historySnapshotUsesOneRequestAndRejectsInconsistentRevisionAndLifecycleData() = runBlocking {
        val reviewedRevision = "d".repeat(40)
        val reviewedDocument = document
            .replace("\"revision\":\"$revision\"", "\"revision\":\"$reviewedRevision\"")
            .replace("\"event_hash\":\"${"c".repeat(64)}\"", "\"event_hash\":\"${"d".repeat(64)}\"")
            .replace("\"status\":\"Draft\"", "\"status\":\"In Review\"")
        val event = """{"revision":"$reviewedRevision","event_hash":"${"d".repeat(64)}","content_hash":"sha256:${"b".repeat(64)}","from_status":"Draft","to_status":"In Review","local_timestamp":"2"}"""
        val valid = """{"revisions":[$reviewedDocument,$document],"lifecycle_events":[$event]}"""
        val calls = mutableListOf<List<String>>()
        val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, args, _ ->
            calls += args
            ProcessResult(0, response("history-snapshot", valid))
        })
        val snapshot = capabilities.documentHistorySnapshot("project", id).getOrThrow()
        assertEquals(1, calls.size)
        assertEquals(listOf("document", "history-snapshot", "--json", "project", id), calls.single())
        assertEquals(listOf(reviewedRevision, revision), snapshot.revisions.map { it.revision })
        assertEquals(reviewedRevision, snapshot.lifecycleEvents.single().revision)

        val inconsistent = listOf(
            """{"revisions":[$reviewedDocument,$document],"lifecycle_events":[]}""",
            """{"revisions":[$reviewedDocument,$document],"lifecycle_events":[$event,$event]}""",
            valid.replace(event, event.replace(reviewedRevision, "e".repeat(40))),
            valid.replace(
                event,
                event.replace(
                    "\"content_hash\":\"sha256:${"b".repeat(64)}\"",
                    "\"content_hash\":\"sha256:${"e".repeat(64)}\"",
                ),
            ),
            """{"revisions":[],"lifecycle_events":[]}""",
        )
        inconsistent.forEach { data ->
            val malformed = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ ->
                ProcessResult(0, response("history-snapshot", data))
            })
            assertTrue(malformed.documentHistorySnapshot("project", id).isFailure, data)
        }
    }

    @Test
    fun malformedProtocolAndWrongDocumentCannotLookSuccessful() = runBlocking {
        val invalid = listOf(
            "not JSON",
            response("save", document).replace("\"schema_version\":2", "\"schema_version\":\"2\""),
            response("create", document),
            response("save", document.replace("\"Draft\"", "\"Approved\"")),
            response("save", document.replace(id, "DOC-${"1".repeat(32)}")),
            response("save", document.replace("\"revision\":\"$revision\"", "\"revision\":42")),
            response("save", document).replace("\"timestamp_unix_ms\":1792600000000", "\"timestamp_unix_ms\":\"bad\""),
            response("save", document).replace("\"findings\":[]", "\"findings\":{}"),
            response("save", document).replace("\"schema_version\":2", "\"schema_version\":1"),
        )
        invalid.forEach { output ->
            val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ -> ProcessResult(0, output) })
            assertTrue(capabilities.saveDocument("project", id, revision, "Policy", "edits").isFailure, output)
        }
    }

    @Test
    fun operationalAndLaunchErrorsAreVisible() = runBlocking {
        val failed = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ ->
            ProcessResult(1, """{"schema_version":2,"status":"error","command":"document.create","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":null,"error":{"code":"OPERATION_FAILED","message":"stale save"}}""")
        })
        assertEquals("stale save", failed.createDocument("project", "Policy", "").exceptionOrNull()?.message)
        val missing = DesktopDocumentCapabilities("missing", DocumentExecutor { _, _, _ -> throw IOException("not found") })
        assertEquals("not found", missing.listDocuments("project").exceptionOrNull()?.message)
    }

    @Test
    fun cancellingHistorySnapshotCancelsAndInterruptsNativeExecution() = runBlocking {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ ->
            started.countDown()
            try {
                CountDownLatch(1).await()
            } catch (error: InterruptedException) {
                interrupted.countDown()
                throw error
            }
            error("Native execution should be interrupted.")
        })
        val request = async(Dispatchers.IO) { capabilities.documentHistorySnapshot("project", id) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
        assertTrue(interrupted.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun lifecycleStatesAreStrictAndTransitionArgumentsAndContentAreValidated() = runBlocking {
        val calls = mutableListOf<List<String>>()
        var state = "Draft"
        var altered = false
        val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, args, input ->
            assertNull(input)
            calls += args
            var data = document.replace("\"Draft\"", "\"$state\"")
            if (args[1] == "transition") data = data.replace(revision, "d".repeat(40))
            if (altered) data = data.replace("sha256:${"b".repeat(64)}", "sha256:${"e".repeat(64)}")
            ProcessResult(0, response(args[1], data))
        })
        val observed = capabilities.showLatestDocument("path with spaces", id).getOrThrow()
        DocumentStatus.entries.forEach { status ->
            state = status.label
            assertEquals(status, capabilities.showLatestDocument("path", id).getOrThrow().status)
        }
        state = "In Review"
        val transitioned = capabilities.transitionDocument("path with spaces", observed, DocumentStatus.InReview).getOrThrow()
        assertEquals(DocumentStatus.InReview, transitioned.status)
        assertEquals(listOf("document", "transition", "--json", "path with spaces", id, "--expected", revision, "--to", "In Review"), calls.last())
        altered = true
        assertTrue(capabilities.transitionDocument("path", observed, DocumentStatus.InReview).isFailure)
        altered = false
        state = "Unknown"
        assertTrue(capabilities.showLatestDocument("path", id).isFailure)
        state = "Approved"
        assertTrue(capabilities.transitionDocument("path", observed, DocumentStatus.InReview).isFailure)
        val validTransition = document.replace("\"Draft\"", "\"In Review\"").replace(revision, "d".repeat(40))
        for (invalid in listOf(
            validTransition.replace(id, "DOC-${"1".repeat(32)}"),
            validTransition.replace("d".repeat(40), revision),
            validTransition.replace("# Policy", "# Unexpected"),
            validTransition.replace("\"status\":\"In Review\",", ""),
        )) {
            val malformed = DesktopDocumentCapabilities("qms", DocumentExecutor { _, _, _ ->
                ProcessResult(0, response("transition", invalid))
            })
            assertTrue(malformed.transitionDocument("path", observed, DocumentStatus.InReview).isFailure, invalid)
        }
    }

    @Test
    fun lifecycleHistoryValidatesHashesStatesOrderAndDuplicateRevisions() = runBlocking {
        val event = """{"revision":"$revision","event_hash":"${"c".repeat(64)}","content_hash":"sha256:${"b".repeat(64)}","from_status":"Draft","to_status":"In Review","local_timestamp":"1"}"""
        var data = "[$event]"
        val capabilities = DesktopDocumentCapabilities("qms", DocumentExecutor { _, args, _ ->
            ProcessResult(0, response(args[1], data))
        })
        assertEquals(DocumentStatus.InReview, capabilities.documentLifecycleHistory("project", id).getOrThrow().single().to)
        for (invalid in listOf("[$event,$event]", "[$event]".replace("In Review", "Unknown"), "[$event]".replace("\"Draft\"", "\"Effective\""), "[$event]".replace(revision, "invalid"), "{}")) {
            data = invalid
            assertTrue(capabilities.documentLifecycleHistory("project", id).isFailure, invalid)
        }
        data = "[]"
        assertTrue(capabilities.documentLifecycleHistory("project", id).getOrThrow().isEmpty())
    }
}
