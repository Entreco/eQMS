package com.entreco.eqms.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class DocumentSnapshot(
    val id: String,
    val title: String,
    val body: String,
    val revision: String,
    val contentHash: String,
    val eventHash: String,
    val configuredAuthor: String,
    val localTimestamp: String,
)

interface DocumentCapabilities : SearchCapabilities {
    suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>>
    suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun documentHistory(project: String, id: String): Result<List<DocumentSnapshot>>
    suspend fun documentDiff(project: String, id: String, from: String, to: String): Result<String>
    suspend fun showLatestDocument(project: String, id: String): Result<DocumentSnapshot> =
        documentHistory(project, id).mapCatching { history ->
            history.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Saved draft was not found.")
        }
    suspend fun showDocument(project: String, id: String, revision: String): Result<DocumentSnapshot> =
        documentHistory(project, id).mapCatching { history ->
            history.firstOrNull { it.id == id && it.revision == revision }
                ?: throw IllegalArgumentException("Saved revision was not found.")
        }
}

@Composable
fun DocumentScreen(
    project: String,
    capabilities: DocumentCapabilities,
    requestNavigation: (() -> Unit) -> Unit,
    onStateChanged: (dirty: Boolean, busy: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSearchResultsLaidOut: (SearchRenderTiming) -> Unit = {},
) {
    key(project, capabilities) {
        DocumentWorkspace(project, capabilities, requestNavigation, onStateChanged, modifier, onSearchResultsLaidOut)
    }
}

@Composable
private fun DocumentWorkspace(
    project: String,
    capabilities: DocumentCapabilities,
    requestNavigation: (() -> Unit) -> Unit,
    onStateChanged: (Boolean, Boolean) -> Unit,
    modifier: Modifier,
    onSearchResultsLaidOut: (SearchRenderTiming) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val search = remember { DocumentSearch(project, capabilities, scope) }
    val timingCallback by rememberUpdatedState(onSearchResultsLaidOut)
    val measured = remember(search.results) { booleanArrayOf(false) }
    var documents by remember { mutableStateOf(emptyList<DocumentSnapshot>()) }
    var selected by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<DocumentSnapshot>?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var historical by remember { mutableStateOf(false) }
    val dirty = editing && !historical && (title != (selected?.title ?: "") || body != (selected?.body ?: ""))
    SideEffect { onStateChanged(dirty, busy) }
    DisposableEffect(Unit) { onDispose { search.dispose(); onStateChanged(false, false) } }

    fun select(document: DocumentSnapshot?) {
        selected = document
        title = document?.title ?: ""
        body = document?.body ?: ""
        editing = true
        historical = false
        savedMessage = null
        error = null
    }

    suspend fun refresh() {
        capabilities.listDocuments(project).fold(
            onSuccess = { documents = it },
            onFailure = { error = it.message ?: "Could not list documents." },
        )
    }

    LaunchedEffect(project, capabilities) {
        search.refresh()
        busy = true
        try { refresh() } finally { busy = false }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy, onClick = { requestNavigation { select(null) } }) { Text("New document") }
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try { refresh(); search.refresh() } finally { busy = false }
                }
            }) { Text("Refresh documents") }
        }
        if (busy) Text("Loading documents...")
        error?.let { Text(it, color = MaterialTheme.colors.error) }
        OutlinedTextField(
            value = search.query, onValueChange = search::updateQuery,
            label = { Text("Search saved drafts") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (search.preparing) Text("Preparing saved-draft search...")
        if (search.loading && !search.preparing) Text("Searching saved drafts...")
        search.error?.let {
            Text(it, color = MaterialTheme.colors.error)
            TextButton(onClick = search::refresh) { Text("Refresh search") }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.width(200.dp).fillMaxHeight().onGloballyPositioned {
                if (search.results != null && !measured[0]) {
                    measured[0] = true
                    timingCallback(search.renderTiming())
                }
            }) {
                if (search.query.isBlank()) {
                    item { Text("Documents", style = MaterialTheme.typography.h6) }
                    if (documents.isEmpty() && !busy) item { Text("No saved drafts yet.") }
                    items(documents, key = { it.id }) { document ->
                        Text(
                            "${document.title} (Draft)",
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                requestNavigation { select(document) }
                            }.padding(vertical = 10.dp),
                        )
                    }
                } else {
                    search.results?.let { results ->
                        if (results.historical) item { Text("Draft/history matches — older saved revisions, read-only.") }
                        if (SearchGroup.entries.all { results.page(it).hits.isEmpty() }) item { Text("No matching saved drafts.") }
                        SearchGroup.entries.forEach { group ->
                            item(key = group) { Text(group.name, style = MaterialTheme.typography.h6) }
                            items(results.page(group).hits, key = { "${group.name}:${it.id}:${it.revision}" }) { hit ->
                                val summary = when (group) {
                                    SearchGroup.Documents -> hit.title
                                    SearchGroup.Authors -> "${hit.title} | by ${hit.configuredAuthor}"
                                    SearchGroup.Content -> "${hit.title} | ${hit.excerpt}"
                                }
                                Text(
                                    if (results.historical) "${hit.title} (Draft/history)\n" +
                                        "Revision: ${hit.revision}\nConfigured author: ${hit.configuredAuthor}\n" +
                                        "Local timestamp: ${hit.localTimestamp}\n${hit.excerpt}" else summary,
                                    Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                        requestNavigation {
                                            scope.launch {
                                                busy = true
                                                error = null
                                                try {
                                                    val result = if (results.historical) {
                                                        capabilities.showDocument(project, hit.id, hit.revision)
                                                    } else {
                                                        capabilities.showLatestDocument(project, hit.id)
                                                    }
                                                    result.fold(
                                                        onSuccess = { select(it); historical = results.historical },
                                                        onFailure = { error = it.message ?: "Could not open saved draft." },
                                                    )
                                                } finally { busy = false }
                                            }
                                        }
                                    }.padding(vertical = 10.dp),
                                )
                            }
                            if (results.page(group).nextOffset != null) {
                                item(key = "more:$group") {
                                    TextButton(enabled = !search.loading, onClick = { search.more(group) }) {
                                        Text("Show more ${group.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (editing) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (historical) "Draft/history — read-only saved revision" else if (dirty) "Draft - unsaved changes" else "Draft")
                    selected?.let { Text(it.id, style = MaterialTheme.typography.caption) }
                    if (historical) {
                        Text("Saved working revision, not an approval. Author is configured identity; timestamp is a local claim.")
                        selected?.let {
                            Text("Revision: ${it.revision}")
                            Text("Configured author: ${it.configuredAuthor}")
                            Text("Local timestamp: ${it.localTimestamp}")
                        }
                        TextButton(enabled = !busy, onClick = {
                            val id = selected?.id ?: return@TextButton
                            scope.launch {
                                busy = true
                                try {
                                    capabilities.showLatestDocument(project, id).fold(
                                        onSuccess = { select(it) },
                                        onFailure = { error = it.message ?: "Could not read latest draft." },
                                    )
                                } finally { busy = false }
                            }
                        }) { Text("Return to latest draft") }
                    }
                    OutlinedTextField(
                        value = title, onValueChange = { title = it; savedMessage = null },
                        label = { Text("Document title") }, singleLine = true,
                        enabled = !busy, readOnly = historical, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body, onValueChange = { body = it; savedMessage = null },
                        label = { Text("Markdown body") }, enabled = !busy, readOnly = historical,
                        modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 100.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = !busy && !historical && title.isNotBlank(), onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    val previous = selected
                                    val result = if (previous == null) {
                                        capabilities.createDocument(project, title, body)
                                    } else {
                                        capabilities.saveDocument(project, previous.id, previous.revision, title, body)
                                    }
                                    result.fold(
                                        onSuccess = {
                                            select(it)
                                            savedMessage = if (previous?.revision == it.revision) "No changes to save." else "Draft saved."
                                            documents = (documents.filterNot { old -> old.id == it.id } + it).sortedBy { doc -> doc.id }
                                            search.refresh()
                                        },
                                        onFailure = { error = it.message ?: "Save failed. Your edits are still here." },
                                    )
                                } finally { busy = false }
                            }
                        }) { Text("Save") }
                        TextButton(enabled = !busy && selected != null, onClick = {
                            val document = selected ?: return@TextButton
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    capabilities.documentHistory(project, document.id).fold(
                                        onSuccess = { history = it },
                                        onFailure = { error = it.message ?: "Could not load history." },
                                    )
                                } finally { busy = false }
                            }
                        }) { Text("History") }
                    }
                    savedMessage?.let { Text(it) }
                }
            } else {
                Text("Select a document or create a new draft.")
            }
        }
    }
    history?.let { revisions ->
        HistoryDialog(project, capabilities, revisions, onDismiss = { history = null }, onBusy = { busy = it })
    }
}

