package com.entreco.eqms.feature.document

import com.entreco.eqms.feature.search.*
import com.entreco.eqms.feature.ui.*

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DocumentScreen(
    project: String,
    capabilities: DocumentCapabilities,
    requestNavigation: (() -> Unit) -> Unit,
    onStateChanged: (dirty: Boolean, busy: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSearchResultsLaidOut: (SearchRenderTiming) -> Unit = {},
    workspaceBusy: Boolean = false,
) {
    key(project, capabilities) {
        DocumentWorkspace(project, capabilities, requestNavigation, onStateChanged, modifier, onSearchResultsLaidOut, workspaceBusy)
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
    workspaceBusy: Boolean,
) {
    val scope = rememberCoroutineScope()
    val search = remember { DocumentSearch(project, capabilities, scope) }
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
    val interactive = !busy && !workspaceBusy && history == null
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

    Column(modifier.fillMaxWidth()) {
        error?.let { Text(it, Modifier.padding(12.dp), color = JewelTheme.globalColors.text.error) }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.width(300.dp).fillMaxHeight().border(1.dp, JewelTheme.globalColors.borders.normal).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Project", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultButton(enabled = interactive, onClick = { requestNavigation { select(null) } }) { Text("New document") }
                    OutlinedButton(enabled = interactive, onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try { refresh(); search.refresh() } finally { busy = false }
                        }
                    }) { Text("Refresh documents") }
                }
                if (busy) Text("Loading documents...")
                SearchPanel(
                    search = search, enabled = interactive,
                    onSelect = { hit, fromHistory ->
                        requestNavigation {
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    val result = if (fromHistory) {
                                        capabilities.showDocument(project, hit.id, hit.revision)
                                    } else {
                                        capabilities.showLatestDocument(project, hit.id)
                                    }
                                    result.fold(
                                        onSuccess = { select(it); historical = fromHistory },
                                        onFailure = { error = it.message ?: "Could not open saved draft." },
                                    )
                                } finally { busy = false }
                            }
                        }
                    },
                    isSelected = { hit, fromHistory ->
                        selected?.id == hit.id && (!fromHistory || selected?.revision == hit.revision)
                    },
                    onResultsLaidOut = onSearchResultsLaidOut,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    item { Text("Documents", Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.SemiBold) }
                    if (documents.isEmpty() && !busy) item { Text("No saved drafts yet.") }
                    items(documents, key = { it.id }) { document ->
                        WorkspaceItem(
                            "${document.title} (Draft)",
                            selected = selected?.id == document.id, enabled = interactive,
                            onClick = { requestNavigation { select(document) } },
                        )
                    }
                }
            }
            if (editing) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().border(1.dp, JewelTheme.globalColors.borders.normal).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (dirty) "* " else "") + (selected?.title ?: "Untitled"),
                            Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("Markdown", color = JewelTheme.globalColors.text.info)
                    }
                    Text(if (historical) "Draft/history — read-only saved revision" else if (dirty) "Draft - unsaved changes" else "Draft")
                    selected?.let { Text(it.id, color = JewelTheme.globalColors.text.info) }
                    if (historical) {
                        Text("Saved working revision, not an approval. Author is configured identity; timestamp is a local claim.")
                        selected?.let {
                            Text("Revision: ${it.revision}")
                            Text("Configured author: ${it.configuredAuthor}")
                            Text("Local timestamp: ${it.localTimestamp}")
                        }
                        OutlinedButton(enabled = interactive, onClick = {
                            val id = selected?.id ?: return@OutlinedButton
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
                    WorkspaceTextField(
                        value = title, onValueChange = { title = it; savedMessage = null },
                        label = "Document title", singleLine = true,
                        enabled = interactive, readOnly = historical, modifier = Modifier.fillMaxWidth(),
                    )
                    WorkspaceTextField(
                        value = body, onValueChange = { body = it; savedMessage = null },
                        label = "Markdown body", enabled = interactive, readOnly = historical,
                        modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 100.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DefaultButton(enabled = interactive && !historical && title.isNotBlank(), onClick = {
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
                        OutlinedButton(enabled = interactive && selected != null, onClick = {
                            val document = selected ?: return@OutlinedButton
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
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("Select a document or create a new draft.", color = JewelTheme.globalColors.text.info)
                }
            }
        }
        history?.let { revisions ->
            HistoryPanel(project, capabilities, revisions, onDismiss = { history = null }, onBusy = { busy = it })
        }
    }
}

@Composable
private fun HistoryPanel(
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
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).border(1.dp, JewelTheme.globalColors.borders.normal).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Draft history", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            DefaultButton(enabled = !busy && older != null && newer != null, onClick = {
                val from = older ?: return@DefaultButton
                val to = newer ?: return@DefaultButton
                scope.launch {
                    busy = true
                    onBusy(true)
                    error = null
                    difference = null
                    try {
                        capabilities.documentDiff(project, to.id, from.revision, to.revision).fold(
                            onSuccess = { difference = it.ifEmpty { "No differences." } },
                            onFailure = { error = it.message ?: "Could not compare revisions." },
                        )
                    } finally { busy = false; onBusy(false) }
                }
            }) { Text("Compare") }
            OutlinedButton(enabled = !busy, onClick = onDismiss) { Text("Close history") }
        }
        error?.let { Text(it, color = JewelTheme.globalColors.text.error) }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Saved working revisions, not approvals. Author is configured identity; timestamps are local claims.")
            RevisionPicker("Older revision", revisions, older, !busy) { older = it; difference = null }
            RevisionPicker("Newer revision", revisions, newer, !busy) { newer = it; difference = null }
            difference?.let { Text(it, fontFamily = FontFamily.Monospace) }
            newer?.let {
                Text("Configured author: ${it.configuredAuthor}")
                Text("Local timestamp (Unix seconds): ${it.localTimestamp}")
                Text("Content: ${it.contentHash}", color = JewelTheme.globalColors.text.info)
                Text("Event: ${it.eventHash}", color = JewelTheme.globalColors.text.info)
                WorkspaceTextField(value = it.body, onValueChange = {}, readOnly = true, label = "Saved Markdown (read-only)")
            }
        }
    }
}

@Composable
private fun RevisionPicker(
    label: String,
    revisions: List<DocumentSnapshot>,
    selected: DocumentSnapshot?,
    enabled: Boolean,
    onSelect: (DocumentSnapshot) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        ListComboBox(
            items = revisions.mapIndexed { index, revision ->
                "Revision ${revisions.size - index}: ${revision.revision.take(12)} - ${revision.title}"
            },
            selectedIndex = revisions.indexOf(selected),
            onSelectedItemChange = { onSelect(revisions[it]) },
            enabled = enabled && revisions.isNotEmpty(),
        )
    }
}
