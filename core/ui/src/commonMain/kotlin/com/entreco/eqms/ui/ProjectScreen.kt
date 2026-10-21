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

data class ProjectDirectory(val name: String, val path: String)

data class DirectoryListing(
    val path: String,
    val parent: String?,
    val children: List<ProjectDirectory>,
)

interface ProjectCapabilities {
    suspend fun listDirectory(path: String?): Result<DirectoryListing>
    suspend fun createFolder(parent: String, name: String): Result<String>
    suspend fun openProject(path: String, create: Boolean): Result<String>
}

@Composable
fun ProjectScreen(
    capabilities: ProjectCapabilities,
    documentCapabilities: DocumentCapabilities? = null,
    closeRequested: Boolean = false,
    onCloseHandled: () -> Unit = {},
    onCloseConfirmed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf<Boolean?>(null) }
    var documentDirty by remember { mutableStateOf(false) }
    var documentBusy by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun navigate(action: () -> Unit) {
        if (documentDirty) pendingNavigation = action else action()
    }

    LaunchedEffect(closeRequested, busy, documentBusy) {
        if (closeRequested && !busy && !documentBusy) {
            navigate(onCloseConfirmed)
            onCloseHandled()
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("eQMS", style = MaterialTheme.typography.h4)
                Text("Create a project or open an existing verified workspace.")
                project?.let { Text("Current project: $it") }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy && !documentBusy, onClick = { navigate { creating = true } }) { Text("Create project") }
                    Button(enabled = !busy && !documentBusy, onClick = { navigate { creating = false } }) { Text("Open project") }
                }
                if (busy) Text("Working...")
                message?.let {
                    Text(it, color = if (error) MaterialTheme.colors.error else MaterialTheme.colors.onSurface)
                }
                if (creating == null && !busy) {
                    project?.let { path ->
                        documentCapabilities?.let { documents ->
                            key(path) {
                                DocumentScreen(
                                    path, documents, requestNavigation = { navigate(it) },
                                    onStateChanged = { dirty, working -> documentDirty = dirty; documentBusy = working },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            pendingNavigation?.let { action ->
                AlertDialog(
                    onDismissRequest = { pendingNavigation = null },
                    title = { Text("Unsaved changes") },
                    text = { Text("Discard your unsaved document edits?") },
                    confirmButton = {
                        TextButton(onClick = { pendingNavigation = null; documentDirty = false; action() }) { Text("Discard changes") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingNavigation = null }) { Text("Keep editing") }
                    },
                )
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
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                capabilities.openProject(path, create).fold(
                                    onSuccess = {
                                        project = it
                                        error = false
                                        message = if (create) "Project created successfully." else "Project verified and opened."
                                    },
                                    onFailure = {
                                        error = true
                                        message = it.message ?: "Workspace operation failed."
                                    },
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
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

    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(if (create) "Create project" else "Open project") },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Directory path") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && path.isNotBlank(), onClick = {
                        scope.launch { navigate(path) }
                    }) { Text("Go") }
                    Button(enabled = !busy && listing?.parent != null, onClick = {
                        val parent = listing?.parent ?: return@Button
                        scope.launch { navigate(parent) }
                    }) { Text("Up") }
                    if (create) {
                        Button(enabled = !busy && listing != null && path == listing?.path, onClick = {
                            folderName = ""
                            error = null
                            newFolder = true
                        }) { Text("New folder") }
                    }
                }
                Text(listing?.path ?: "Choose a directory")
                Column(Modifier.height(220.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    listing?.children?.forEach { child ->
                        Text(
                            child.name,
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                scope.launch { navigate(child.path) }
                            }.padding(8.dp),
                        )
                    }
                }
                if (busy) Text("Working...")
                if (!newFolder) error?.let { Text(it, color = MaterialTheme.colors.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && listing != null && path == listing?.path && !newFolder,
                onClick = { listing?.let { onSelect(it.path) } },
            ) { Text("Select directory") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text("Cancel") } },
    )

    if (newFolder) {
        AlertDialog(
            onDismissRequest = { if (!busy) newFolder = false },
            title = { Text("New folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Folder name") },
                        enabled = !busy,
                        singleLine = true,
                    )
                    error?.let { Text(it, color = MaterialTheme.colors.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && folderName.isNotBlank(), onClick = {
                    val parent = listing?.path ?: return@TextButton
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
                TextButton(enabled = !busy, onClick = { newFolder = false; error = null }) { Text("Cancel") }
            },
        )
    }
}
