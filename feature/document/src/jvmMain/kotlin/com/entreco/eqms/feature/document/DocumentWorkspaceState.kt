package com.entreco.eqms.feature.document

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import com.entreco.eqms.feature.search.DocumentSearch
import kotlinx.coroutines.CoroutineScope

class DocumentNavigationController {
    internal var handler: ((() -> Unit) -> Unit)? = null
    fun request(action: () -> Unit) { handler?.invoke(action) ?: action() }
}
internal class DocumentTab(
    val key: Int,
    snapshot: DocumentSnapshot?,
    val historical: Boolean = false,
) {
    var snapshot by mutableStateOf(snapshot)
    val titleField = mutableStateOf(TextFieldValue(snapshot?.title.orEmpty()))
    val bodyField = mutableStateOf(TextFieldValue(snapshot?.body.orEmpty()))
    var title: String
        get() = titleField.value.text
        set(value) { titleField.value = TextFieldValue(value) }
    var body: String
        get() = bodyField.value.text
        set(value) { bodyField.value = TextFieldValue(value) }
    val dirty get() = !historical && (title != snapshot?.title.orEmpty() || body != snapshot?.body.orEmpty())
    val editable get() = !historical && (snapshot == null || snapshot?.status == DocumentStatus.Draft)
    val scroll = ScrollState(0)
    var viewMode by mutableStateOf(DocumentViewMode.Preview)
    val previewScroll = androidx.compose.foundation.lazy.LazyListState()
    var splitFraction by mutableFloatStateOf(0.5f)
    var rendered by mutableStateOf<RenderedDocument?>(null)
    var renderedTitle = ""
    var renderedBody = ""
    var renderedRevision: String? = null
    var renderRequestedAt = System.nanoTime()
    var reportedRendering: RenderedDocument? = null
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var history by mutableStateOf<List<DocumentSnapshot>>(emptyList())
    var events by mutableStateOf<List<DocumentLifecycleEvent>>(emptyList())
    var difference by mutableStateOf<String?>(null)
    var older by mutableStateOf<DocumentSnapshot?>(null)
    var newer by mutableStateOf<DocumentSnapshot?>(null)
}

