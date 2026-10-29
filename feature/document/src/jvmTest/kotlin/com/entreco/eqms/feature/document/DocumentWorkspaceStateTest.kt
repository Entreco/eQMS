package com.entreco.eqms.feature.document

import kotlinx.coroutines.*
import kotlin.test.*
import org.junit.Test

class DocumentWorkspaceStateTest {
    private class Capabilities : DocumentCapabilities {
        val first = DocumentSnapshot("one", "Policy", "original", "r1", "hash", "event", "Author", "1")
        val second = first.copy(id = "two", title = "Procedure")
        val saved = mutableMapOf(first.id to first, second.id to second)
        var failId: String? = null
        var failTransition = false
        var transitionCalls = 0
        var pending: CompletableDeferred<Result<DocumentSnapshot>>? = null
        override suspend fun listDocuments(project: String) = Result.success(saved.values.toList())
        override suspend fun createDocument(project: String, title: String, body: String) =
            Result.success(first.copy(id = "new", title = title, body = body))
        override suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot> {
            if (id == failId || saved.getValue(id).revision != expected) return Result.failure(IllegalStateException("Conflict"))
            return Result.success(saved.getValue(id).copy(title = title, body = body, revision = "$expected-saved").also { saved[id] = it })
        }
        override suspend fun showLatestDocument(project: String, id: String) = pending?.await() ?: Result.success(saved.getValue(id))
        override suspend fun documentHistory(project: String, id: String) = Result.success(listOf(saved.getValue(id)))
        override suspend fun documentDiff(project: String, id: String, from: String, to: String) = Result.success("")
        override suspend fun documentLifecycleHistory(project: String, id: String) = Result.success(emptyList<DocumentLifecycleEvent>())
        override suspend fun transitionDocument(project: String, document: DocumentSnapshot, target: DocumentStatus): Result<DocumentSnapshot> {
            transitionCalls++
            if (failTransition) return Result.failure(IllegalStateException("Stale transition"))
            return Result.success(document.copy(status = target, revision = "${document.revision}-transition").also { saved[it.id] = it })
        }
    }

    @Test
    fun independentBuffersDeduplicateLatestAndKeepHistorySeparate() = runBlocking {
        val capabilities = Capabilities()
        val state = DocumentWorkspaceState("project", capabilities, this)
        try {
            val first = state.openSnapshot(capabilities.first)
            first.body = "unsaved first"
            val second = state.openSnapshot(capabilities.second)
            second.title = "unsaved second"
            assertSame(first, state.openSnapshot(capabilities.first))
            val historical = state.openSnapshot(capabilities.first, historical = true)
            assertFalse(historical.editable)
            assertFalse(historical.dirty)
            assertEquals(3, state.tabs.size)
            assertEquals("unsaved first", first.body)
            assertEquals("unsaved second", second.title)
            assertTrue(state.save(second))
            assertTrue(first.dirty)
            assertFalse(second.dirty)
            assertEquals("original", historical.body)
            state.newDocument()
            val new = state.active!!
            state.newDocument()
            assertNotEquals(new.key, state.active!!.key)
        } finally { state.dispose() }
    }

    @Test
    fun closeCancelDiscardAndPartialSaveAllPreserveFailedEdits() = runBlocking {
        val capabilities = Capabilities()
        val state = DocumentWorkspaceState("project", capabilities, this)
        try {
            val first = state.openSnapshot(capabilities.first).also { it.body = "first edit" }
            val second = state.openSnapshot(capabilities.second).also { it.body = "second edit" }
            var navigated = false
            state.requestNavigation { navigated = true }
            assertEquals(2, state.pendingClose!!.size)
            state.cancelClose()
            assertFalse(navigated)
            assertEquals("first edit", first.body)
            capabilities.failId = second.snapshot!!.id
            state.requestNavigation { navigated = true }
            state.saveAndContinue()
            assertFalse(navigated)
            assertFalse(first.dirty)
            assertTrue(second.dirty)
            assertEquals("Conflict", second.error)
            assertSame(second, state.active)
            state.requestClose(second)
            state.discardAndContinue()
            assertFalse(second in state.tabs)
            assertTrue(first in state.tabs)
        } finally { state.dispose() }
    }

