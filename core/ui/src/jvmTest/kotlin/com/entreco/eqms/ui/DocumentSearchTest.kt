package com.entreco.eqms.ui

import kotlin.test.*
import kotlinx.coroutines.*

class DocumentSearchTest {
    private val empty = SearchPage(emptyList(), null)
    private fun hit(title: String) = SearchHit(title, title, "revision", "Author", "1", "saved text")
    private fun results(title: String, snapshot: String = "snapshot") =
        SearchResults(snapshot, false, SearchPage(listOf(hit(title)), null), empty, empty)
    private suspend fun await(condition: () -> Boolean) {
        withTimeout(3000) { while (!condition()) delay(1) }
    }

    @Test
    fun debouncesAndRejectsObsoleteResponsesAndRestoresBlankList() = runBlocking {
        val old = CompletableDeferred<Result<SearchResults>>()
        val queries = mutableListOf<String>()
        val capabilities = object : SearchCapabilities {
            override suspend fun prepareSearch(project: String) = Result.success(SearchPreparation("snapshot", 1))
            override suspend fun searchDocuments(project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int): Result<SearchResults> {
                queries += query
                return if (query == "old") withContext(NonCancellable) { old.await() } else Result.success(results(query))
            }
        }
        val search = DocumentSearch("project", capabilities, this)
        search.updateQuery("o")
        search.updateQuery("old")
        await { queries == listOf("old") }
        search.updateQuery("new")
        await { search.results?.documents?.hits?.singleOrNull()?.title == "new" }
        old.complete(Result.success(results("old")))
        delay(10)
        assertEquals("new", search.results?.documents?.hits?.single()?.title)
        search.updateQuery("   ")
        assertNull(search.results)
        await { !search.loading }
        assertEquals(listOf("old", "new"), queries)
        search.dispose()
    }

    @Test
    fun paginationMergesOnlyRequestedGroupAndStalePagesRequireRestart() = runBlocking {
        val offsets = mutableListOf<List<Int>>()
        var stale = false
        val capabilities = object : SearchCapabilities {
            override suspend fun prepareSearch(project: String) = Result.success(SearchPreparation("snapshot", 10))
            override suspend fun searchDocuments(project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int): Result<SearchResults> {
                offsets += listOf(documentsOffset, authorsOffset, contentOffset)
                if (stale) return Result.failure(IllegalArgumentException("Stale snapshot; prepare search"))
                val page = SearchPage(List(5) { hit("$documentsOffset-$it") }, documentsOffset + 5)
                return Result.success(SearchResults("snapshot", false, page, SearchPage(listOf(hit("author")), null), empty))
            }
        }
        val search = DocumentSearch("project", capabilities, this)
        search.updateQuery("saved")
        await { search.results != null }
        search.more(SearchGroup.Documents)
        await { !search.loading }
        assertEquals(listOf(listOf(0, 0, 0), listOf(5, 0, 0)), offsets)
        assertEquals(10, search.results?.documents?.hits?.size)
        assertEquals(1, search.results?.authors?.hits?.size)
        stale = true
        search.more(SearchGroup.Documents)
        await { !search.loading }
        assertNull(search.results)
        assertContains(search.error.orEmpty(), "Refresh search to restart")
        stale = false
        search.refresh()
        await { search.results != null }
        assertEquals(5, search.results?.documents?.hits?.size)
        search.dispose()
    }

    @Test
    fun preparationIsVisibleAndRefreshRepreparesWithoutSearchingUnsavedText() = runBlocking {
        val preparation = CompletableDeferred<Result<SearchPreparation>>()
        var prepares = 0
        var searches = 0
        val capabilities = object : SearchCapabilities {
            override suspend fun prepareSearch(project: String): Result<SearchPreparation> {
                prepares++
                return preparation.await()
            }
            override suspend fun searchDocuments(project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int): Result<SearchResults> {
                searches++
                return Result.success(results(query))
            }
        }
        val search = DocumentSearch("project", capabilities, this)
        search.refresh()
        await { search.preparing }
        assertNull(search.results)
        preparation.complete(Result.success(SearchPreparation("snapshot", 2)))
        await { !search.preparing }
        assertEquals(0, searches)
        search.updateQuery("saved")
        await { search.results != null }
        assertEquals(1, prepares)
        search.refresh()
        await { prepares == 2 && search.results != null }
        search.dispose()
    }

