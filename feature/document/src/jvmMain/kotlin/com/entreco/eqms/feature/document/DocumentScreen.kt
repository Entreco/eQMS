package com.entreco.eqms.feature.document

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.search.SearchPanel
import com.entreco.eqms.feature.search.SearchRenderTiming
import com.entreco.eqms.feature.ui.*
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DocumentScreen(
    project: String,
    capabilities: DocumentCapabilities,
    onStateChanged: (dirty: Boolean, busy: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSearchResultsLaidOut: (SearchRenderTiming) -> Unit = {},
    workspaceBusy: Boolean = false,
    navigation: DocumentNavigationController? = null,
    onDocumentRendered: (DocumentRenderTiming) -> Unit = {},
) {
    key(project, capabilities) {
        val scope = rememberCoroutineScope()
        val state = remember { DocumentWorkspaceState(project, capabilities, scope) }
        val editorStates = rememberSaveableStateHolder()
        val previousKeys = remember { mutableSetOf<Int>() }
        val openKeys = state.tabs.map { it.key }.toSet()
        SideEffect {
            (previousKeys - openKeys).forEach(editorStates::removeState)
            previousKeys.clear()
            previousKeys.addAll(openKeys)
        }
        var left by remember { mutableStateOf<WorkspaceTool?>(WorkspaceTool.Project) }
        var right by remember { mutableStateOf<WorkspaceTool?>(WorkspaceTool.Review) }
        var leftWidth by remember { mutableFloatStateOf(260f) }
        var rightWidth by remember { mutableFloatStateOf(300f) }
        var confirmation by remember { mutableStateOf<Pair<DocumentTab, DocumentStatus>?>(null) }
        var externalLink by remember { mutableStateOf<String?>(null) }
        var linkBusy by remember { mutableStateOf(false) }
        val bitmapCache = remember { DocumentBitmapCache() }
        val tab = state.active
        val interactive =
            !state.busy && !workspaceBusy && state.pendingClose == null && confirmation == null && externalLink == null && !linkBusy
        val isMac = remember { System.getProperty("os.name").startsWith("Mac") }
        val shortcuts = remember(isMac) { ToolShortcutHandler(isMac) }
        val railFocus = remember { WorkspaceTool.entries.associateWith { FocusRequester() } }
        var leftFocused by remember { mutableStateOf(false) }
        var rightFocused by remember { mutableStateOf(false) }
        val toggle: (WorkspaceTool) -> Unit = { tool ->
            if (interactive) {
                if (tool.isLeft) {
                    if (leftFocused) railFocus.getValue(tool).requestFocus()
                    left = if (left == tool) null else tool
                } else {
                    if (rightFocused) railFocus.getValue(tool).requestFocus()
                    right = if (right == tool) null else tool
                }
            }
        }
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused) { if (!windowFocused) shortcuts.reset() }
        val dirty = state.dirty
        val working =
            state.busy || state.pendingClose != null || confirmation != null || externalLink != null || linkBusy
        SideEffect { onStateChanged(dirty, working) }
        DisposableEffect(state, navigation) {
            navigation?.handler = { state.requestNavigation(it) }
            onDispose {
                navigation?.handler = null
                state.dispose()
                onStateChanged(false, false)
            }
        }
        LaunchedEffect(state) { state.refresh() }
        LaunchedEffect(right, tab?.key, tab?.snapshot?.revision) {
            if (right == WorkspaceTool.History && tab != null) state.loadHistory(tab)
        }

        Column(
            modifier.fillMaxSize()
            .onFocusChanged { if (!it.hasFocus) shortcuts.reset() }
            .focusGroup()
            .onPreviewKeyEvent { shortcuts.handle(it, interactive, toggle) }) {
            state.error?.let { Text(it, Modifier.padding(8.dp), color = JewelTheme.globalColors.text.error) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val panelLimit = (maxWidth.value * 0.27f).coerceAtLeast(140f)
                Row(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                    ToolRail(WorkspaceTool.entries.filter { it.isLeft }, left, interactive, isMac, railFocus, toggle)
                    if (left != null) {
                        ToolPanel(
                            left!!.name, leftWidth.coerceIn(140f, panelLimit),
                            modifier = Modifier.onFocusChanged { leftFocused = it.hasFocus }) {
                            when (left) {
                                WorkspaceTool.Project -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DefaultButton(
                                            enabled = interactive,
                                            onClick = state::newDocument
                                        ) { Text("New document") }
                                    }
                                    OutlinedButton(
                                        enabled = interactive && !state.documentsLoading,
                                        onClick = { scope.launch { state.refresh() } },
                                    ) { Text("Refresh documents") }
                                    LazyColumn(Modifier.weight(1f)) {
                                        if (state.documentsLoading) item { Text("Loading documents...") }
                                        state.documentsError?.let { message ->
                                            item {
                                                Text(
                                                    "Could not refresh documents: $message",
                                                    color = JewelTheme.globalColors.text.error
                                                )
                                            }
                                        }
                                        if (state.documents.isEmpty() && !state.documentsLoading && state.documentsError == null) {
                                            item { Text("No saved documents yet.") }
                                        }
                                        items(state.documents, key = { it.id }) { document ->
                                            WorkspaceItem(
                                                "${document.title} (${document.status.label})",
                                                selected = tab?.snapshot?.id == document.id,
                                                enabled = interactive && !state.documentsLoading,
                                                onClick = { scope.launch { state.open(document.id) } })
                                        }
                                    }
                                }

                                WorkspaceTool.Search -> SearchPanel(
                                    state.search, interactive,
                                    onSelect = { hit, historical ->
                                        scope.launch { state.open(hit.id, if (historical) hit.revision else null) }
                                    },
                                    isSelected = { hit, historical ->
                                        tab?.snapshot?.id == hit.id && tab.historical == historical &&
                                                (!historical || tab.snapshot?.revision == hit.revision)
                                    },
                                    onResultsLaidOut = onSearchResultsLaidOut,
                                    modifier = Modifier.weight(1f),
                                ) {}

                                else -> Unit
                            }
                        }
                        PanelDivider("Resize left panel", interactive) { delta ->
                            leftWidth = (leftWidth + delta).coerceIn(140f, panelLimit)
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().workspacePanel()
                            .semantics { contentDescription = "Document editor panel" }) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.tabs.forEach { open ->
                                key(open.key) {
                                    WorkspaceEditorTab(
                                        title = open.title.ifBlank { "Untitled" },
                                        description = "Document tab ${open.title.ifBlank { "Untitled" }}${if (open.historical) " historical ${open.snapshot?.revision}" else ""}",
                                        closeDescription = "Close tab ${open.title.ifBlank { "Untitled" }}${if (open.historical) " historical ${open.snapshot?.revision}" else ""}",
                                        selected = open.key == state.activeKey,
                                        enabled = !workspaceBusy && state.pendingClose == null && confirmation == null,
                                        closeEnabled = interactive,
                                        onSelect = { state.activeKey = open.key },
                                        onClose = { state.requestClose(open) },
                                        dirty = open.dirty, historical = open.historical,
                                    )
                                }
                            }
                        }
                        if (tab == null) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Select a document or create a new draft.")
                            }
                        } else editorStates.SaveableStateProvider(tab.key) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(tab.snapshot?.status?.label ?: "New draft", fontWeight = FontWeight.SemiBold)
                                if (tab.dirty) Text("Draft - unsaved changes")
                                if (tab.historical) Text("Saved revision - read-only")
                                tab.error?.let { Text(it, color = JewelTheme.globalColors.text.error) }
                                tab.message?.let { Text(it) }
                                DocumentPresentation(
                                    project,
                                    tab,
                                    capabilities,
                                    interactive,
                                    bitmapCache,
                                    onExternalLink = { if (interactive) externalLink = it },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    onRendered = onDocumentRendered
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DefaultButton(
                                        enabled = interactive && tab.editable && tab.title.isNotBlank(),
                                        onClick = { scope.launch { state.save(tab) } }) { Text("Save") }
                                    OutlinedButton(
                                        enabled = interactive && tab.snapshot != null,
                                        onClick = {
                                            right = WorkspaceTool.History; scope.launch { state.loadHistory(tab) }
                                        }) { Text("Show history") }
                                    OutlinedButton(enabled = interactive && tab.snapshot != null, onClick = {
                                        val source = tab.snapshot ?: return@OutlinedButton
                                        if (tab.historical) scope.launch { state.open(source.id) }
                                        else state.requestTabNavigation(tab) { scope.launch { state.reload(tab) } }
                                    }) { Text(if (tab.historical) "Return to latest document" else "Reload saved document") }
                                }
                                if (!tab.editable) Text("Read-only. Rework is not available in this version.")
                                Text(
                                    "Configured identity and local timestamps are claims, not authenticated identity or trusted time.",
                                    color = JewelTheme.globalColors.text.info
                                )
                            }
                        }
                    }
                    if (right != null) {
                        PanelDivider("Resize right panel", interactive) { delta ->
                            rightWidth = (rightWidth - delta).coerceIn(140f, panelLimit)
                        }
                        ToolPanel(
                            right!!.name, rightWidth.coerceIn(140f, panelLimit),
                            modifier = Modifier.onFocusChanged { rightFocused = it.hasFocus }) {
                            if (tab == null) Text("Select a document.") else when (right) {
                                WorkspaceTool.Review -> ReviewPanel(tab, interactive) { confirmation = tab to it }
                                WorkspaceTool.History -> DocumentHistoryPanel(
                                    tab, interactive,
                                    onRefresh = { scope.launch { state.loadHistory(tab) } },
                                    onCompare = { scope.launch { state.compare(tab) } },
                                    onOpen = { state.openSnapshot(it, historical = true) })

                                else -> Unit
                            }
                        }
                    }
                    ToolRail(WorkspaceTool.entries.filter { !it.isLeft }, right, interactive, isMac, railFocus, toggle)
                }
            }
        }
        externalLink?.let { target ->
            WorkspaceDialog(
                onDismissRequest = { if (!linkBusy) externalLink = null },
                title = { Text("Open external link?") },
                text = { Text("This opens your default application outside DutchDoxx:\n$target") },
                confirmButton = {
                    DefaultButton(enabled = !linkBusy, onClick = {
                        linkBusy = true
                        scope.launch {
                            try {
                                capabilities.openDocumentLink(target).onFailure {
                                    tab?.error = it.message ?: "Could not open external link."
                                }
                                externalLink = null
                            } finally {
                                linkBusy = false
                            }
                        }
                    }) { Text("Open link") }
                },
                dismissButton = {
                    OutlinedButton(
                        enabled = !linkBusy,
                        onClick = { externalLink = null }) { Text("Cancel") }
                },
            )
        }
        confirmation?.let { (source, target) ->
            WorkspaceDialog(
                onDismissRequest = { confirmation = null },
                title = { Text(actionLabel(target)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${source.title}: ${source.snapshot?.status?.label} -> ${target.label}")
                        Text("Revision: ${source.snapshot?.revision}")
                        Text("This records a local lifecycle action, not a cryptographic signature or proof of reviewer authority.")
                        if (target == DocumentStatus.InReview) Text("Content becomes read-only. Return to draft/rework is not available.")
                        if (target == DocumentStatus.Effective) Text("Internal Effective state only. No baseline merge, release record or external distribution.")
                    }
                },
                confirmButton = {
                    DefaultButton(onClick = {
                        confirmation = null
                        scope.launch { state.transition(source, target) }
                    }) { Text("Confirm") }
                },
                dismissButton = { OutlinedButton(onClick = { confirmation = null }) { Text("Cancel") } },
            )
        }
        state.pendingClose?.let { affected ->
            WorkspaceDialog(
                onDismissRequest = { if (!state.busy) state.cancelClose() },
                title = { Text("Unsaved changes") },
                text = { Text("Save changes to ${affected.joinToString { it.title.ifBlank { "Untitled" } }}?") },
                confirmButton = {
                    DefaultButton(enabled = !state.busy, onClick = { scope.launch { state.saveAndContinue() } }) {
                        Text(if (affected.size > 1) "Save all" else "Save")
                    }
                },
                dismissButton = {
                    OutlinedButton(enabled = !state.busy, onClick = state::cancelClose) { Text("Cancel") }
                    OutlinedButton(enabled = !state.busy, onClick = state::discardAndContinue) {
                        Text(if (affected.size > 1) "Discard all" else "Discard changes")
                    }
                },
            )
        }
    }
}

