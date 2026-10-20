package com.entreco.eqms.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.IOException
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val JSON_SCHEMA_VERSION = 1

internal enum class WorkspaceOperation(val command: String, val label: String) {
    Create("init", "Create project"),
    Open("verify", "Open project"),
}

internal sealed interface WorkspaceCommandResult {
    data class Success(val path: String) : WorkspaceCommandResult
    data class Failure(val message: String) : WorkspaceCommandResult
}

internal data class ProcessResult(val exitCode: Int, val output: String)

internal fun interface ProcessExecutor {
    @Throws(IOException::class, InterruptedException::class)
    fun execute(executable: String, arguments: List<String>): ProcessResult
}

internal class WorkspaceCli(
    private val executable: String = System.getenv("EQMS_QMS_PATH")?.takeIf(String::isNotBlank) ?: "qms",
    private val processExecutor: ProcessExecutor = ProcessExecutor { command, arguments ->
        executeProcess(command, arguments)
    },
) {
    fun execute(operation: WorkspaceOperation, path: Path): WorkspaceCommandResult {
        val result = try {
            processExecutor.execute(
                executable,
                listOf(operation.command, "--json", path.toString()),
            )
        } catch (error: IOException) {
            return WorkspaceCommandResult.Failure(
                "Could not start qms. Set EQMS_QMS_PATH to the native executable: ${error.message ?: "I/O error"}",
            )
        } catch (error: SecurityException) {
            return WorkspaceCommandResult.Failure(
                "The qms executable could not be started: ${error.message ?: "permission denied"}",
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            return WorkspaceCommandResult.Failure("The qms operation was interrupted.")
        }

        val response = parseResponse(result.output)
        if (result.exitCode != 0) {
            return WorkspaceCommandResult.Failure(
                response?.errorMessage()
                    ?: "qms ${operation.command} failed with exit code ${result.exitCode}.",
            )
        }
        if (response == null) {
            return WorkspaceCommandResult.Failure("qms returned an invalid JSON response.")
        }
        if (response.intValue("schema_version") != JSON_SCHEMA_VERSION) {
            return WorkspaceCommandResult.Failure("qms returned an unsupported JSON schema version.")
        }
        if (response.stringValue("status") != "ok") {
            return WorkspaceCommandResult.Failure("qms returned an unsuccessful response.")
        }
        if (response.stringValue("command") != operation.command) {
            return WorkspaceCommandResult.Failure("qms returned a response for an unexpected command.")
        }
        val projectPath = response.stringValue("path")
            ?: return WorkspaceCommandResult.Failure("qms returned a response without a project path.")
        if (projectPath.isBlank()) {
            return WorkspaceCommandResult.Failure("qms returned an empty project path.")
        }
        return WorkspaceCommandResult.Success(projectPath)
    }
}

private fun parseResponse(output: String): JsonObject? =
    try {
        Json.parseToJsonElement(output.trim()).jsonObject
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

private fun JsonObject.errorMessage(): String? =
    try {
        this["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

private fun JsonObject.stringValue(key: String): String? =
    try {
        this[key]
            ?.jsonPrimitive
            ?.takeIf { it.isString }
            ?.contentOrNull
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

private fun JsonObject.intValue(key: String): Int? =
    try {
        this[key]
            ?.jsonPrimitive
            ?.takeUnless { it.isString }
            ?.intOrNull
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

private fun executeProcess(executable: String, arguments: List<String>): ProcessResult {
    val process = ProcessBuilder(listOf(executable) + arguments)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    return ProcessResult(process.waitFor(), output)
}

private fun chooseProjectDirectory(): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Choose project directory"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.toPath()
    } else {
        null
    }
}

fun main() = application {
    val cli = remember { WorkspaceCli() }
    val scope = rememberCoroutineScope()
    var projectPath by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    fun selectProject(operation: WorkspaceOperation) {
        val selectedPath = chooseProjectDirectory()
        if (selectedPath == null) {
            statusMessage = "${operation.label} cancelled."
            hasError = false
            return
        }
        runWorkspaceOperation(
            operation = operation,
            path = selectedPath,
            cli = cli,
            scope = scope,
            onBusyChanged = { isBusy = it },
            onComplete = { result ->
                when (result) {
                    is WorkspaceCommandResult.Success -> {
                        projectPath = result.path
                        statusMessage = when (operation) {
                            WorkspaceOperation.Create -> "Project created successfully."
                            WorkspaceOperation.Open -> "Project verified and opened."
                        }
                        hasError = false
                    }
                    is WorkspaceCommandResult.Failure -> {
                        statusMessage = result.message
                        hasError = true
                    }
                }
            },
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "eQMS",
        state = rememberWindowState(width = 840.dp, height = 560.dp),
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("eQMS", style = MaterialTheme.typography.h4)
                    Text("Create a project or open an existing verified workspace.")
                    projectPath?.let { path ->
                        Text("Current project: $path")
                    }

                    Row {
                        Button(
                            enabled = !isBusy,
                            onClick = { selectProject(WorkspaceOperation.Create) },
                        ) {
                            Text(WorkspaceOperation.Create.label)
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = { selectProject(WorkspaceOperation.Open) },
                        ) {
                            Text(WorkspaceOperation.Open.label)
                        }
                    }

                    if (isBusy) {
                        Text("Working…")
                    }
                    statusMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (hasError) MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun runWorkspaceOperation(
    operation: WorkspaceOperation,
    path: Path,
    cli: WorkspaceCli,
    scope: kotlinx.coroutines.CoroutineScope,
    onBusyChanged: (Boolean) -> Unit,
    onComplete: (WorkspaceCommandResult) -> Unit,
) {
    onBusyChanged(true)
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            cli.execute(operation, path)
        }
        onComplete(result)
        onBusyChanged(false)
    }
}
