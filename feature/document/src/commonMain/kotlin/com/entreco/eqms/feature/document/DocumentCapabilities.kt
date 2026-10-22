package com.entreco.eqms.feature.document

import com.entreco.eqms.feature.search.SearchCapabilities

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
