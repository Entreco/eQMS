package com.entreco.eqms.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        var saves = 0
        var pending: CompletableDeferred<Result<DocumentSnapshot>>? = null
        override suspend fun listDocuments(project: String) = Result.success(listOf(saved))
        override suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot> =
            saveDocument(project, first.id, first.revision, title, body)
        override suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot> {
            saves++
            pending?.let { return it.await() }
            if (fail) return Result.failure(IllegalArgumentException("Conflict: edits not saved"))
            saved = first.copy(title = title, body = body, revision = "second")
            return Result.success(saved)
        }
        override suspend fun documentHistory(project: String, id: String) = Result.success(listOf(saved, first))
        override suspend fun documentDiff(project: String, id: String, from: String, to: String) = Result.success("-original\n+edited")
    }

    @Test
    fun editsSurviveFailedSaveAndCanBeSavedAndCompared() {
        val capabilities = Capabilities()
        var dirty = false
        compose.setContent {
            MaterialTheme {
                DocumentScreen("/project", capabilities, { it() }, { value, _ -> dirty = value })
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithText("Markdown body").performTextReplacement("edited")
        compose.runOnIdle { assertTrue(dirty); capabilities.fail = true }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Conflict: edits not saved").assertExists()
        compose.onNodeWithText("Markdown body").assertTextContains("edited")
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
        compose.setContent { MaterialTheme { DocumentScreen("/project", capabilities, { it() }, { _, _ -> }) } }
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Document title").performTextInput("New policy")
        compose.runOnIdle { assertEquals(0, capabilities.saves) }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("New document").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, capabilities.saves); pending.complete(Result.success(capabilities.first)) }
        compose.onNodeWithText("Draft saved.").assertExists()
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
        compose.onNodeWithText("Document title").performTextInput("Unsaved")
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Document title").assertTextContains("Unsaved")
        compose.runOnIdle { closeRequested = true }
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("Document title").assertTextContains("")
        compose.runOnIdle { closeRequested = true }
        compose.runOnIdle { assertTrue(closed) }
    }

    @Composable
    private fun CapabilitiesRemembered(): DocumentCapabilities = remember { Capabilities() }
}
