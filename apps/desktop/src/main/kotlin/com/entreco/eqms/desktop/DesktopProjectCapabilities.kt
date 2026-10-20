package com.entreco.eqms.desktop

import com.entreco.eqms.ui.DirectoryListing
import com.entreco.eqms.ui.ProjectCapabilities
import com.entreco.eqms.ui.ProjectDirectory
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun createProjectFolder(parent: Path, name: String): Path {
    require(
        name.isNotBlank() && name != "." && name != ".." &&
            '/' !in name && '\\' !in name && ':' !in name &&
            name.none { it.code < 32 } && !Path.of(name).isAbsolute,
    ) { "Enter a single folder name, not a path." }
    return Files.createDirectory(parent.resolve(name))
}

internal class DesktopProjectCapabilities(
    private val cli: WorkspaceCli = WorkspaceCli(),
) : ProjectCapabilities {
    override suspend fun listDirectory(path: String?): Result<DirectoryListing> = filesystemOperation {
        val directory = Paths.get(path ?: System.getProperty("user.home")).toRealPath()
        require(Files.isDirectory(directory)) { "Choose an existing directory." }
        val children = Files.list(directory).use { entries ->
            entries.filter { Files.isDirectory(it) }
                .map { ProjectDirectory(it.fileName.toString(), it.toString()) }
                .toList()
                .sortedBy { it.name.lowercase() }
        }
        DirectoryListing(directory.toString(), directory.parent?.toString(), children)
    }

    override suspend fun createFolder(parent: String, name: String): Result<String> = filesystemOperation {
        createProjectFolder(Paths.get(parent), name).toString()
    }

    override suspend fun openProject(path: String, create: Boolean): Result<String> = filesystemOperation {
        when (val result = cli.execute(if (create) WorkspaceOperation.Create else WorkspaceOperation.Open, Paths.get(path))) {
            is WorkspaceCommandResult.Success -> result.path
            is WorkspaceCommandResult.Failure -> throw IOException(result.message)
        }
    }

    private suspend fun <T> filesystemOperation(operation: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(operation())
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: UncheckedIOException) {
            Result.failure(error.cause ?: error)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        } catch (error: SecurityException) {
            Result.failure(error)
        }
    }
}
