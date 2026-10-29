package com.entreco.eqms.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

class SearchPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun injectedDocumentsGroupedSelectionPaginationAndHistoryStayEditorIndependent() {
        val hit = SearchHit("DOC-1", "Policy", "first", "Test", "1", "risk controls")
        val next = hit.copy(id = "DOC-2", title = "Second policy")
        val selections = mutableListOf<Pair<SearchHit, Boolean>>()
        val timings = mutableListOf<SearchRenderTiming>()
        val offsets = mutableListOf<Int>()
        val capabilities = object : SearchCapabilities {
            override suspend fun prepareSearch(project: String) = Result.success(SearchPreparation("snapshot", 2))
            override suspend fun searchDocuments(
                project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int,
            ): Result<SearchResults> {
                offsets += documentsOffset
                return Result.success(SearchResults(
                    "snapshot", query == "older",
                    SearchPage(listOf(if (documentsOffset == 0) hit else next), if (documentsOffset == 0) 1 else null),
                    SearchPage(emptyList(), null), SearchPage(emptyList(), null),
                ))
            }
        }
        compose.setContent {
            IntUiTheme(isDark = true) {
                val scope = rememberCoroutineScope()
                val search = remember { DocumentSearch("/project", capabilities, scope) }
                var selected by remember { mutableStateOf<SearchHit?>(null) }
                DisposableEffect(search) { onDispose { search.dispose() } }
                SearchPanel(
                    search, enabled = true,
                    onSelect = { row, history -> selected = row; selections += row to history },
                    isSelected = { row, _ -> selected == row },
                    onResultsLaidOut = { timings += it },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Text("Injected document list") }
                }
            }
        }
        compose.onNodeWithText("Injected document list").assertExists()
        compose.onNodeWithContentDescription("Search saved documents").performTextReplacement("policy")
        compose.waitUntil(3000) { timings.isNotEmpty() }
        compose.onNodeWithText("Documents").assertExists()
        compose.onNodeWithText("Authors").assertExists()
        compose.onNodeWithText("Content").assertExists()
        compose.onNodeWithText("Policy").performClick()
        compose.onNodeWithText("Policy").assertIsSelected()
        compose.onNodeWithText("Show more Documents").performClick()
        compose.waitUntil(3000) { offsets.contains(1) }
        compose.onNodeWithText("Second policy").assertExists()
        compose.onNodeWithContentDescription("Search saved documents").performTextReplacement("older")
        compose.waitUntil(3000) { timings.size == 2 }
        compose.onNodeWithText("Policy (Saved revision)", substring = true).performClick()
        compose.runOnIdle {
            assertEquals(listOf(hit to false, hit to true), selections)
            assertEquals(listOf("policy", "older"), timings.map { it.query })
            assertEquals(listOf(0, 1, 0), offsets)
        }
        compose.onNodeWithContentDescription("Search saved documents").performTextClearance()
        compose.onNodeWithText("Injected document list").assertExists()
    }
}
