package com.entreco.eqms.desktop

import com.entreco.eqms.feature.scaffold.BrandingDraft
import com.entreco.eqms.feature.scaffold.BrandingPreview
import com.entreco.eqms.feature.scaffold.DirectoryListing
import com.entreco.eqms.feature.scaffold.ProjectCapabilities
import com.entreco.eqms.feature.scaffold.ProjectDirectory
import com.entreco.eqms.feature.ui.BrandTokens
import java.awt.FileDialog
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

/** `=` form so values starting with `-` are never read as options; a PNG logo replaces the seed. */
internal fun brandingArguments(draft: BrandingDraft): List<String> = buildList {
    add("--name=${draft.name}")
    if (draft.logoFile != null) add("--logo-file=${draft.logoFile}") else if (draft.seed.isNotEmpty()) add("--seed=${draft.seed}")
    if (draft.accent != "auto" || draft.logoFile == null) add("--accent=${draft.accent}")
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

    override suspend fun brandTokens(path: String): Result<BrandTokens?> = withContext(Dispatchers.IO) {
        cli.brandingData(Paths.get(path)).mapCatching(::brandTokensFrom)
    }

    override suspend fun projectBranding(path: String): Result<BrandingPreview?> = withContext(Dispatchers.IO) {
        cli.brandingData(Paths.get(path)).mapCatching(::brandingPreviewFrom)
    }

    override suspend fun saveBranding(path: String, draft: BrandingDraft): Result<BrandingPreview> =
        withContext(Dispatchers.IO) {
            cli.brandingSet(Paths.get(path), brandingArguments(draft)).mapCatching(::brandingPreviewFrom)
        }

    override suspend fun previewBranding(draft: BrandingDraft): Result<BrandingPreview> =
        withContext(Dispatchers.IO) {
            cli.brandingPreview(brandingArguments(draft)).mapCatching(::brandingPreviewFrom)
        }

    override suspend fun createProject(path: String, draft: BrandingDraft): Result<String> =
        filesystemOperation {
            when (val result = cli.execute(WorkspaceOperation.Create, Paths.get(path), brandingArguments(draft))) {
                is WorkspaceCommandResult.Success -> result.path
                is WorkspaceCommandResult.Failure -> throw IOException(
                    "${result.message} The folder may already be initialised; use File > Open project to continue.",
                )
            }
        }

    override suspend fun chooseLogoFile(): String? = withContext(Dispatchers.IO) {
        val dialog = FileDialog(null as java.awt.Frame?, "Choose a PNG logo", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".png", ignoreCase = true) }
        dialog.isVisible = true
        dialog.file?.let { java.io.File(dialog.directory, it).path }.also { dialog.dispose() }
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