    @Test
    fun invalidTitleBlocksSaveAndDirtyTransitionCannotRun() = runBlocking {
        val capabilities = Capabilities()
        val state = DocumentWorkspaceState("project", capabilities, this)
        try {
            state.newDocument()
            val tab = state.active!!
            tab.body = "cannot lose this"
            var closed = false
            state.requestNavigation { closed = true }
            state.saveAndContinue()
            assertFalse(closed)
            assertEquals("Document title is required.", tab.error)
            assertEquals("cannot lose this", tab.body)
            val saved = state.openSnapshot(capabilities.first)
            saved.body = "dirty"
            state.transition(saved, DocumentStatus.InReview)
            assertEquals(0, capabilities.transitionCalls)
        } finally { state.dispose() }
    }

    @Test
    fun lifecycleChangesReadOnlyStateAndFailureRetainsObservedSnapshot() = runBlocking {
        val capabilities = Capabilities()
        val state = DocumentWorkspaceState("project", capabilities, this)
        try {
            val tab = state.openSnapshot(capabilities.first)
            capabilities.failTransition = true
            state.transition(tab, DocumentStatus.InReview)
            assertEquals(DocumentStatus.Draft, tab.snapshot!!.status)
            assertEquals("Stale transition", tab.error)
            capabilities.failTransition = false
            for (status in listOf(DocumentStatus.InReview, DocumentStatus.Approved, DocumentStatus.Effective)) {
                state.transition(tab, status)
                assertEquals(status, tab.snapshot!!.status)
                assertFalse(tab.editable)
                assertEquals("original", tab.body)
            }
            assertFalse(state.save(tab))
            state.loadHistory(tab)
            state.compare(tab)
            assertEquals("No differences.", tab.difference)
        } finally { state.dispose() }
    }

    @Test
    fun lateOpenDoesNotStealSelectionAndDisposedResultsAreIgnored() = runBlocking {
        val capabilities = Capabilities()
        val state = DocumentWorkspaceState("project", capabilities, this)
        try {
            val first = state.openSnapshot(capabilities.first)
            val second = state.openSnapshot(capabilities.second)
            state.activeKey = first.key
            capabilities.pending = CompletableDeferred()
            val opening = launch(start = CoroutineStart.UNDISPATCHED) { state.open("third") }
            state.activeKey = second.key
            capabilities.pending!!.complete(Result.success(capabilities.first.copy(id = "third")))
            opening.join()
            assertSame(second, state.active)
            assertEquals(3, state.tabs.size)
            capabilities.pending = CompletableDeferred()
            val obsolete = launch(start = CoroutineStart.UNDISPATCHED) { state.open("fourth") }
            state.dispose()
            capabilities.pending!!.complete(Result.success(capabilities.first.copy(id = "fourth")))
            obsolete.join()
            assertEquals(3, state.tabs.size)
        } finally { state.dispose() }
    }

    @Test
    fun refreshDoesNotOverwriteDirtyContentAndReloadRequiresExplicitGuard() = runBlocking {
        val capabilities = Capabilities()
        val state = DocumentWorkspaceState("project", capabilities, this)
        try {
            val tab = state.openSnapshot(capabilities.first).also { it.body = "my edit" }
            capabilities.saved[capabilities.first.id] = capabilities.first.copy(body = "external", revision = "r2", status = DocumentStatus.InReview)
            state.refresh()
            assertEquals("my edit", tab.body)
            assertEquals("r1", tab.snapshot!!.revision)
            state.requestTabNavigation(tab) {}
            state.cancelClose()
            assertEquals("my edit", tab.body)
            state.requestTabNavigation(tab) {}
            state.discardAndContinue()
            state.reload(tab)
            assertEquals("external", tab.body)
            assertEquals(DocumentStatus.InReview, tab.snapshot!!.status)
        } finally { state.dispose() }
    }
}