@Composable
private fun ToolRail(
    tools: List<WorkspaceTool>, selected: WorkspaceTool?, enabled: Boolean, isMac: Boolean,
    focus: Map<WorkspaceTool, FocusRequester>, select: (WorkspaceTool) -> Unit,
) {
    Column(Modifier.width(44.dp).fillMaxHeight().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.forEach { tool ->
            WorkspaceToolButton(
                tool, selected == tool, enabled, isMac, { select(tool) },
                Modifier.focusRequester(focus.getValue(tool))
            )
        }
    }
}

@Composable
private fun ToolPanel(
    title: String, width: Float, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.focusGroup().width(width.dp).fillMaxHeight().workspacePanel()
            .semantics { contentDescription = "$title panel" }.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun PanelDivider(label: String, enabled: Boolean, resize: (Float) -> Unit) {
    val density = LocalDensity.current.density
    Box(
        Modifier.width(8.dp).fillMaxHeight()
        .pointerHoverIcon(if (enabled) PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR)) else PointerIcon.Default)
        .semantics { contentDescription = label }
        .draggable(rememberDraggableState { resize(it / density) }, Orientation.Horizontal, enabled = enabled)
    )
}

@Composable
private fun Modifier.workspacePanel(): Modifier =
    clip(RoundedCornerShape(10.dp))
        .background(workspaceSurfaceColor())
        .border(1.dp, JewelTheme.globalColors.borders.normal.copy(alpha = 0.4f), RoundedCornerShape(10.dp))

