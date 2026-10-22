package com.entreco.eqms.ui

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

data class DocumentSnapshot(
    val id: String,
    val title: String,
    val body: String,
    val revision: String,
    val contentHash: String,
    val eventHash: String,
    val configuredAuthor: String,
    val localTimestamp: String,
)

interface DocumentCapabilities : SearchCapabilities {
    suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>>
    suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun documentHistory(project: String, id: String): Result<List<DocumentSnapshot>>
    suspend fun documentDiff(project: String, id: String, from: String, to: String): Result<String>
    suspend fun showLatestDocument(project: String, id: String): Result<DocumentSnapshot> =
        documentHistory(project, id).mapCatching { history ->
            history.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Saved draft was not found.")
        }
    suspend fun showDocument(project: String, id: String, revision: String): Result<DocumentSnapshot> =
        documentHistory(project, id).mapCatching { history ->
            history.firstOrNull { it.id == id && it.revision == revision }
                ?: throw IllegalArgumentException("Saved revision was not found.")
        }
}