internal class DocumentWorkspaceState(
    val project: String,
    private val capabilities: DocumentCapabilities,
    scope: CoroutineScope,
) {
    val search = DocumentSearch(project, capabilities, scope)
    val tabs = mutableStateListOf<DocumentTab>()
    var activeKey by mutableStateOf<Int?>(null)
    val active get() = tabs.firstOrNull { it.key == activeKey }
    var documents by mutableStateOf<List<DocumentSnapshot>>(emptyList())
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var pendingClose by mutableStateOf<List<DocumentTab>?>(null)
        private set
    private var afterClose: (() -> Unit)? = null
    private var nextKey = 0
    private var disposed = false
    val dirty get() = tabs.any { it.dirty }

    fun dispose() { disposed = true; search.dispose() }

    fun newDocument() {
        if (busy) return
        val tab = DocumentTab(++nextKey, null)
        tabs += tab
        activeKey = tab.key
    }

    fun openSnapshot(document: DocumentSnapshot, historical: Boolean = false): DocumentTab {
        val existing = tabs.firstOrNull {
            it.snapshot?.id == document.id && it.historical == historical &&
                (!historical || it.snapshot?.revision == document.revision)
        }
        val tab = existing ?: DocumentTab(++nextKey, document, historical).also { tabs += it }
        activeKey = tab.key
        return tab
    }

    suspend fun open(id: String, revision: String? = null) {
        if (busy) return
        val existing = tabs.firstOrNull {
            it.snapshot?.id == id && it.historical == (revision != null) &&
                (revision == null || it.snapshot?.revision == revision)
        }
        if (existing != null) { activeKey = existing.key; return }
        val selectedBefore = activeKey
        operation {
            val result = if (revision == null) capabilities.showLatestDocument(project, id)
                else capabilities.showDocument(project, id, revision)
            if (!disposed) result.fold(
                onSuccess = {
                    val currentSelection = activeKey
                    openSnapshot(it, revision != null)
                    if (currentSelection != selectedBefore) activeKey = currentSelection
                },
                onFailure = { error = it.message ?: "Could not open document." },
            )
        }
    }

    suspend fun refresh() {
        operation {
            capabilities.listDocuments(project).fold(
                onSuccess = { updated ->
                    if (!disposed) {
                        documents = updated
                        tabs.filter { !it.historical }.forEach { tab ->
                            updated.firstOrNull { it.id == tab.snapshot?.id }?.let {
                                if (!tab.dirty) applySnapshot(tab, it)
                                else if (it.revision != tab.snapshot?.revision) {
                                    tab.error = "Saved document changed. Your edits are retained; reconcile them before reloading."
                                }
                            }
                        }
                    }
                },
                onFailure = { if (!disposed) error = it.message ?: "Could not list documents." },
            )
            if (!disposed) search.refresh()
        }
    }

    suspend fun save(tab: DocumentTab): Boolean {
        if (busy || disposed || tab !in tabs) return false
        if (!tab.editable || tab.title.isBlank()) {
            tab.error = if (tab.title.isBlank()) "Document title is required." else "This document is read-only."
            activeKey = tab.key
            return false
        }
        var saved = false
        operation {
            tab.error = null
            val before = tab.snapshot
            val result = if (before == null) capabilities.createDocument(project, tab.title, tab.body)
                else capabilities.saveDocument(project, before.id, before.revision, tab.title, tab.body)
            if (!disposed && tab in tabs) result.fold(
                onSuccess = {
                    applySnapshot(tab, it)
                    tab.message = if (before?.revision == it.revision) "No changes to save." else "Draft saved."
                    updateList(it)
                    search.refresh()
                    saved = true
                },
                onFailure = { tab.error = it.message ?: "Save failed. Your edits are still here."; activeKey = tab.key },
            )
        }
        return saved
    }

    suspend fun transition(tab: DocumentTab, target: DocumentStatus) {
        val previous = tab.snapshot ?: return
        if (busy || tab.dirty || tab.historical || tab !in tabs) return
        operation {
            tab.error = null
            capabilities.transitionDocument(project, previous, target).fold(
                onSuccess = {
                    if (!disposed && tab in tabs) {
                        applySnapshot(tab, it)
                        updateList(it)
                        tab.message = "Document is now ${it.status.label}."
                        search.refresh()
                    }
                },
                onFailure = { if (!disposed) tab.error = it.message ?: "Lifecycle action failed." },
            )
        }
    }

    suspend fun loadHistory(tab: DocumentTab) {
        val document = tab.snapshot ?: return
        if (busy) return
        operation {
            tab.error = null
            capabilities.documentHistory(project, document.id).fold(
                onSuccess = { revisions ->
                    if (!disposed && tab in tabs) {
                        tab.history = revisions
                        tab.older = revisions.lastOrNull()
                        tab.newer = revisions.firstOrNull()
                        tab.difference = null
                    }
                },
                onFailure = { if (!disposed) tab.error = it.message ?: "Could not load history." },
            )
            capabilities.documentLifecycleHistory(project, document.id).fold(
                onSuccess = { if (!disposed && tab in tabs) tab.events = it },
                onFailure = { if (!disposed) tab.error = it.message ?: "Could not load lifecycle history." },
            )
        }
    }

    suspend fun compare(tab: DocumentTab) {
        val from = tab.older ?: return
        val to = tab.newer ?: return
        if (busy) return
        operation {
            tab.error = null
            tab.difference = null
            capabilities.documentDiff(project, to.id, from.revision, to.revision).fold(
                onSuccess = { if (!disposed && tab in tabs) tab.difference = it.ifEmpty { "No differences." } },
                onFailure = { if (!disposed) tab.error = it.message ?: "Could not compare revisions." },
            )
        }

    }

    suspend fun reload(tab: DocumentTab) {
        val document = tab.snapshot ?: return
        if (busy) return
        operation {
            capabilities.showLatestDocument(project, document.id).fold(
                onSuccess = { if (!disposed && tab in tabs) {
                    applySnapshot(tab, it)
                    updateList(it)
                } },
                onFailure = { if (!disposed) tab.error = it.message ?: "Could not reload document." },
            )
        }
    }

    fun requestClose(tab: DocumentTab) = requestNavigation(listOf(tab)) {
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        if (activeKey == tab.key) activeKey = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.key
    }

    fun requestNavigation(action: () -> Unit) = requestNavigation(tabs.toList(), action)

    fun requestTabNavigation(tab: DocumentTab, action: () -> Unit) = requestNavigation(listOf(tab), action)

    private fun requestNavigation(affected: List<DocumentTab>, action: () -> Unit) {
        if (busy || pendingClose != null) return
        val dirty = affected.filter { it.dirty }
        if (dirty.isEmpty()) action() else { pendingClose = dirty; afterClose = action }
    }

    fun cancelClose() { pendingClose = null; afterClose = null }

    fun discardAndContinue() {
        val action = afterClose
        cancelClose()
        action?.invoke()
    }

    suspend fun saveAndContinue() {
        val pending = pendingClose ?: return
        for (tab in pending) {
            if (!save(tab)) { cancelClose(); return }
        }
        discardAndContinue()
    }

    private fun applySnapshot(tab: DocumentTab, document: DocumentSnapshot) {
        tab.snapshot = document
        tab.error = null
        tab.message = null
        if (tab.title != document.title) tab.title = document.title
        if (tab.body != document.body) tab.body = document.body
        tab.history = emptyList()
        tab.events = emptyList()
        tab.difference = null
    }

    private fun updateList(document: DocumentSnapshot) {
        documents = (documents.filterNot { it.id == document.id } + document).sortedBy { it.id }
    }

    private suspend fun operation(action: suspend () -> Unit) {
        if (busy || disposed) return
        busy = true
        error = null
        try { action() } finally { busy = false }
    }
}