internal fun actionLabel(target: DocumentStatus): String = when (target) {
    DocumentStatus.InReview -> "Submit for review"
    DocumentStatus.Approved -> "Approve"
    DocumentStatus.Effective -> "Publish"
    DocumentStatus.Obsolete -> "Mark obsolete"
    DocumentStatus.Superseded -> "Mark superseded"
    DocumentStatus.Draft -> "Draft"
}

@Composable
private fun ReviewPanel(tab: DocumentTab, enabled: Boolean, transition: (DocumentStatus) -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val document = tab.snapshot
        Text(document?.status?.label ?: "Save this draft before submitting it.", fontWeight = FontWeight.SemiBold)
        document?.let {
            Text(it.id)
            Text("Revision: ${it.revision}")
            Text("Content: ${it.contentHash}")
            Text("Event: ${it.eventHash}")
            Text("Configured author: ${it.configuredAuthor}")
            Text("Local timestamp: ${it.localTimestamp}")
        }
        val targets = when (document?.status) {
            DocumentStatus.Draft -> listOf(DocumentStatus.InReview)
            DocumentStatus.InReview -> listOf(DocumentStatus.Approved)
            DocumentStatus.Approved -> listOf(DocumentStatus.Effective)
            DocumentStatus.Effective -> listOf(DocumentStatus.Obsolete, DocumentStatus.Superseded)
            else -> emptyList()
        }
        if (tab.dirty) Text("Save your changes before a lifecycle action.")
        if (tab.historical) Text("Historical revision - lifecycle actions unavailable.")
        targets.forEach { target ->
            DefaultButton(enabled = enabled && !tab.dirty && !tab.historical, onClick = { transition(target) }) {
                Text(actionLabel(target))
            }
        }
        Text("Approval is not a signature or authenticated reviewer authority. Publish means internal Effective only, not a release or external distribution.")
    }
}

