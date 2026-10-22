package com.entreco.eqms.feature.search

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

data class SearchPreparation(val snapshot: String, val indexedRevisions: Int)
data class SearchHit(
    val id: String,
    val title: String,
    val revision: String,
    val configuredAuthor: String,
    val localTimestamp: String,
    val excerpt: String,
)
data class SearchPage(val hits: List<SearchHit>, val nextOffset: Int?)
data class SearchResults(
    val snapshot: String,
    val historical: Boolean,
    val documents: SearchPage,
    val authors: SearchPage,
    val content: SearchPage,
)
enum class SearchGroup { Documents, Authors, Content }

/** Final-input-to-layout timing, including debounce, native query and decoding. */
data class SearchRenderTiming(val query: String, val historical: Boolean, val elapsedMilliseconds: Double, val generation: Int = 0)

internal class SearchRenderRequest(
    val generation: Int,
    val query: String,
    val historical: Boolean,
    val started: TimeSource.Monotonic.ValueTimeMark,
)

class SearchIndexOutOfDateException(message: String) : Exception(message)

interface SearchCapabilities {
    suspend fun prepareSearch(project: String): Result<SearchPreparation> =
        Result.failure(UnsupportedOperationException("Search preparation is unavailable."))
    suspend fun searchDocuments(
        project: String,
        query: String,
        snapshot: String? = null,
        documentsOffset: Int = 0,
        authorsOffset: Int = 0,
        contentOffset: Int = 0,
    ): Result<SearchResults> = Result.failure(UnsupportedOperationException("Search is unavailable."))
}

internal fun SearchResults.page(group: SearchGroup): SearchPage = when (group) {
    SearchGroup.Documents -> documents
    SearchGroup.Authors -> authors
    SearchGroup.Content -> content
}

class DocumentSearch(
    private val project: String,
    private val capabilities: SearchCapabilities,
    private val scope: CoroutineScope,
) {
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<SearchResults?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    internal var renderRequest by mutableStateOf<SearchRenderRequest?>(null)
        private set
    private var measuredGeneration = -1
    private var prepared = false
    private var generation = 0
    private var disposed = false
    private var job: Job? = null
    private var started = TimeSource.Monotonic.markNow()

    fun dispose() { disposed = true; generation++; job?.cancel() }
    fun updateQuery(value: String) { started = TimeSource.Monotonic.markNow(); query = value; restart(debounce = true) }
    fun refresh() { started = TimeSource.Monotonic.markNow(); prepared = false; restart(debounce = false) }
    internal fun claimRenderTiming(request: SearchRenderRequest): SearchRenderTiming? {
        if (!current(request.generation) || renderRequest !== request || measuredGeneration == request.generation) return null
        measuredGeneration = request.generation
        return SearchRenderTiming(
            request.query, request.historical, request.started.elapsedNow().inWholeNanoseconds / 1_000_000.0, request.generation,
        )
    }

    private fun restart(debounce: Boolean) {
        val request = ++generation
        job?.cancel()
        results = null
        renderRequest = null
        error = null
        preparing = false
        loading = query.isNotBlank()
        val text = query
        job = scope.launch {
            if (debounce) delay(10)
            if (!prepared) {
                preparing = true
                val preparation = capabilities.prepareSearch(project)
                if (!current(request)) return@launch
                preparing = false
                if (preparation.isFailure) {
                    error = preparation.exceptionOrNull()?.message ?: "Could not prepare search."
                    loading = false
                    return@launch
                }
                prepared = true
            }
            if (text.isBlank()) { loading = false; return@launch }
            var response = capabilities.searchDocuments(project, text)
            if (!current(request)) return@launch
            if (response.exceptionOrNull() is SearchIndexOutOfDateException) {
                prepared = false
                preparing = true
                val preparation = capabilities.prepareSearch(project)
                if (!current(request)) return@launch
                preparing = false
                if (preparation.isFailure) {
                    error = preparation.exceptionOrNull()?.message ?: "Could not prepare search."
                    loading = false
                    return@launch
                }
                prepared = true
                response = capabilities.searchDocuments(project, text)
                if (!current(request)) return@launch
            }
            loading = false
            response.fold(
                onSuccess = {
                    results = it
                    renderRequest = SearchRenderRequest(request, text, it.historical, started)
                },
                onFailure = { error = it.message ?: "Search failed. Refresh search to retry." },
            )
        }
    }

    fun more(group: SearchGroup) {
        val previous = results ?: return
        val offset = previous.page(group).nextOffset ?: return
        if (loading || preparing) return
        val request = ++generation
        renderRequest = null
        val text = query
        loading = true
        error = null
        job = scope.launch {
            val response = capabilities.searchDocuments(
                project, text, previous.snapshot,
                documentsOffset = if (group == SearchGroup.Documents) offset else 0,
                authorsOffset = if (group == SearchGroup.Authors) offset else 0,
                contentOffset = if (group == SearchGroup.Content) offset else 0,
            )
            if (!current(request)) return@launch
            loading = false
            val next = response.getOrNull()
            if (next == null || next.snapshot != previous.snapshot || next.historical != previous.historical) {
                results = null
                prepared = false
                error = "${response.exceptionOrNull()?.message ?: "Search snapshot changed."} Refresh search to restart."
                return@launch
            }
            val page = next.page(group).copy(hits = previous.page(group).hits + next.page(group).hits)
            results = when (group) {
                SearchGroup.Documents -> previous.copy(documents = page)
                SearchGroup.Authors -> previous.copy(authors = page)
                SearchGroup.Content -> previous.copy(content = page)
            }
        }
    }

    private fun current(request: Int) = !disposed && generation == request
}
