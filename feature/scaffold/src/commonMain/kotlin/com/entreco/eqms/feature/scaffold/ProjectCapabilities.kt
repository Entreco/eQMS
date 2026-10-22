package com.entreco.eqms.feature.scaffold

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
