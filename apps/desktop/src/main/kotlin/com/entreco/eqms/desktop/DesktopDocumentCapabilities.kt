package com.entreco.eqms.desktop

import com.entreco.eqms.feature.document.DocumentCapabilities
import com.entreco.eqms.feature.document.DocumentHistorySnapshot
import com.entreco.eqms.feature.document.DocumentSnapshot
import com.entreco.eqms.feature.document.DocumentStatus
import com.entreco.eqms.feature.document.DocumentLifecycleEvent
import com.entreco.eqms.feature.document.RenderedDocument
import com.entreco.eqms.feature.document.RenderNode
import com.entreco.eqms.feature.document.RenderHeading
import com.entreco.eqms.feature.document.RenderSource
import com.entreco.eqms.feature.document.DocumentImage
import com.entreco.eqms.feature.search.SearchHit
import com.entreco.eqms.feature.search.SearchPage
import com.entreco.eqms.feature.search.SearchPreparation
import com.entreco.eqms.feature.search.SearchResults
import com.entreco.eqms.feature.search.SearchIndexOutOfDateException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

internal fun interface DocumentExecutor {
    fun execute(executable: String, arguments: List<String>, input: String?): ProcessResult
}

internal class DesktopDocumentCapabilities(
    private val executable: String = System.getenv("EQMS_QMS_PATH")?.takeIf(String::isNotBlank) ?: "qms",
    private val executor: DocumentExecutor = DocumentExecutor { command, arguments, input ->
        executeProcess(command, arguments, input)
    },
) : DocumentCapabilities {
    override suspend fun renderBuffer(project: String, title: String, body: String): Result<RenderedDocument> =
        operation("render-buffer", project, emptyList(), buildJsonObject {
            put("title", title); put("body", body)
        }.toString()) { data ->
            renderedDocument(data).also {
                require(it.title == title && it.inputDigest == bodyDigest(body) && it.source.kind == "unsaved" &&
                    it.source.id.isEmpty() && it.source.revision.isEmpty() && it.source.contentHash.isEmpty()) {
                    "qms returned rendering for a different buffer."
                }
            }
        }

    override suspend fun renderSaved(project: String, document: DocumentSnapshot): Result<RenderedDocument> =
        operation("render", project, listOf(document.id, "--revision", document.revision)) { data ->
            renderedDocument(data).also {
                require(it.title == document.title && it.inputDigest == bodyDigest(document.body) &&
                    it.source == RenderSource("saved", document.id, document.revision, document.contentHash)) {
                    "qms returned rendering for a different saved revision."
                }
            }
        }

    override suspend fun readDocumentImage(project: String, asset: String): Result<DocumentImage> =
        operation("render-asset", project, listOf(asset)) { data ->
            val value = data.jsonObject
            require(value.string("asset") == asset && value.getValue("unversioned").jsonPrimitive.boolean) {
                "qms returned an unexpected asset."
            }
            val width = value.nonnegativeInt("width")
            val height = value.nonnegativeInt("height")
            require(width > 0 && height > 0 && width.toLong() * height <= 16_000_000) { "Image dimensions exceed preview limits." }
            val encoded = value.string("base64")
            require(encoded.length <= 14 * 1024 * 1024) { "Image exceeds preview byte limit." }
            val bytes = java.util.Base64.getDecoder().decode(encoded)
            require(bytes.size <= 10 * 1024 * 1024 && value.string("digest") == bodyDigest(bytes)) { "Invalid image digest/size." }
            DocumentImage(bytes, width, height)
        }

    override suspend fun openDocumentLink(target: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uri = java.net.URI(target)
            require(uri.scheme in listOf("https", "http", "mailto") &&
                target.none { it.isWhitespace() || it.isISOControl() }) { "Unsupported external link." }
            if (uri.scheme == "mailto") java.awt.Desktop.getDesktop().mail(uri)
            else {
                require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "Invalid external link." }
                java.awt.Desktop.getDesktop().browse(uri)
            }
            Result.success(Unit)
        } catch (error: java.net.URISyntaxException) {
            Result.failure(error)
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        } catch (error: UnsupportedOperationException) {
            Result.failure(error)
        } catch (error: SecurityException) {
            Result.failure(error)
        }
    }

    override suspend fun transitionDocument(
        project: String, document: DocumentSnapshot, target: DocumentStatus,
    ): Result<DocumentSnapshot> =
        operation("transition", project, listOf(document.id, "--expected", document.revision, "--to", target.label)) {
            snapshot(it).also { updated ->
                require(updated.id == document.id && updated.status == target && updated.revision != document.revision &&
                    updated.contentHash == document.contentHash && updated.title == document.title && updated.body == document.body) {
                    "qms returned an unexpected lifecycle transition."
                }
            }
        }

    override suspend fun documentLifecycleHistory(project: String, id: String): Result<List<DocumentLifecycleEvent>> =
        operation("lifecycle-history", project, listOf(id)) { data ->
            lifecycleEvents(data)
        }

    override suspend fun documentHistorySnapshot(project: String, id: String): Result<DocumentHistorySnapshot> =
        operation("history-snapshot", project, listOf(id)) { data ->
            val value = data.jsonObject
            val revisions = value.getValue("revisions").jsonArray.map(::snapshot)
            require(revisions.isNotEmpty() && revisions.all { it.id == id }) {
                "qms returned invalid document history."
            }
            require(revisions.map { it.revision }.distinct().size == revisions.size) {
                "qms returned duplicate document revisions."
            }
            val events = lifecycleEvents(value.getValue("lifecycle_events"))
            val revisionsById = revisions.associateBy { it.revision }
            events.forEach { event ->
                val revision = revisionsById[event.revision]
                    ?: throw IllegalArgumentException("Lifecycle event is not part of the returned document history.")
                require(
                    revision.eventHash == event.eventHash &&
                        revision.contentHash == event.contentHash &&
                        revision.status == event.to,
                ) { "Lifecycle event differs from its document revision." }
            }
            require(events.map { it.revision }.toSet() ==
                revisions.filter { it.status != DocumentStatus.Draft }.map { it.revision }.toSet()) {
                "Lifecycle events do not match document revision statuses."
            }
            DocumentHistorySnapshot(revisions, events)
        }

    override suspend fun prepareSearch(project: String): Result<SearchPreparation> =
        operation("search.prepare", project, listOf("--prepare"), search = true) { data ->
            val value = data.jsonObject
            SearchPreparation(value.nonemptyString("snapshot"), value.nonnegativeInt("indexed_revisions"))
        }

    override suspend fun searchDocuments(
        project: String, query: String, snapshot: String?,
        documentsOffset: Int, authorsOffset: Int, contentOffset: Int,
    ): Result<SearchResults> {
        if (listOf(documentsOffset, authorsOffset, contentOffset).any { it < 0 } ||
            (listOf(documentsOffset, authorsOffset, contentOffset).any { it > 0 } && snapshot.isNullOrBlank())
        ) return Result.failure(IllegalArgumentException("Search pagination requires a snapshot and nonnegative offsets."))
        val arguments = listOf("--query", query) +
            (snapshot?.let { listOf("--snapshot", it) } ?: emptyList()) +
            listOf("--documents-offset", "$documentsOffset", "--authors-offset", "$authorsOffset", "--content-offset", "$contentOffset")
        return operation("search", project, arguments, search = true) { data ->
            val value = data.jsonObject
            val historical = requireNotNull(value["historical"]).jsonPrimitive
            require(!historical.isString && historical.booleanOrNull != null) { "Invalid historical flag." }
            SearchResults(
                value.nonemptyString("snapshot"), historical.boolean,
                searchPage(value.getValue("documents"), documentsOffset),
                searchPage(value.getValue("authors"), authorsOffset),
                searchPage(value.getValue("content"), contentOffset),
            ).also { result ->
                require(snapshot == null || result.snapshot == snapshot) { "Search snapshot changed. Prepare and restart search." }
            }
        }
    }

    override suspend fun showDocument(project: String, id: String, revision: String): Result<DocumentSnapshot> =
        operation("show", project, listOf(id, "--revision", revision)) {
            snapshot(it).also { document ->
                require(document.id == id && document.revision == revision) { "qms returned a different saved revision." }
            }
        }

    override suspend fun showLatestDocument(project: String, id: String): Result<DocumentSnapshot> =
        operation("show", project, listOf(id)) {
            snapshot(it).also { document ->
                require(document.id == id) { "qms returned a different document." }
            }
        }

    override suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>> =
        operation("list", project) { data ->
            data.jsonArray.map(::snapshot).also { documents ->
                require(documents.map { it.id }.distinct().size == documents.size) { "Duplicate document IDs in qms response." }
            }
        }

    override suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot> =
        operation("create", project, listOf("--title", title), body) {
            snapshot(it).also { document ->
                require(document.status == DocumentStatus.Draft) { "qms created a non-Draft document." }
            }
        }

    override suspend fun saveDocument(
        project: String, id: String, expected: String, title: String, body: String,
    ): Result<DocumentSnapshot> =
        operation("save", project, listOf(id, "--title", title, "--expected", expected), body) {
            snapshot(it).also { doc ->
                require(doc.id == id && doc.status == DocumentStatus.Draft) { "qms returned a different document or non-Draft save." }
            }
        }

    override suspend fun documentHistory(project: String, id: String): Result<List<DocumentSnapshot>> =
        operation("history", project, listOf(id)) { data ->
            data.jsonArray.map(::snapshot).also { revisions ->
                require(revisions.isNotEmpty() && revisions.all { it.id == id }) { "qms returned invalid document history." }
                require(revisions.map { it.revision }.distinct().size == revisions.size) { "qms returned duplicate revisions." }
            }
        }

    override suspend fun documentDiff(project: String, id: String, from: String, to: String): Result<String> =
        operation("diff", project, listOf(id, from, to)) { data ->
            require(data.jsonPrimitive.isString) { "qms returned invalid comparison text." }
            data.jsonPrimitive.content
        }

    private suspend fun <T> operation(
        command: String,
        project: String,
        arguments: List<String> = emptyList(),
        input: String? = null,
        search: Boolean = false,
        decode: (JsonElement) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val argv = if (search) listOf("search", "--json", project)
                else if (command == "render-buffer") listOf("document", command, "--json")
                else listOf("document", command, "--json", project)
            val result = runInterruptible { executor.execute(executable, argv + arguments, input) }
            val response = Json.parseToJsonElement(result.output.trim()).jsonObject
            require(response.qmsSchemaVersion() == 2) { "qms returned an unsupported JSON schema version." }
            require(response.isQmsJsonV2Envelope()) { "qms returned an invalid or unsupported JSON response." }
            require(response.stringValue("command") == if (search) command else "document.$command") {
                "qms returned a response for an unexpected command."
            }
            if (result.exitCode != 0) {
                require(response.stringValue("status") == "error") {
                    "qms $command failed (${result.exitCode})."
                }
                val message = response.errorMessage() ?: "qms $command failed (${result.exitCode})."
                if (command == "search" &&
                    (response["error"] as? JsonObject)?.stringValue("code") == "OPERATION_FAILED" &&
                    message == "Search index is out of date. Run qms search <path> --prepare."
                ) return@withContext Result.failure(SearchIndexOutOfDateException(message))
                throw IOException(message)
            }
            require(response.string("status") == "ok") { "qms returned an unsuccessful response." }
            Result.success(decode(requireNotNull(response["data"]) { "qms returned no operation data." }))
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: SerializationException) {
            Result.failure(IOException("qms returned invalid JSON: ${error.message}", error))
        } catch (error: IllegalArgumentException) {
            Result.failure(IOException("Invalid qms response: ${error.message}", error))
        } catch (error: IllegalStateException) {
            Result.failure(IOException("Invalid qms response: ${error.message}", error))
        } catch (error: NoSuchElementException) {
            Result.failure(IOException("Invalid qms response: ${error.message}", error))
        } catch (error: SecurityException) {
            Result.failure(error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(IOException("The qms operation was interrupted.", error))
        }
    }
}

