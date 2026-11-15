package com.entreco.eqms.feature.scaffold

import com.entreco.eqms.feature.ui.BrandTokens

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

    /** Brand tokens of the project at [path]; `null` when the capability has none. */
    suspend fun brandTokens(path: String): Result<BrandTokens?> = Result.success(null)
}