@Composable
private fun DocumentHistoryPanel(
    tab: DocumentTab, enabled: Boolean, onRefresh: () -> Unit, onCompare: () -> Unit,
    onOpen: (DocumentSnapshot) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            enabled = enabled && tab.snapshot != null && !tab.historyLoading,
            onClick = onRefresh,
        ) { Text("Refresh history") }
        Text("Saved revisions and local lifecycle events. Configured identity/time are not trusted attestations.")
        if (tab.historyLoading) Text("Loading verified history...")
        tab.historyError?.let { Text(it, color = JewelTheme.globalColors.text.error) }
        if (tab.historyLoaded) {
            if (tab.history.isEmpty()) {
                Text("No saved revisions are available.")
            } else {
                RevisionPicker("Older revision", tab.history, tab.older, enabled && !tab.historyLoading) {
                    tab.older = it; tab.difference = null
                }
                RevisionPicker("Newer revision", tab.history, tab.newer, enabled && !tab.historyLoading) {
                    tab.newer = it; tab.difference = null
                }
                DefaultButton(
                    enabled = enabled && !tab.historyLoading && tab.older != null && tab.newer != null,
                    onClick = onCompare,
                ) { Text("Compare") }
                tab.newer?.let { selected ->
                    OutlinedButton(enabled = enabled && !tab.historyLoading, onClick = { onOpen(selected) }) {
                        Text("Open saved revision")
                    }
                }
            }
            if (tab.events.isEmpty()) Text("No lifecycle events recorded.")
            tab.events.forEach {
                Text("${it.from.label} -> ${it.to.label}\nRevision: ${it.revision}\nLocal timestamp: ${it.localTimestamp}")
            }
        }
        tab.difference?.let { Text(it) }
    }
}

@Composable
private fun RevisionPicker(
    label: String, revisions: List<DocumentSnapshot>, selected: DocumentSnapshot?, enabled: Boolean,
    onSelect: (DocumentSnapshot) -> Unit,
) {
    Text(label)
    ListComboBox(
        items = revisions.map { "${it.status.label}: ${it.revision.take(12)} - ${it.title}" },
        selectedIndex = revisions.indexOf(selected),
        onSelectedItemChange = { onSelect(revisions[it]) }, enabled = enabled,
    )
}
