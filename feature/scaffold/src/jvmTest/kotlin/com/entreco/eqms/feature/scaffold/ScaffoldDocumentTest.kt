package com.entreco.eqms.feature.scaffold

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.feature.document.*
import com.entreco.eqms.feature.search.*
import kotlin.test.*
import org.junit.Rule
import org.junit.Test

class ScaffoldDocumentTest {
    @get:Rule val compose = createComposeRule()

    private fun sourceField(name: String): SemanticsNodeInteraction {
        if (compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithContentDescription("View Source").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("View Source").performClick()
        }
        return compose.onNodeWithContentDescription(name)
    }

    @Test
    fun settingsProjectChooserAndAboutDoNotToggleBackgroundTools() {
        val chrome = chrome()
        val projects = Projects()
        val documents = Documents()
        compose.setContent { ProjectScreen(projects, documents, chrome = chrome) }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Policy (Draft)").performClick()
        fun sendToFocusedWindow() {
            compose.onAllNodes(isRoot()).onLast().performKeyInput {
                val modifier = if (System.getProperty("os.name").startsWith("Mac"))
                    androidx.compose.ui.input.key.Key.MetaLeft else androidx.compose.ui.input.key.Key.AltLeft
                keyDown(modifier)
                pressKey(androidx.compose.ui.input.key.Key.Two)
                keyUp(modifier)
            }
        }
        compose.runOnIdle { chrome.request(DesktopCommand.Settings) }
        compose.onNodeWithText("Close").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        sendToFocusedWindow()
        compose.onNodeWithContentDescription("Toggle Search panel").assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithContentDescription("Directory path")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        sendToFocusedWindow()
        compose.onNodeWithContentDescription("Toggle Search panel").assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("Close").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        sendToFocusedWindow()
        compose.onNodeWithContentDescription("Toggle Search panel").assertIsNotSelected()
        compose.onNodeWithText("Close").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        sourceField("Markdown body").assertTextContains("original")
    }

    private fun searchField(): SemanticsNodeInteraction {
        if (compose.onAllNodesWithContentDescription("Search saved documents").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Toggle Search panel").performClick()
        }
        return compose.onNodeWithContentDescription("Search saved documents")
    }

