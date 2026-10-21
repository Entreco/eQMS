package com.entreco.eqms.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.entreco.eqms.ui.ProjectScreen
import java.io.IOException
import java.nio.file.Path
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

internal fun executeProcess(executable: String, arguments: List<String>, input: String? = null): ProcessResult {
    val process = ProcessBuilder(listOf(executable) + arguments)
        .redirectErrorStream(true)
        .start()
    val writeError = java.util.concurrent.atomic.AtomicReference<IOException?>()
    val writer = Thread {
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(input ?: "") }
        } catch (error: IOException) {
            writeError.set(error)
        }
    }.apply { start() }
    try {
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exit = process.waitFor()
        writer.join()
        if (exit == 0) writeError.get()?.let { throw it }
        return ProcessResult(exit, output)
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}

fun main() = application {
    val capabilities = remember { DesktopProjectCapabilities() }
    val documents = remember { DesktopDocumentCapabilities() }
    var closeRequested by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = { closeRequested = true },
        title = "eQMS",
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
    ) {
        ProjectScreen(
            capabilities, documents, closeRequested,
            onCloseHandled = { closeRequested = false },
            onCloseConfirmed = ::exitApplication,
        )
    }
}
