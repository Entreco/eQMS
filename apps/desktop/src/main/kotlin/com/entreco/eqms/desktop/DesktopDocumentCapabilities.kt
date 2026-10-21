package com.entreco.eqms.desktop

import com.entreco.eqms.ui.DocumentCapabilities
import com.entreco.eqms.ui.DocumentSnapshot
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    override suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>> =
        operation("list", project) { data ->
            data.jsonArray.map(::snapshot).also { documents ->
                require(documents.map { it.id }.distinct().size == documents.size) { "Duplicate document IDs in qms response." }
            }
        }

    override suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot> =
        operation("create", project, listOf("--title", title), body, ::snapshot)

    override suspend fun saveDocument(
        project: String, id: String, expected: String, title: String, body: String,
    ): Result<DocumentSnapshot> =
        operation("save", project, listOf(id, "--title", title, "--expected", expected), body) {
            snapshot(it).also { doc -> require(doc.id == id) { "qms returned a different document." } }
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
        decode: (JsonElement) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val result = executor.execute(executable, listOf("document", command, "--json", project) + arguments, input)
            val response = Json.parseToJsonElement(result.output.trim()).jsonObject
            if (result.exitCode != 0) {
                val message = response["error"]?.jsonObject?.get("message")?.jsonPrimitive
                throw IOException(message?.takeIf { it.isString }?.content ?: "qms document $command failed (${result.exitCode}).")
            }
            val schema = response["schema_version"]?.jsonPrimitive
            require(schema?.isString == false && schema.intOrNull == 1) { "qms returned an unsupported JSON schema version." }
            require(response.string("status") == "ok") { "qms returned an unsuccessful response." }
            require(response.string("command") == "document.$command") { "qms returned a response for an unexpected command." }
            Result.success(decode(requireNotNull(response["data"]) { "qms returned no document data." }))
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: SerializationException) {
            Result.failure(IOException("qms returned invalid JSON: ${error.message}", error))
        } catch (error: IllegalArgumentException) {
            Result.failure(IOException("Invalid qms document response: ${error.message}", error))
        } catch (error: IllegalStateException) {
            Result.failure(IOException("Invalid qms document response: ${error.message}", error))
        } catch (error: SecurityException) {
            Result.failure(error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(IOException("The document operation was interrupted.", error))
        }
    }
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
    require(Regex("([0-9a-f]{40}|[0-9a-f]{64})").matches(revision)) { "Invalid saved revision." }
    require(Regex("sha256:[0-9a-f]{64}").matches(contentHash)) { "Invalid content hash." }
    require(Regex("[0-9a-f]{64}").matches(eventHash)) { "Invalid event hash." }
    require(title.isNotBlank() && value.string("status") == "Draft") { "Invalid draft metadata." }
    return DocumentSnapshot(
        id, title, value.string("body"), revision, contentHash, eventHash,
        value.string("configured_author"), value.string("local_timestamp"),
    )
}