private fun JsonObject.nonemptyString(name: String): String =
    string(name).also { require(it.isNotBlank()) { "$name must not be blank." } }

private fun JsonObject.nonnegativeInt(name: String): Int {
    val value = requireNotNull(this[name]) { "Missing $name." }.jsonPrimitive
    return requireNotNull(value.takeUnless { it.isString }?.intOrNull) { "$name must be an integer." }
        .also { require(it >= 0) { "$name must not be negative." } }
}

private fun searchPage(data: JsonElement, offset: Int): SearchPage {
    val value = data.jsonObject
    val hits = value.getValue("hits").jsonArray.map {
        val hit = it.jsonObject
        val id = hit.string("id")
        val revision = hit.string("revision")
        require(Regex("DOC-[0-9a-f]{32}").matches(id)) { "Invalid search document ID." }
        require(Regex("([0-9a-f]{40}|[0-9a-f]{64})").matches(revision)) { "Invalid search revision." }
        SearchHit(id, hit.nonemptyString("title"), revision, hit.string("configured_author"), hit.string("local_timestamp"), hit.string("excerpt"))
    }
    require(hits.size <= 5 && hits.map { it.id }.distinct().size == hits.size) { "Invalid search page." }
    val next = if (value.getValue("next_offset") === JsonNull) null else value.nonnegativeInt("next_offset")
    require(next == null || (hits.size == 5 && next.toLong() == offset.toLong() + hits.size)) { "Invalid search next offset." }
    return SearchPage(hits, next)
}

