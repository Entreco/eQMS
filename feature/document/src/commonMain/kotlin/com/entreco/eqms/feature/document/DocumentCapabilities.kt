package com.entreco.eqms.feature.document

import com.entreco.eqms.feature.search.SearchCapabilities

enum class DocumentStatus(val label: String) {
    Draft("Draft"), InReview("In Review"), Approved("Approved"), Effective("Effective"),
    Obsolete("Obsolete"), Superseded("Superseded");
}

data class DocumentLifecycleEvent(
    val revision: String,
    val eventHash: String,
    val contentHash: String,
    val from: DocumentStatus,
    val to: DocumentStatus,
    val localTimestamp: String,
)

data class DocumentHistorySnapshot(
    val revisions: List<DocumentSnapshot>,
    val lifecycleEvents: List<DocumentLifecycleEvent>,
)

data class DocumentSnapshot(
    val id: String,
    val title: String,
    val body: String,
    val revision: String,
    val contentHash: String,
    val eventHash: String,
    val configuredAuthor: String,
    val localTimestamp: String,
    val status: DocumentStatus = DocumentStatus.Draft,
)

interface DocumentCapabilities : SearchCapabilities {
    suspend fun renderBuffer(project: String, title: String, body: String): Result<RenderedDocument> =
        Result.failure(UnsupportedOperationException("Document rendering is not configured. Source remains available."))
    suspend fun renderSaved(project: String, document: DocumentSnapshot): Result<RenderedDocument> =
        Result.failure(UnsupportedOperationException("Saved document rendering is not configured. Source remains available."))
    suspend fun readDocumentImage(project: String, asset: String): Result<DocumentImage> =
        Result.failure(UnsupportedOperationException("Local image loading is not configured."))
    suspend fun openDocumentLink(target: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("External link opening is not configured."))
    suspend fun transitionDocument(project: String, document: DocumentSnapshot, target: DocumentStatus): Result<DocumentSnapshot>
    suspend fun documentLifecycleHistory(project: String, id: String): Result<List<DocumentLifecycleEvent>>
    suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>>
    suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot>
    suspend fun documentHistory(project: String, id: String): Result<List<DocumentSnapshot>>
    suspend fun documentHistorySnapshot(project: String, id: String): Result<DocumentHistorySnapshot> {
        val revisions = documentHistory(project, id)
        if (revisions.isFailure) {
            return Result.failure(requireNotNull(revisions.exceptionOrNull()))
        }
        val lifecycleEvents = documentLifecycleHistory(project, id)
        if (lifecycleEvents.isFailure) {
            return Result.failure(requireNotNull(lifecycleEvents.exceptionOrNull()))
        }
        return Result.success(
            DocumentHistorySnapshot(revisions.getOrThrow(), lifecycleEvents.getOrThrow()),
        )
    }
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
