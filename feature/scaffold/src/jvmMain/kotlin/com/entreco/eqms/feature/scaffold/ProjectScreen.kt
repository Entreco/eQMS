package com.entreco.eqms.feature.scaffold

import com.entreco.eqms.feature.document.DocumentCapabilities
import com.entreco.eqms.feature.document.DocumentScreen
import com.entreco.eqms.feature.document.DocumentNavigationController
import com.entreco.eqms.feature.ui.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.component.Text

typealias ProjectScreenLayout =
    @Composable (title: @Composable () -> Unit, actions: @Composable () -> Unit, content: @Composable () -> Unit) -> Unit

@Composable
fun ProjectScreen(
    capabilities: ProjectCapabilities,
    documentCapabilities: DocumentCapabilities? = null,
    closeRequested: Boolean = false,
    onCloseHandled: () -> Unit = {},
    onCloseConfirmed: () -> Unit = {},
    chrome: DesktopChrome,
    headerActionsModifier: Modifier = Modifier,
    layout: ProjectScreenLayout? = null,
) {
    val screenLayout: ProjectScreenLayout = layout ?: { title, actions, content ->
        Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { title() }
                actions()
            }
            Divider(Orientation.Horizontal)
            Box(Modifier.weight(1f)) { content() }
        }
    }
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<String?>(null) }
    var projectGeneration by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf<Boolean?>(null) }
    var identityFor by remember { mutableStateOf<String?>(null) }
    var documentDirty by remember { mutableStateOf(false) }
    var documentBusy by remember { mutableStateOf(false) }
    val documentNavigation = remember { DocumentNavigationController() }
    val dark = chrome.dark
    var appearanceError by remember {
        mutableStateOf(chrome.initialAppearance.exceptionOrNull()?.let { "Could not load appearance: ${it.message}" })
    }
    var savingAppearance by remember { mutableStateOf(false) }
    var chromeDialog by remember { mutableStateOf<DesktopCommand?>(null) }
    var aboutVisible by remember { mutableStateOf(false) }
    var aboutRequest by remember { mutableIntStateOf(0) }
    var branding by remember { mutableStateOf<BrandingPreview?>(null) }
    val brand = branding?.tokens
    var brandError by remember { mutableStateOf<String?>(null) }
    val interactive = !busy && !documentBusy && creating == null && identityFor == null &&
        chromeDialog == null && !savingAppearance

    fun changeAppearance(value: Boolean) {
        if (savingAppearance) return
        savingAppearance = true
        scope.launch {
            try {
                chrome.saveAppearance(value).fold(
                    onSuccess = { chrome.dark = value; appearanceError = null },
                    onFailure = { appearanceError = "Could not save appearance: ${it.message}" },
                )
            } finally {
                savingAppearance = false
            }
        }
    }

    fun navigate(action: () -> Unit) {
        documentNavigation.request(action)
    }

    fun persistProjects(current: String?, recents: List<String>) {
        scope.launch {
            chrome.saveProjects(current, recents).onFailure {
                error = true
                message = "Could not save project preference: ${it.message ?: "storage failure"}"
            }
        }
    }

    fun openProject(path: String, create: Boolean, restoring: Boolean = false, branding: BrandingDraft? = null) {
        busy = true
        message = null
        scope.launch {
            try {
                val outcome = if (create && branding != null) {
                    capabilities.createProject(path, branding)
                } else {
                    capabilities.openProject(path, create)
                }
                outcome.fold(
                    onSuccess = { opened ->
                        navigate {
                            project = opened
                            projectGeneration++
                            error = false
                            message = when {
                                create -> "Project created successfully."
                                restoring -> "Project restored and verified."
                                else -> "Project verified and opened."
                            }
                            val recents = (listOf(opened) + chrome.recents).distinct().take(MAX_RECENT_PROJECTS)
                            chrome.recents = recents
                            persistProjects(opened, recents)
                        }
                    },
                    onFailure = {
                        error = true
                        val reason = it.message ?: "Workspace operation failed."
                        message = if (restoring) "Could not reopen the last project: $reason" else reason
                    },
                )
            } finally {
                busy = false
            }
        }
    }

    SideEffect {
        chrome.enabled = interactive
        chrome.hasProject = project != null
    }

    LaunchedEffect(Unit) {
        chrome.initialProject.fold(
            onSuccess = { stored -> if (stored != null) openProject(stored, create = false, restoring = true) },
            onFailure = {
                error = true
                message = "Could not load the last project: ${it.message ?: "invalid stored value"}"
            },
        )
        chrome.initialRecents.exceptionOrNull()?.let {
            if (message == null) {
                error = true
                message = "Could not load recent projects: ${it.message ?: "invalid stored value"}"
            }
        }
    }

    LaunchedEffect(chrome.command) {
        val command = chrome.consume()
        if (command != null && interactive) {
            when (command) {
                DesktopCommand.About -> {
                    aboutVisible = true
                    aboutRequest++
                }
                DesktopCommand.NewProject -> { creating = true }
                DesktopCommand.OpenProject -> { creating = false }
                DesktopCommand.OpenRecent -> chrome.recentPath?.let { openProject(it, create = false) }
                DesktopCommand.CloseProject -> if (project != null) navigate {
                    project = null
                    projectGeneration++
                    documentDirty = false
                    documentBusy = false
                    error = false
                    message = null
                    persistProjects(null, chrome.recents)
                }
                else -> chromeDialog = command
            }
        }
    }

    LaunchedEffect(project, projectGeneration) {
        branding = null
        brandError = null
        project?.let { opened ->
            capabilities.projectBranding(opened).fold(
                onSuccess = { branding = it },
                onFailure = { brandError = "Project branding unavailable: ${it.message ?: "unknown error"}" },
            )
        }
    }

    LaunchedEffect(closeRequested, interactive) {
        if (closeRequested && interactive) {
            navigate(onCloseConfirmed)
            onCloseHandled()
        }
    }

    IntUiTheme(isDark = dark) {
        BrandTheme(brand) {
        if (aboutVisible) {
            AboutWindow(chrome, dark, aboutRequest, onClose = { aboutVisible = false })
        }
        screenLayout(
            {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("DutchDoxx", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val openPath = project
                    val shown = branding
                    if (openPath != null && shown != null) {
                        Divider(Orientation.Vertical, Modifier.height(20.dp))
                        LogoPreview(shown, storedLogoPath(openPath, shown), size = 28.dp)
                        Divider(Orientation.Vertical, Modifier.height(20.dp))
                    }
                    Text(
                        shown?.tokens?.name?.takeIf { openPath != null }
                            ?: project?.substringAfterLast('/')?.substringAfterLast('\\') ?: "Quality workspace",
                        Modifier.weight(1f),
                        color = JewelTheme.globalColors.text.info,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            {
                Row(headerActionsModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(enabled = interactive, onClick = { chromeDialog = DesktopCommand.Settings }) {
                        Icon(AllIconsKeys.General.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                    }
                }
            },
            {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                    Column {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            val path = project
                            val documents = documentCapabilities
                            if (path != null && documents != null) {
                                key(path, projectGeneration) {
                                    DocumentScreen(
                                        path, documents,
                                        onStateChanged = { dirty, working -> documentDirty = dirty; documentBusy = working },
                                        modifier = Modifier.fillMaxSize(),
                                        workspaceBusy = busy || creating != null || identityFor != null || chromeDialog != null || savingAppearance,
                                        navigation = documentNavigation,
                                    )
                                }
                            } else {
                                Column(
                                    Modifier.fillMaxSize().padding(48.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                                ) {
                                    Text("Your quality workspace", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Use File > New project or File > Open project to get started.")
                                    Text(
                                        "Write Markdown drafts, search saved documents, and inspect working history.",
                                        color = JewelTheme.globalColors.text.info,
                                    )
                                    Text("Drafts are not approvals.", color = JewelTheme.globalColors.text.info)
                                }
                            }
                        }
                        Divider(Orientation.Horizontal)
                        brandError?.let {
                            Text(it, Modifier.padding(horizontal = 16.dp), color = JewelTheme.globalColors.text.error)
                        }
                        brand?.warnings.orEmpty().forEach {
                            Text("Branding warning: $it", Modifier.padding(horizontal = 16.dp))
                        }
                        appearanceError?.let {
                            Text(it, Modifier.padding(horizontal = 16.dp), color = JewelTheme.globalColors.text.error)
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                if (busy) "Working..." else message ?: "",
                                Modifier.weight(1f),
                                color = if (error) JewelTheme.globalColors.text.error else JewelTheme.globalColors.text.normal,
                            )
                            Text(
                                if (documentBusy) "Document operation in progress" else if (documentDirty) "Draft has unsaved edits" else "Local workspace",
                                color = JewelTheme.globalColors.text.info,
                            )
                            ProductAttribution()
                        }
                        chromeDialog?.let { command ->
                            key(command) {
                                if (command == DesktopCommand.Settings) ProjectSettingsDialog(
                                    project, capabilities, branding, dark, savingAppearance, appearanceError,
                                    onAppearance = ::changeAppearance,
                                    onBrandingSaved = { branding = it },
                                    onDismiss = { chromeDialog = null },
                                ) else ChromeDialog(
                                    command, chrome, dark, savingAppearance, appearanceError,
                                    onAppearance = ::changeAppearance,
                                    onDismiss = { chromeDialog = null },
                                )
                            }
                        }
                    }
                    creating?.let { create ->
                        DirectoryDialog(
                            capabilities = capabilities,
                            create = create,
                            onCancel = {
                                creating = null
                                error = false
                                message = "Project selection cancelled."
                            },
                            onSelect = { path ->
                                creating = null
                                if (create) identityFor = path else openProject(path, create)
                            },
                        )
                    }
                    identityFor?.let { path ->
                        BrandingDialog(
                            capabilities = capabilities,
                            folderName = path.substringAfterLast('/').substringAfterLast('\\'),
                            onCancel = {
                                identityFor = null
                                error = false
                                message = "Project selection cancelled."
                            },
                            onCreate = { draft ->
                                identityFor = null
                                openProject(path, create = true, branding = draft)
                            },
                        )
                    }
                }
            },
        )
        }
    }
}

@Composable
private fun DirectoryDialog(
    capabilities: ProjectCapabilities,
    create: Boolean,
    onCancel: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var path by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }

    suspend fun navigate(destination: String?) {
        busy = true
        error = null
        listing = null
        if (destination != null) path = destination
        try {
            capabilities.listDirectory(destination).fold(
                onSuccess = { listing = it; path = it.path },
                onFailure = { error = it.message ?: "Could not read directory." },
            )
        } finally {
            busy = false
        }
    }

    LaunchedEffect(capabilities) { navigate(null) }

    WorkspaceDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(if (create) "Create project" else "Open project") },
        windowTitle = if (create) "Create project" else "Open project",
        size = DpSize(680.dp, 580.dp),
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = "Directory path",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && path.isNotBlank(), onClick = {
                        scope.launch { navigate(path) }
                    }) { Text("Go") }
                    OutlinedButton(enabled = !busy && listing?.parent != null, onClick = {
                        val parent = listing?.parent ?: return@OutlinedButton
                        scope.launch { navigate(parent) }
                    }) { Text("Up") }
                    if (create) {
                        OutlinedButton(enabled = !busy && listing != null && path == listing?.path, onClick = {
                            folderName = ""
                            error = null
                            newFolder = true
                        }) { Text("New folder") }
                    }
                }
                Text(listing?.path ?: "Choose a directory")
                Column(Modifier.height(220.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    listing?.children?.forEach { child ->
                        WorkspaceItem(
                            child.name,
                            enabled = !busy,
                            onClick = {
                                scope.launch { navigate(child.path) }
                            },
                        )
                    }
                }
                if (busy) Text("Working...")
                if (!newFolder) error?.let { Text(it, color = JewelTheme.globalColors.text.error) }
            }
        },
        confirmButton = {
            DefaultButton(
                enabled = !busy && listing != null && path == listing?.path && !newFolder,
                onClick = { listing?.let { onSelect(it.path) } },
            ) { Text("Select directory") }
        },
        dismissButton = { OutlinedButton(enabled = !busy, onClick = onCancel) { Text("Cancel") } },
    )

    if (newFolder) {
        WorkspaceDialog(
            onDismissRequest = { if (!busy) newFolder = false },
            title = { Text("New folder") },
            windowTitle = "New folder",
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkspaceTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = "Folder name",
                        enabled = !busy,
                        singleLine = true,
                    )
                    error?.let { Text(it, color = JewelTheme.globalColors.text.error) }
                }
            },
            confirmButton = {
                DefaultButton(enabled = !busy && folderName.isNotBlank(), onClick = {
                    val parent = listing?.path ?: return@DefaultButton
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            capabilities.createFolder(parent, folderName).fold(
                                onSuccess = {
                                    newFolder = false
                                    navigate(it)
                                },
                                onFailure = { error = it.message ?: "Could not create folder." },
                            )
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Create folder") }
            },
            dismissButton = {
                OutlinedButton(enabled = !busy, onClick = { newFolder = false; error = null }) { Text("Cancel") }
            },
        )
    }
}