    @Test
    fun disposedProjectCannotAcceptLatePreparation() = runBlocking {
        val preparation = CompletableDeferred<Result<SearchPreparation>>()
        var searches = 0
        val capabilities = object : SearchCapabilities {
            override suspend fun prepareSearch(project: String) = withContext(NonCancellable) { preparation.await() }
            override suspend fun searchDocuments(project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int): Result<SearchResults> {
                searches++
                return Result.success(results(query))
            }
        }
        val search = DocumentSearch("old project", capabilities, this)
        search.updateQuery("saved")
        await { search.preparing }
        search.dispose()
        preparation.complete(Result.success(SearchPreparation("old", 1)))
        delay(10)
        assertNull(search.results)
        assertEquals(0, searches)
    }

    @Test
    fun errorsAreNotEmptyResultsAndPreparationCanBeRetried() = runBlocking {
        var failPreparation = true
        var failQuery = true
        val capabilities = object : SearchCapabilities {
            override suspend fun prepareSearch(project: String): Result<SearchPreparation> =
                if (failPreparation) Result.failure(IllegalArgumentException("Prepare failed"))
                else Result.success(SearchPreparation("snapshot", 0))
            override suspend fun searchDocuments(project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int): Result<SearchResults> =
                if (failQuery) Result.failure(IllegalArgumentException("Index out of date; prepare"))
                else Result.success(SearchResults("snapshot", false, empty, empty, empty))
        }
        val search = DocumentSearch("project", capabilities, this)
        search.updateQuery("saved")
        await { !search.loading }
        assertEquals("Prepare failed", search.error)
        assertNull(search.results)
        failPreparation = false
        search.refresh()
        await { !search.loading }
        assertEquals("Index out of date; prepare", search.error)
        assertNull(search.results)
        failQuery = false
        search.refresh()
        await { search.results != null }
        assertNull(search.error)
        assertTrue(search.results!!.documents.hits.isEmpty())
        search.dispose()
    }

    @Test
    fun staleInitialQueryPreparesAndRetriesOnceButStalePageNeverRetries() = runBlocking {
        var prepares = 0
        var queries = 0
        var alwaysStale = false
        var pageStale = false
        val preparation = CompletableDeferred<Result<SearchPreparation>>()
        val capabilities = object : SearchCapabilities {
            override suspend fun prepareSearch(project: String): Result<SearchPreparation> {
                prepares++
                return if (prepares == 2) preparation.await() else Result.success(SearchPreparation("snapshot", 10))
            }
            override suspend fun searchDocuments(project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int): Result<SearchResults> {
                queries++
                if (queries == 1 || alwaysStale || pageStale) return Result.failure(SearchIndexOutOfDateException("stale"))
                return Result.success(results(query).copy(documents = SearchPage(List(5) { hit("$it") }, 5)))
            }
        }
        val search = DocumentSearch("project", capabilities, this)
        search.updateQuery("saved")
        await { prepares == 2 && search.preparing }
        assertTrue(search.loading)
        preparation.complete(Result.success(SearchPreparation("snapshot", 10)))
        await { search.results != null }
        assertEquals(2, queries)
        pageStale = true
        search.more(SearchGroup.Documents)
        await { !search.loading }
        assertEquals(3, queries)
        assertEquals(2, prepares)
        assertContains(search.error.orEmpty(), "Refresh search to restart")
        assertNull(search.results)
        pageStale = false
        alwaysStale = true
        search.refresh()
        await { !search.loading }
        assertEquals(5, queries)
        assertEquals(4, prepares)
        assertEquals("stale", search.error)
        search.dispose()
    }
}