private fun JsonObject.string(name: String): String {
    val value = requireNotNull(this[name]) { "Missing $name." }.jsonPrimitive
    require(value.isString) { "$name must be text." }
    return value.content
}

private fun snapshot(data: JsonElement): DocumentSnapshot {
    val value = data.jsonObject
    val id = value.string("id")
    val revision = value.string("revision")
    val contentHash = value.string("content_hash")
    val eventHash = value.string("event_hash")
    val title = value.string("title")
    require(Regex("DOC-[0-9a-f]{32}").matches(id)) { "Invalid document ID." }
    validateHashes(revision, contentHash, eventHash)
    require(title.isNotBlank()) { "Invalid document title." }
    return DocumentSnapshot(
        id, title, value.string("body"), revision, contentHash, eventHash,
        value.string("configured_author"), value.string("local_timestamp"), status(value.string("status")),
    )
}

private fun lifecycleEvents(data: JsonElement): List<DocumentLifecycleEvent> =
    data.jsonArray.map { element ->
        val value = element.jsonObject
        val revision = value.string("revision")
        val eventHash = value.string("event_hash")
        val contentHash = value.string("content_hash")
        validateHashes(revision, contentHash, eventHash)
        DocumentLifecycleEvent(
            revision, eventHash, contentHash, status(value.string("from_status")),
            status(value.string("to_status")), value.nonemptyString("local_timestamp"),
        )
    }.also { events ->
        require(events.map { it.revision }.distinct().size == events.size) {
            "Duplicate lifecycle revisions."
        }
        require(events.map { it.eventHash }.distinct().size == events.size) {
            "Duplicate lifecycle events."
        }
        events.zipWithNext().forEach { (previous, next) ->
            require(previous.to == next.from && previous.contentHash == next.contentHash) {
                "Invalid lifecycle event chain."
            }
        }
        var previous = DocumentStatus.Draft
        events.forEach { event ->
            require(event.from == previous && event.to in when (previous) {
                DocumentStatus.Draft -> listOf(DocumentStatus.InReview)
                DocumentStatus.InReview -> listOf(DocumentStatus.Approved)
                DocumentStatus.Approved -> listOf(DocumentStatus.Effective)
                DocumentStatus.Effective -> listOf(DocumentStatus.Obsolete, DocumentStatus.Superseded)
                else -> emptyList()
            }) { "Invalid lifecycle transition history." }
            previous = event.to
        }
    }

private fun status(value: String): DocumentStatus =
    DocumentStatus.entries.firstOrNull { it.label == value }
        ?: throw IllegalArgumentException("Unknown document status: $value")

private fun validateHashes(revision: String, contentHash: String, eventHash: String) {
    require(Regex("([0-9a-f]{40}|[0-9a-f]{64})").matches(revision)) { "Invalid saved revision." }
    require(Regex("sha256:[0-9a-f]{64}").matches(contentHash)) { "Invalid content hash." }
    require(Regex("[0-9a-f]{64}").matches(eventHash)) { "Invalid event hash." }
}
