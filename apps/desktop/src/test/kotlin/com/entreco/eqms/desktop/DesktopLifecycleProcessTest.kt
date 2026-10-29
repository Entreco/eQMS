package com.entreco.eqms.desktop

import com.entreco.eqms.feature.document.DocumentStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopLifecycleProcessTest {
    @Test
    fun nativeLifecyclePersistsOnReopenWithoutChangingBaselineContentOrIndex() = runBlocking {
        val executable = System.getenv("EQMS_QMS_PATH")?.takeIf { it.isNotBlank() }
            ?: Path.of("../../target/release/qms").toAbsolutePath().normalize().toString()
        val root = Files.createTempDirectory("eqms-lifecycle-test-")
        try {
            fun git(vararg args: String): String {
                val result = executeProcess("git", listOf("-C", root.toString()) + args)
                assertEquals(0, result.exitCode, result.output)
                return result.output
            }
            git("init", "--quiet")
            git("config", "user.name", "Lifecycle Test")
            git("config", "user.email", "test@example.invalid")
            DesktopProjectCapabilities(WorkspaceCli(executable)).openProject(root.toString(), true).getOrThrow()
            Files.writeString(root.resolve("unrelated"), "do not commit")
            git("add", "unrelated")
            val baseline = git("rev-parse", "HEAD")
            val index = Files.readAllBytes(root.resolve(".git/index"))
            val journal = Files.readAllBytes(root.resolve(".eqms/journal.jsonl"))
            val documents = DesktopDocumentCapabilities(executable)
            val created = documents.createDocument(root.toString(), "Policy", "first").getOrThrow()
            val saved = documents.saveDocument(root.toString(), created.id, created.revision, created.title, "saved body\n").getOrThrow()
            var current = saved
            for (status in listOf(DocumentStatus.InReview, DocumentStatus.Approved, DocumentStatus.Effective)) {
                current = documents.transitionDocument(root.toString(), current, status).getOrThrow()
                assertEquals(status, current.status)
                assertEquals(saved.contentHash, current.contentHash)
                assertEquals(saved.body, current.body)
            }
            assertTrue(documents.transitionDocument(root.toString(), saved, DocumentStatus.InReview).isFailure)
            assertTrue(documents.saveDocument(root.toString(), current.id, current.revision, "Changed", "overwrite").isFailure)
            val reopened = DesktopDocumentCapabilities(executable)
            assertEquals(current, reopened.showLatestDocument(root.toString(), current.id).getOrThrow())
            assertEquals(3, reopened.documentLifecycleHistory(root.toString(), current.id).getOrThrow().size)
            assertEquals(5, reopened.documentHistory(root.toString(), current.id).getOrThrow().size)
            assertEquals("", reopened.documentDiff(root.toString(), current.id, saved.revision, current.revision).getOrThrow())
            reopened.prepareSearch(root.toString()).getOrThrow()
            val search = reopened.searchDocuments(root.toString(), "Policy", null, 0, 0, 0).getOrThrow()
            assertTrue(search.documents.hits.any { it.id == current.id })
            for (terminal in listOf(DocumentStatus.Obsolete, DocumentStatus.Superseded)) {
                val draft = reopened.createDocument(root.toString(), terminal.label, "content").getOrThrow()
                var record = draft
                for (target in listOf(DocumentStatus.InReview, DocumentStatus.Approved, DocumentStatus.Effective, terminal)) {
                    record = reopened.transitionDocument(root.toString(), record, target).getOrThrow()
                }
                assertEquals(terminal, reopened.showLatestDocument(root.toString(), record.id).getOrThrow().status)
                assertTrue(reopened.transitionDocument(root.toString(), record, DocumentStatus.Draft).isFailure)
            }
            val audit = executeProcess(executable, listOf("audit", "verify-ledger", "--json", root.toString()))
            assertEquals(0, audit.exitCode, audit.output)
            assertEquals(baseline, git("rev-parse", "HEAD"))
            assertContentEquals(index, Files.readAllBytes(root.resolve(".git/index")))
            assertContentEquals(journal, Files.readAllBytes(root.resolve(".eqms/journal.jsonl")))
            assertEquals("do not commit", Files.readString(root.resolve("unrelated")))
            assertTrue(git("status", "--porcelain").contains("A  unrelated"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