@Composable
private fun HistoryDialog(
    project: String,
    capabilities: DocumentCapabilities,
    revisions: List<DocumentSnapshot>,
    onDismiss: () -> Unit,
    onBusy: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var older by remember { mutableStateOf(revisions.lastOrNull()) }
    var newer by remember { mutableStateOf(revisions.firstOrNull()) }
    var difference by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Draft history") },
        text = {
            Column(Modifier.width(680.dp).heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved working revisions, not approvals. Author is configured identity; timestamps are local claims.")
                RevisionPicker("Older revision", revisions, older, !busy) { older = it; difference = null }
                RevisionPicker("Newer revision", revisions, newer, !busy) { newer = it; difference = null }
                newer?.let {
                    Text("Configured author: ${it.configuredAuthor}")
                    Text("Local timestamp (Unix seconds): ${it.localTimestamp}")
                    Text("Content: ${it.contentHash}", style = MaterialTheme.typography.caption)
                    Text("Event: ${it.eventHash}", style = MaterialTheme.typography.caption)
                    OutlinedTextField(value = it.body, onValueChange = {}, readOnly = true, label = { Text("Saved Markdown (read-only)") })
                }
                Button(enabled = !busy && older != null && newer != null, onClick = {
                    val from = older ?: return@Button
                    val to = newer ?: return@Button
                    scope.launch {
                        busy = true
                        onBusy(true)
                        error = null
                        try {
                            capabilities.documentDiff(project, to.id, from.revision, to.revision).fold(
                                onSuccess = { difference = it.ifEmpty { "No differences." } },
                                onFailure = { error = it.message ?: "Could not compare revisions." },
                            )
                        } finally { busy = false; onBusy(false) }
                    }
                }) { Text("Compare") }
                difference?.let { Text(it) }
                error?.let { Text(it, color = MaterialTheme.colors.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Close history") } },
    )
}

@Composable
private fun RevisionPicker(
    label: String,
    revisions: List<DocumentSnapshot>,
    selected: DocumentSnapshot?,
    enabled: Boolean,
    onSelect: (DocumentSnapshot) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }) {
            Text("$label: ${selected?.revision?.take(12) ?: "None"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            revisions.forEachIndexed { index, revision ->
                DropdownMenuItem(onClick = { onSelect(revision); expanded = false }) {
                    Text("Revision ${revisions.size - index}: ${revision.revision.take(12)} - ${revision.title}")
                }
            }
        }
    }
}
