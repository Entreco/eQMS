package com.entreco.eqms.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

interface DocumentCapabilities {
    suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>>
    suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun documentHistory(project: String, id: String): Result<List<DocumentSnapshot>>
    suspend fun documentDiff(project: String, id: String, from: String, to: String): Result<String>
}

@Composable
fun DocumentScreen(
    project: String,
    capabilities: DocumentCapabilities,
    requestNavigation: (() -> Unit) -> Unit,
    onStateChanged: (dirty: Boolean, busy: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf(emptyList<DocumentSnapshot>()) }
    var selected by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<DocumentSnapshot>?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val dirty = editing && (title != (selected?.title ?: "") || body != (selected?.body ?: ""))
    SideEffect { onStateChanged(dirty, busy) }
    DisposableEffect(Unit) { onDispose { onStateChanged(false, false) } }

    fun select(document: DocumentSnapshot?) {
        selected = document
        title = document?.title ?: ""
        body = document?.body ?: ""
        editing = true
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
                    try { refresh() } finally { busy = false }
                }
            }) { Text("Refresh documents") }
        }
        if (busy) Text("Loading documents...")
        error?.let { Text(it, color = MaterialTheme.colors.error) }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(200.dp).verticalScroll(rememberScrollState())) {
                Text("Documents", style = MaterialTheme.typography.h6)
                if (documents.isEmpty() && !busy) Text("No saved drafts yet.")
                documents.forEach { document ->
                    Text(
                        "${document.title} (Draft)",
                        Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            requestNavigation { select(document) }
                        }.padding(vertical = 10.dp),
                    )
                }
            }
            if (editing) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (dirty) "Draft - unsaved changes" else "Draft")
                    selected?.let { Text(it.id, style = MaterialTheme.typography.caption) }
                    OutlinedTextField(
                        value = title, onValueChange = { title = it; savedMessage = null },
                        label = { Text("Document title") }, singleLine = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body, onValueChange = { body = it; savedMessage = null },
                        label = { Text("Markdown body") }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 100.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = !busy && title.isNotBlank(), onClick = {
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