    private class Projects : ProjectCapabilities {
        var failOpen = false
        override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing("/project", null, emptyList()))
        override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
        override suspend fun openProject(path: String, create: Boolean): Result<String> =
            if (failOpen) Result.failure(IllegalArgumentException("Verification failed")) else Result.success(path)
    }

    private class Documents : DocumentCapabilities {
        override suspend fun transitionDocument(project: String, document: DocumentSnapshot, target: DocumentStatus) =
            Result.failure<DocumentSnapshot>(IllegalStateException("This navigation test must not transition"))
        override suspend fun documentLifecycleHistory(project: String, id: String) =
            Result.success(emptyList<DocumentLifecycleEvent>())
        val first = DocumentSnapshot("DOC-1", "Policy", "original", "first", "hash", "event", "Test", "1")
        var saved = first
        var lists = 0
        var prepares = 0
        val queries = mutableListOf<String>()
        override suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>> {
            lists++
            return Result.success(listOf(saved))
        }
        override suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot> =
            error("This navigation test must not save")
        override suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot> =
            error("This navigation test must not save")
        override suspend fun documentHistory(project: String, id: String) = Result.success(listOf(saved))
        override suspend fun documentDiff(project: String, id: String, from: String, to: String): Result<String> =
            error("This navigation test must not compare")
        override suspend fun prepareSearch(project: String): Result<SearchPreparation> {
            prepares++
            return Result.success(SearchPreparation("snapshot", 1))
        }
        override suspend fun searchDocuments(
            project: String, query: String, snapshot: String?, documentsOffset: Int, authorsOffset: Int, contentOffset: Int,
        ): Result<SearchResults> {
            queries += query
            val empty = SearchPage(emptyList(), null)
            return Result.success(SearchResults("snapshot", false, empty, empty, empty))
        }
    }

    @Test
    fun projectAndCloseNavigationWarnBeforeDiscarding() {
        val projects = Projects()
        val documents = Documents()
        var closeRequested by mutableStateOf(false)
        var closed = false
        compose.setContent {
            ProjectScreen(projects, documents, closeRequested, { closeRequested = false }, { closed = true })
        }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("New document").performClick()
        sourceField("Document title").performTextInput("Unsaved")
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Document title").assertTextContains("Unsaved")
        compose.runOnIdle { closeRequested = true }
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithContentDescription("Document tab Unsaved").performClick()
        compose.onNodeWithContentDescription("Close tab Unsaved").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        sourceField("Document title").assertTextContains("")
        compose.runOnIdle { closeRequested = true }
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test
    fun themeChangesPreserveDirtyEditorSearchAndNavigationGuard() {
        val capabilities = Documents()
        val chrome = chrome()
        compose.setContent { ProjectScreen(ProjectsRemembered(), capabilities, chrome = chrome) }
        compose.onNodeWithText("Your quality workspace").assertExists()
        compose.onNodeWithText("Light theme").assertExists()
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("keep these edits")
        searchField().performTextReplacement("policy")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        repeat(2) {
            compose.onNodeWithText(if (it == 0) "Light theme" else "Dark theme").performClick()
            compose.runOnIdle { assertEquals(it != 0, chrome.dark) }
            sourceField("Markdown body").assertTextContains("keep these edits")
            searchField().assertTextContains("policy")
            compose.onNodeWithText("Draft has unsaved edits").assertExists()
        }
        compose.runOnIdle {
            assertEquals(1, capabilities.lists)
            assertEquals(1, capabilities.prepares)
            assertEquals(listOf("policy"), capabilities.queries)
        }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Markdown body").assertTextContains("keep these edits")
    }

    @Composable
    private fun ProjectsRemembered(): ProjectCapabilities = remember { Projects() }

    @Test
    fun projectCancellationAndFailureKeepEditorButSuccessfulReopenRefreshesIt() {
        val capabilities = Documents()
        val projects = Projects()
        compose.setContent { ProjectScreen(projects, capabilities) }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("unsaved before switch")
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Markdown body").assertTextContains("unsaved before switch")
        compose.runOnIdle { projects.failOpen = true }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Verification failed").assertExists()
        sourceField("Markdown body").assertTextContains("unsaved before switch")
        compose.runOnIdle {
            assertEquals(1, capabilities.lists)
            projects.failOpen = false
            capabilities.saved = capabilities.first.copy(title = "External policy", body = "external", revision = "second")
        }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("External policy (Draft)").assertExists()
        sourceField("Markdown body").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, capabilities.lists) }
    }

    private fun chrome() = DesktopChrome(
        "1.0.0-dev", Result.success(true), { Result.success(Unit) },
        { Result.success(ReleaseCheck("No published stable releases are available.")) },
        { Result.success(Unit) },
    )

    @Test
    fun aboutWindowKeepsEditorUsableAndApplicationExitGuarded() {
        val chrome = chrome()
        val projects = Projects()
        val documents = Documents()
        var closeRequested by mutableStateOf(false)
        var exited = false
        compose.setContent {
            ProjectScreen(
                projects, documents, closeRequested,
                onCloseHandled = { closeRequested = false },
                onCloseConfirmed = { exited = true }, chrome = chrome,
            )
        }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertIsDisplayed()
        sourceField("Markdown body")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetText) {
                it(androidx.compose.ui.text.AnnotatedString("edit while About is open"))
            }
        sourceField("Markdown body").assertTextContains("edit while About is open")
        compose.runOnIdle { closeRequested = true }
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Markdown body").assertTextContains("edit while About is open")
        compose.runOnIdle { assertFalse(exited) }
    }

    @Test
    fun closeProjectMenuGuardsDirtyStateAndDoesNotQuit() {
        val chrome = chrome()
        var quit = false
        compose.setContent { ProjectScreen(Projects(), Documents(), onCloseConfirmed = { quit = true }, chrome = chrome) }
        compose.runOnIdle {
            assertFalse(chrome.hasProject)
            chrome.request(DesktopCommand.CloseProject)
        }
        compose.onNodeWithText("Your quality workspace").assertExists()
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("keep edits")
        compose.runOnIdle { chrome.request(DesktopCommand.CloseProject) }
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.runOnIdle {
            assertFalse(chrome.enabled)
            chrome.request(DesktopCommand.NewProject)
        }
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Markdown body").assertTextContains("keep edits")
        compose.runOnIdle { chrome.request(DesktopCommand.CloseProject) }
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("Your quality workspace").assertExists()
        sourceField("Markdown body").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(chrome.hasProject)
            assertFalse(quit)
        }
    }

    @Test
    fun menuNewReusesCreateDialogAndSettingsPreserveDirtySearch() {
        val chrome = chrome()
        val documents = Documents()
        compose.setContent { ProjectScreen(Projects(), documents, chrome = chrome) }
        compose.runOnIdle { chrome.request(DesktopCommand.NewProject) }
        compose.onNodeWithText("New folder").assertExists()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project created successfully.").assertExists()
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("keep edits")
        searchField().performTextReplacement("policy")
        compose.waitUntil(3000) { documents.queries.isNotEmpty() }
        compose.runOnIdle { chrome.request(DesktopCommand.Settings) }
        compose.onNodeWithText("Use light appearance").performClick()
        compose.onNodeWithText("Appearance: Light").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Dark theme").assertExists()
        sourceField("Markdown body").assertTextContains("keep edits")
        searchField().assertTextContains("policy")
        compose.runOnIdle {
            assertEquals(1, documents.lists)
            assertEquals(1, documents.prepares)
        }
    }
}
