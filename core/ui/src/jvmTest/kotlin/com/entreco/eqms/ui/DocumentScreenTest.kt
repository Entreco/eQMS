package com.entreco.eqms.ui

import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class DocumentScreenTest {
    @get:Rule val compose = createComposeRule()

    private class Capabilities : DocumentCapabilities {
        val first = DocumentSnapshot("DOC-1", "Policy", "original", "first", "hash", "event", "Test", "1")
        var saved = first
        var fail = false
        var diffFails = false
        var saves = 0
        var prepares = 0
        val queries = mutableListOf<String>()
        var searchResult = SearchResults("snapshot", false, SearchPage(emptyList(), null), SearchPage(emptyList(), null), SearchPage(emptyList(), null))
        val reads = mutableListOf<Pair<String, String>>()
        val latestReads = mutableListOf<String>()
        var lists = 0
        var pending: CompletableDeferred<Result<DocumentSnapshot>>? = null
        override suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>> {
            lists++
            return Result.success(listOf(saved))
        }
        override suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot> =
            saveDocument(project, first.id, first.revision, title, body)
        override suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot> {
            saves++
            pending?.let { return it.await() }
            if (fail) return Result.failure(IllegalArgumentException("Conflict: edits not saved"))
            if (expected != saved.revision) return Result.failure(IllegalArgumentException("Conflict: draft changed"))
            saved = first.copy(title = title, body = body, revision = "second")
            return Result.success(saved)
        }
        override suspend fun documentHistory(project: String, id: String) = Result.success(listOf(saved, first))
        override suspend fun documentDiff(project: String, id: String, from: String, to: String): Result<String> =
            if (diffFails) Result.failure(IllegalArgumentException("Comparison unavailable"))
            else Result.success("-original\n+edited")
        override suspend fun prepareSearch(project: String): Result<SearchPreparation> {
            prepares++
            return Result.success(SearchPreparation("snapshot", 2))
        }
        override suspend fun searchDocuments(project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int): Result<SearchResults> {
            queries += query
            return Result.success(searchResult)
        }
        override suspend fun showDocument(project: String, id: String, revision: String): Result<DocumentSnapshot> {
            reads += id to revision
            return super.showDocument(project, id, revision)
        }
        override suspend fun showLatestDocument(project: String, id: String): Result<DocumentSnapshot> {
            latestReads += id
            return super.showLatestDocument(project, id)
        }
    }

    @Test
    fun editsSurviveFailedSaveAndCanBeSavedAndCompared() {
        val capabilities = Capabilities()
        var dirty = false
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { it() }, { value, _ -> dirty = value })
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithContentDescription("Markdown body").performTextReplacement("edited")
        compose.runOnIdle { assertTrue(dirty); capabilities.fail = true }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Conflict: edits not saved").assertExists()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("edited")
        compose.runOnIdle { assertTrue(dirty); capabilities.fail = false }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Draft saved.").assertExists()
        compose.runOnIdle { assertFalse(dirty) }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Draft history").assertExists()
        compose.onNodeWithText("Compare").performClick()
        compose.onNodeWithText("-original\n+edited").assertExists()
    }

    @Test
    fun newDocumentDoesNotWriteBeforeSaveAndBusySaveCannotRepeat() {
        val capabilities = Capabilities()
        val pending = CompletableDeferred<Result<DocumentSnapshot>>()
        capabilities.pending = pending
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { it() }, { _, _ -> }) } }
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Document title").performTextInput("New policy")
        compose.runOnIdle { assertEquals(0, capabilities.saves) }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("New document").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, capabilities.saves); pending.complete(Result.success(capabilities.first)) }
        compose.onNodeWithText("Draft saved.").assertExists()
        compose.waitUntil(3000) { capabilities.prepares == 2 }
        compose.onNodeWithText("Refresh documents").performClick()
        compose.waitUntil(3000) { capabilities.prepares == 3 }
    }

    @Test
    fun historicalSearchGuardsDirtyNavigationAndCannotOverwriteLatest() {
        val capabilities = Capabilities()
        capabilities.saved = capabilities.first.copy(title = "Latest policy", body = "latest", revision = "second")
        val hit = SearchHit(capabilities.first.id, "Policy", "first", "Test", "1", "original")
        capabilities.searchResult = capabilities.searchResult.copy(
            historical = true, documents = SearchPage(listOf(hit), null),
        )
        var allowNavigation = false
        var guarded = false
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, {
                    if (allowNavigation) it() else guarded = true
                }, { _, _ -> })
            }
        }
        compose.runOnIdle { allowNavigation = true }
        compose.onNodeWithText("Latest policy (Draft)").performClick()
        compose.onNodeWithContentDescription("Markdown body").performTextReplacement("unsaved latest")
        compose.runOnIdle { allowNavigation = false }
        compose.onNodeWithContentDescription("Search saved drafts").performTextReplacement("original")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        compose.onNodeWithText("Draft/history matches", substring = true).assertExists()
        compose.onNodeWithText("Policy (Draft/history)", substring = true).performClick()
        compose.runOnIdle {
            assertTrue(guarded)
            assertTrue(capabilities.reads.isEmpty())
            assertEquals(listOf("original"), capabilities.queries)
            allowNavigation = true
        }
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("unsaved latest")
        compose.onNodeWithText("Policy (Draft/history)", substring = true).performClick()
        compose.onNodeWithText("Draft/history — read-only saved revision").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("original")
        compose.runOnIdle {
            assertEquals(listOf(capabilities.first.id to "first"), capabilities.reads)
            assertEquals(0, capabilities.saves)
            assertEquals("latest", capabilities.saved.body)
        }
        compose.onNodeWithText("Return to latest draft").performClick()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("latest")
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithContentDescription("Search saved drafts").performTextClearance()
        compose.onNodeWithText("Latest policy (Draft)").assertExists()
    }

    @Test
    fun currentSearchOpensLatestAndSaveRefreshesPreparation() {
        val capabilities = Capabilities()
        val timings = mutableListOf<SearchRenderTiming>()
        val hit = SearchHit(capabilities.first.id, "Policy", "first", "Test", "1", "saved excerpt")
        capabilities.searchResult = capabilities.searchResult.copy(documents = SearchPage(listOf(hit), null))
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { it() }, { _, _ -> }, onSearchResultsLaidOut = { timings += it })
            }
        }
        compose.onNodeWithContentDescription("Search saved drafts").performTextReplacement("policy")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        compose.onNodeWithText("Policy", substring = false).performClick()
        compose.runOnIdle {
            assertEquals("policy", timings.single().query)
            assertFalse(timings.single().historical)
            assertTrue(timings.single().elapsedMilliseconds >= 0)
            assertEquals(1, capabilities.prepares)
            assertEquals(listOf(capabilities.first.id), capabilities.latestReads)
        }
        compose.onNodeWithContentDescription("Markdown body").performTextReplacement("unsaved text")
        compose.runOnIdle { assertEquals(listOf("policy"), capabilities.queries) }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Draft saved.").assertExists()
        compose.waitUntil(3000) { capabilities.prepares == 2 }
        compose.runOnIdle { assertEquals("unsaved text", capabilities.saved.body) }
    }

    @Test
    fun currentHitReadsOnlyLatestAndStaleSaveRetainsEdits() {
        val capabilities = Capabilities()
        val hit = SearchHit(capabilities.first.id, "Policy", "first", "Test", "1", "saved excerpt")
        capabilities.searchResult = capabilities.searchResult.copy(documents = SearchPage(listOf(hit), null))
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { it() }, { _, _ -> }) } }
        compose.onNodeWithContentDescription("Search saved drafts").performTextReplacement("policy")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        compose.runOnIdle {
            capabilities.saved = capabilities.first.copy(revision = "second", body = "external update")
        }
        compose.onNodeWithText("Policy", substring = false).performClick()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("external update")
        compose.runOnIdle {
            capabilities.saved = capabilities.saved.copy(revision = "third", body = "newer external update")
        }
        compose.onNodeWithContentDescription("Markdown body").performTextReplacement("my edit")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Conflict: draft changed").assertExists()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("my edit")
        compose.runOnIdle {
            assertEquals(1, capabilities.lists)
            assertEquals(listOf(capabilities.first.id), capabilities.latestReads)
            assertTrue(capabilities.reads.isEmpty())
            assertEquals("newer external update", capabilities.saved.body)
        }
    }

    @Test
    fun groupedCurrentRowsAreConciseAndLazyPaginationCanBeScrolled() {
        val capabilities = Capabilities()
        val hits = List(20) {
            SearchHit("DOC-$it", "Title $it", "full-revision-$it", "Author $it", "time $it", "Excerpt $it")
        }
        capabilities.searchResult = capabilities.searchResult.copy(
            documents = SearchPage(hits, null),
            authors = SearchPage(listOf(hits.first()), null),
            content = SearchPage(listOf(hits.first()), 5),
        )
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { it() }, { _, _ -> }) } }
        compose.onNodeWithContentDescription("Search saved drafts").performTextReplacement("title")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        compose.onNodeWithText("Title 0", substring = false).assertExists()
        compose.onNodeWithText("Title 19", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Revision: full-revision-0", substring = true).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Title 0 | by Author 0"))
        compose.onNodeWithText("Title 0 | by Author 0").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Title 0 | Excerpt 0"))
        compose.onNodeWithText("Title 0 | Excerpt 0").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Show more Content"))
        compose.onNodeWithText("Show more Content").assertExists()
    }

    @Test
    fun layoutTimingReportsExactlyOnceForEachMatchingQueryGeneration() {
        val capabilities = Capabilities()
        val timings = mutableListOf<SearchRenderTiming>()
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { it() }, { _, _ -> }, onSearchResultsLaidOut = { timings += it })
            }
        }
        val queries = listOf("And", "risk", "And", "trace", "risk")
        queries.forEachIndexed { index, query ->
            compose.onNodeWithContentDescription("Search saved drafts").performTextReplacement(query)
            compose.waitUntil(3000) { timings.size > index }
            compose.runOnIdle {
                assertEquals(index + 1, timings.size)
                assertEquals(query, timings[index].query)
                assertEquals(timings.size, timings.map { it.generation }.distinct().size)
                assertEquals(queries.take(index + 1), timings.map { it.query })
            }
        }
    }

    @Test
    fun projectAndCloseNavigationWarnBeforeDiscarding() {
        val projects = object : ProjectCapabilities {
            override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing("/project", null, emptyList()))
            override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
            override suspend fun openProject(path: String, create: Boolean) = Result.success(path)
        }
        var closeRequested by mutableStateOf(false)
        var closed = false
        compose.setContent {
            ProjectScreen(projects, CapabilitiesRemembered(), closeRequested, { closeRequested = false }, { closed = true })
        }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithContentDescription("Document title").performTextInput("Unsaved")
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("Document title").assertTextContains("Unsaved")
        compose.runOnIdle { closeRequested = true }
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithContentDescription("Document title").assertTextContains("")
        compose.runOnIdle { closeRequested = true }
        compose.runOnIdle { assertTrue(closed) }
    }

    @Composable
    private fun CapabilitiesRemembered(): DocumentCapabilities = remember { Capabilities() }

    @Test
    fun themeChangesPreserveDirtyEditorSearchAndNavigationGuard() {
        val capabilities = Capabilities()
        val projects = object : ProjectCapabilities {
            override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing("/project", null, emptyList()))
            override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
            override suspend fun openProject(path: String, create: Boolean) = Result.success(path)
        }
        compose.setContent { ProjectScreen(projects, capabilities) }
        compose.onNodeWithText("Your quality workspace").assertExists()
        compose.onNodeWithText("Light theme").assertExists()
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithContentDescription("Markdown body").performTextReplacement("keep these edits")
        compose.onNodeWithContentDescription("Search saved drafts").performTextReplacement("policy")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        repeat(2) {
            compose.onNodeWithText(if (it == 0) "Light theme" else "Dark theme").performClick()
            compose.onNodeWithContentDescription("Markdown body").assertTextContains("keep these edits")
            compose.onNodeWithContentDescription("Search saved drafts").assertTextContains("policy")
            compose.onNodeWithText("Draft has unsaved edits").assertExists()
        }
        compose.runOnIdle {
            assertEquals(1, capabilities.lists)
            assertEquals(1, capabilities.prepares)
            assertEquals(listOf("policy"), capabilities.queries)
        }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("keep these edits")
    }

    @Test
    fun comparisonErrorsAreVisibleAndClosingPanelRestoresEditor() {
        val capabilities = Capabilities().apply { diffFails = true }
        compose.setContent {
            IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { it() }, { _, _ -> }) }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Compare").performClick()
        compose.onNodeWithText("Comparison unavailable").assertExists()
        compose.onNodeWithText("New document").assertIsNotEnabled()
        compose.onNodeWithText("Close history").performClick()
        compose.onNodeWithText("New document").assertIsEnabled()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("original")
    }

    @Test
    fun documentRowsSupportKeyboardFocusAndActivation() {
        val capabilities = Capabilities()
        compose.setContent {
            IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { it() }, { _, _ -> }) }
        }
        compose.onNodeWithText("Policy (Draft)").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Policy (Draft)").assertIsFocused()
        compose.onNodeWithText("Policy (Draft)").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("original")
        compose.onNodeWithText("Policy (Draft)").assertIsSelected()
    }

    @Test
    fun projectCancellationAndFailureKeepEditorButSuccessfulReopenRefreshesIt() {
        val capabilities = Capabilities()
        var failOpen = false
        val projects = object : ProjectCapabilities {
            override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing("/project", null, emptyList()))
            override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
            override suspend fun openProject(path: String, create: Boolean): Result<String> =
                if (failOpen) Result.failure(IllegalArgumentException("Verification failed")) else Result.success(path)
        }
        compose.setContent { ProjectScreen(projects, capabilities) }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("original")
        compose.runOnIdle { failOpen = true }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Verification failed").assertExists()
        compose.onNodeWithContentDescription("Markdown body").assertTextContains("original")
        compose.runOnIdle {
            assertEquals(1, capabilities.lists)
            failOpen = false
            capabilities.saved = capabilities.first.copy(title = "External policy", body = "external", revision = "second")
        }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("External policy (Draft)").assertExists()
        compose.onNodeWithContentDescription("Markdown body").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, capabilities.lists) }
    }
}
