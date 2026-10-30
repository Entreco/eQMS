package com.entreco.eqms.feature.document

import com.entreco.eqms.feature.search.*

import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class DocumentScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun sourceField(name: String): SemanticsNodeInteraction {
        if (compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithContentDescription("View Source").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("View Source").performClick()
        }
        return compose.onNodeWithContentDescription(name)
    }

    @Test
    fun renderedTablesFillAvailableWidthAndKeepWrappedRowCellsAligned() {
        fun cell(text: String) = RenderNode("table_cell", children = listOf(RenderNode("text", text)))
        val capabilities = Capabilities().apply {
            renderNodes = listOf(RenderNode("table", children = listOf(
                RenderNode("table_header", children = listOf(cell("Role"), cell("Activity"))),
                RenderNode("table_row", children = listOf(cell("Reviewer"),
                    cell("Inspect the saved revision and all supporting documentation before approving this document."))),
            )))
        }
        compose.setContent {
            IntUiTheme(isDark = true) {
                Box(Modifier.requiredSize(1000.dp, 720.dp)) {
                    DocumentScreen("/project", capabilities, { _, _ -> })
                }
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithContentDescription("Toggle Project panel").performClick()
        compose.onNodeWithContentDescription("Toggle Review panel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Role").fetchSemanticsNodes().isNotEmpty() }
        val table = compose.onNodeWithContentDescription("Rendered table with 2 columns").fetchSemanticsNode().boundsInRoot
        val role = compose.onNodeWithText("Role").fetchSemanticsNode().boundsInRoot
        val activity = compose.onNodeWithText("Activity").fetchSemanticsNode().boundsInRoot
        assertTrue(role.width > 300f)
        assertTrue(abs(role.width - activity.width) < 1f)
        assertTrue(abs(activity.right - role.left + 20f - table.width) < 1f)
        val reviewer = compose.onNodeWithText("Reviewer").fetchSemanticsNode().boundsInRoot
        val wrapped = compose.onNodeWithText("Inspect the saved revision", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(reviewer.height - wrapped.height) < 1f)
    }

    @Test
    fun roundedPanelCornersExposeShellAndFillsRemainVisiblyDistinctInBothThemes() {
        var dark by mutableStateOf(true)
        val capabilities = Capabilities()
        compose.setContent {
            IntUiTheme(isDark = dark) {
                DocumentScreen("/project", capabilities, { _, _ -> })
            }
        }
        for (theme in listOf(true, false)) {
            compose.runOnIdle { dark = theme }
            for (name in listOf("Project panel", "Review panel", "Document editor panel")) {
                val pixels = compose.onNodeWithContentDescription(name).captureToImage().toPixelMap()
                val corner = pixels[0, 0]
                val fill = pixels[pixels.width / 2, pixels.height - 20]
                assertTrue(abs(fill.red - corner.red) >= 0.025f,
                    "$name in dark=$theme must expose a contrasting rounded cutout: $corner vs $fill")
                val opposite = pixels[pixels.width - 1, pixels.height - 1]
                assertEquals(corner, opposite, "Opposite rounded cutouts must expose the same shell")
            }
        }
    }

    @Test
    fun bothSplittersDragResizeToolsAndEditorWithinBoundsAndDisableWhileBusy() {
        val capabilities = Capabilities()
        var busy by mutableStateOf(false)
        compose.setContent {
            IntUiTheme(isDark = true) {
                Box(Modifier.requiredSize(1100.dp, 800.dp)) {
                    DocumentScreen("/project", capabilities, { _, _ -> }, workspaceBusy = busy)
                }
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("keep during resize")
        fun width(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot.width
        fun drag(label: String, delta: Float) {
            compose.onNodeWithContentDescription(label).performMouseInput {
                moveTo(center); press(); moveTo(center + androidx.compose.ui.geometry.Offset(delta, 0f), 500); release()
            }
        }
        val project = width("Project panel")
        val editor = width("Document editor panel")
        drag("Resize left panel", -60f)
        assertTrue(width("Project panel") < project - 40)
        assertTrue(width("Document editor panel") > editor + 40)
        val review = width("Review panel")
        val widerEditor = width("Document editor panel")
        drag("Resize right panel", 60f)
        assertTrue(width("Review panel") < review - 40)
        assertTrue(width("Document editor panel") > widerEditor + 40)
        drag("Resize left panel", -500f)
        compose.onNodeWithContentDescription("Project panel").assertWidthIsEqualTo(140.dp)
        drag("Resize right panel", -500f)
        assertTrue(width("Review panel") <= 297.5f)
        assertTrue(width("Document editor panel") >= 300f)
        compose.runOnIdle { busy = true }
        val leftBefore = width("Project panel")
        val rightBefore = width("Review panel")
        drag("Resize left panel", 60f)
        drag("Resize right panel", 60f)
        assertEquals(leftBefore, width("Project panel"))
        assertEquals(rightBefore, width("Review panel"))
        sourceField("Markdown body").assertTextContains("keep during resize")
    }

    private fun shortcut(number: Key) {
        compose.onRoot().performKeyInput {
            val modifier = if (System.getProperty("os.name").startsWith("Mac")) Key.MetaLeft else Key.AltLeft
            keyDown(modifier)
            pressKey(number)
            keyUp(modifier)
        }
    }

    @Test
    fun selectedTabIsRevealedAfterOpeningAndReturningAcrossOverflow() {
        val capabilities = Capabilities()
        compose.setContent {
            IntUiTheme(isDark = true) {
                Box(Modifier.requiredSize(960.dp, 720.dp)) {
                    DocumentScreen("/project", capabilities, { _, _ -> })
                }
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        repeat(5) { number ->
            compose.onNodeWithText("New document").performClick()
            sourceField("Document title").performTextReplacement("Long document title number $number")
        }
        compose.onNodeWithContentDescription("Document tab Long document title number 4").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithContentDescription("Document tab Policy").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun panelShortcutsRetainDirtyEditorCaretAndOperateFromTitleAndSearch() {
        val capabilities = Capabilities()
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        compose.onNodeWithText("Policy (Draft)").performClick()
        val body = sourceField("Markdown body")
        body.performTextReplacement("unsaved body")
        body.performTextInputSelection(TextRange(3))
        val before = body.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        for ((key, name) in listOf(Key.One to "Project", Key.Two to "Search", Key.Three to "Review", Key.Four to "History")) {
            val button = compose.onNodeWithContentDescription("Toggle $name panel")
            shortcut(key)
            if (name == "Project" || name == "Review") button.assertIsNotSelected() else button.assertIsSelected()
            body.assertIsFocused().assertTextContains("unsaved body")
            assertEquals(before, body.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        }
        sourceField("Document title").performClick()
        shortcut(Key.Three)
        compose.onNodeWithContentDescription("Toggle Review panel").assertIsSelected()
        sourceField("Document title").assertIsFocused()
        compose.onNodeWithContentDescription("Search saved documents").performTextReplacement("retained query")
        shortcut(Key.Two)
        compose.onNodeWithContentDescription("Toggle Search panel").assertIsFocused().assertIsNotSelected()
        shortcut(Key.Two)
        compose.onNodeWithContentDescription("Search saved documents").assertTextContains("retained query")
        body.assertTextContains("unsaved body")
        compose.runOnIdle { assertEquals(0, capabilities.saves) }
    }

    @Test
    fun workspaceBusyAndLifecycleAndUnsavedDialogsBlockPanelShortcuts() {
        val capabilities = Capabilities()
        var workspaceBusy by mutableStateOf(false)
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { _, _ -> }, workspaceBusy = workspaceBusy)
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performClick()
        compose.runOnIdle { workspaceBusy = true }
        shortcut(Key.One)
        compose.onNodeWithContentDescription("Toggle Project panel").assertIsSelected().assertIsNotEnabled()
        compose.runOnIdle { workspaceBusy = false }
        compose.onNodeWithText("Submit for review").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onAllNodes(isRoot()).onLast().performKeyInput {
            val modifier = if (System.getProperty("os.name").startsWith("Mac")) Key.MetaLeft else Key.AltLeft
            keyDown(modifier); pressKey(Key.One); keyUp(modifier)
        }
        compose.onNodeWithContentDescription("Toggle Project panel").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Markdown body").performTextReplacement("dirty")
        compose.onNodeWithContentDescription("Close tab Policy").performClick()
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onAllNodes(isRoot()).onLast().performKeyInput {
            val modifier = if (System.getProperty("os.name").startsWith("Mac")) Key.MetaLeft else Key.AltLeft
            keyDown(modifier); pressKey(Key.Four); keyUp(modifier)
        }
        compose.onNodeWithContentDescription("Toggle Review panel").assertIsSelected()
        compose.onNodeWithContentDescription("Toggle History panel").assertIsNotSelected()
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Markdown body").assertTextContains("dirty")
    }

    private fun searchField(): SemanticsNodeInteraction {
        if (compose.onAllNodesWithContentDescription("Search saved documents").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Toggle Search panel").performClick()
        }
        return compose.onNodeWithContentDescription("Search saved documents")
    }

    private class Capabilities : DocumentCapabilities {
        var renderPending: CompletableDeferred<Result<RenderedDocument>>? = null
        var historyPending: CompletableDeferred<Result<DocumentHistorySnapshot>>? = null
        var renderFailure: String? = null
        var renderNodes: List<RenderNode>? = null
        var renderToc: List<RenderHeading> = emptyList()
        var imageReads = 0
        var openedLinks = 0
        override suspend fun readDocumentImage(project: String, asset: String): Result<DocumentImage> {
            imageReads++
            return Result.failure(IllegalArgumentException("Missing test image"))
        }
        override suspend fun openDocumentLink(target: String): Result<Unit> {
            openedLinks++
            return Result.success(Unit)
        }
        private fun rendered(title: String, body: String, source: RenderSource) =
            RenderedDocument(title, body, source,
                renderNodes ?: listOf(RenderNode("paragraph", children = listOf(RenderNode("text", body)))),
                renderToc, emptyList())
        override suspend fun renderBuffer(project: String, title: String, body: String): Result<RenderedDocument> {
            renderPending?.let { return it.await() }
            renderFailure?.let { return Result.failure(IllegalArgumentException(it)) }
            return Result.success(rendered(title, body, RenderSource("unsaved", "", "", "")))
        }
        override suspend fun renderSaved(project: String, document: DocumentSnapshot): Result<RenderedDocument> {
            renderPending?.let { return it.await() }
            renderFailure?.let { return Result.failure(IllegalArgumentException(it)) }
            return Result.success(rendered(document.title, document.body,
                RenderSource("saved", document.id, document.revision, document.contentHash)))
        }
        var transitionFails = false
        var transitions = 0
        override suspend fun transitionDocument(project: String, document: DocumentSnapshot, target: DocumentStatus): Result<DocumentSnapshot> {
            transitions++
            if (transitionFails) return Result.failure(IllegalStateException("Stale lifecycle request"))
            saved = document.copy(status = target, revision = "transition-$transitions")
            return Result.success(saved)
        }
        override suspend fun documentLifecycleHistory(project: String, id: String) =
            Result.success(emptyList<DocumentLifecycleEvent>())
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
        var listPending: CompletableDeferred<Result<List<DocumentSnapshot>>>? = null
        override suspend fun listDocuments(project: String): Result<List<DocumentSnapshot>> {
            lists++
            return listPending?.await() ?: Result.success(listOf(saved))
        }
        override suspend fun createDocument(project: String, title: String, body: String): Result<DocumentSnapshot> {
            saves++
            pending?.let { return it.await() }
            if (fail) return Result.failure(IllegalArgumentException("Conflict: edits not saved"))
            return Result.success(first.copy(id = "DOC-new-$saves", title = title, body = body, revision = "created"))
        }
        override suspend fun saveDocument(project: String, id: String, expected: String, title: String, body: String): Result<DocumentSnapshot> {
            saves++
            pending?.let { return it.await() }
            if (fail) return Result.failure(IllegalArgumentException("Conflict: edits not saved"))
            if (expected != saved.revision) return Result.failure(IllegalArgumentException("Conflict: draft changed"))
            saved = first.copy(title = title, body = body, revision = "second")
            return Result.success(saved)
        }
        override suspend fun documentHistory(project: String, id: String) = Result.success(listOf(saved, first))
        override suspend fun documentHistorySnapshot(project: String, id: String) =
            historyPending?.await() ?: Result.success(DocumentHistorySnapshot(listOf(saved, first), emptyList()))
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
    fun previewIsDefaultAndModesRetainUnsavedSourceCaretAndIndependentTabs() {
        val capabilities = Capabilities()
        compose.setContent {
            IntUiTheme(isDark = true) {
                Box(Modifier.requiredSize(1100.dp, 800.dp)) {
                    DocumentScreen("/project", capabilities, { _, _ -> })
                }
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithContentDescription("View Preview").assertIsSelected()
        compose.onNodeWithContentDescription("Markdown body").assertDoesNotExist()
        compose.waitUntil { compose.onAllNodesWithText("original").fetchSemanticsNodes().isNotEmpty() }
        sourceField("Markdown body").performTextReplacement("unsaved preview text")
        sourceField("Markdown body").performTextInputSelection(TextRange(5))
        compose.onNodeWithContentDescription("View Split").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Unsaved preview - not a saved or approved record").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Resize source and preview; arrow keys adjust").assertExists()
        compose.onNodeWithContentDescription("View Preview").performClick()
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithContentDescription("View Preview").assertIsSelected()
        sourceField("Document title").performTextReplacement("Other")
        compose.onNodeWithContentDescription("Document tab Policy").performClick()
        compose.onNodeWithContentDescription("View Preview").assertIsSelected()
        sourceField("Markdown body").assertTextContains("unsaved preview text")
        assertEquals(TextRange(5), sourceField("Markdown body").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        compose.runOnIdle { assertEquals(0, capabilities.saves) }
    }

    @Test
    fun lateRenderCannotReplaceAnotherTabAndFailureLeavesSourceAvailable() {
        val capabilities = Capabilities()
        val pending = CompletableDeferred<Result<RenderedDocument>>()
        capabilities.renderPending = pending
        compose.setContent { IntUiTheme(isDark = false) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithText("Rendering document...").assertExists()
        compose.onNodeWithText("New document").performClick()
        sourceField("Document title").performTextReplacement("Other")
        compose.runOnIdle {
            pending.complete(Result.success(RenderedDocument("Wrong old result", "", RenderSource("saved", "", "", ""),
                emptyList(), emptyList(), emptyList())))
            capabilities.renderPending = null
            capabilities.renderFailure = "Preview limit exceeded"
        }
        compose.onNodeWithContentDescription("View Preview").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Preview limit exceeded").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Wrong old result").assertDoesNotExist()
        sourceField("Document title").assertTextContains("Other")
        sourceField("Markdown body").performTextReplacement("still editable")
        compose.runOnIdle { assertEquals(0, capabilities.saves) }
    }

    @Test
    fun historicalAssetsNeverReadCurrentFilesAndMissingCurrentImagesKeepAltText() {
        val capabilities = Capabilities()
        capabilities.renderNodes = listOf(RenderNode("image", target = "assets/sample.png",
            children = listOf(RenderNode("text", "Synthetic illustration"))))
        var tab by mutableStateOf(DocumentTab(1, capabilities.first, historical = true))
        var dark by mutableStateOf(true)
        compose.setContent {
            IntUiTheme(isDark = dark) {
                DocumentPresentation("/project", tab, capabilities, true, remember { DocumentBitmapCache() }, {},
                    Modifier.requiredSize(700.dp, 600.dp))
            }
        }
        compose.waitUntil {
            compose.onAllNodesWithText("Synthetic illustration: Historical image unavailable: assets are not versioned.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { dark = false; assertEquals(0, capabilities.imageReads) }
        compose.onNodeWithText("Synthetic illustration: Historical image unavailable: assets are not versioned.").assertExists()
        compose.runOnIdle { tab = DocumentTab(2, capabilities.first) }
        compose.waitUntil { compose.onAllNodesWithText("Synthetic illustration: Missing test image").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(1, capabilities.imageReads) }
    }

    @Test
    fun sourcePreviewSplitResizesByPointerAndKeyboardAndHonorsBusyGuard() {
        val capabilities = Capabilities()
        val tab = DocumentTab(1, capabilities.first).apply { viewMode = DocumentViewMode.Split }
        var interactive by mutableStateOf(true)
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentPresentation("/project", tab, capabilities, interactive, remember { DocumentBitmapCache() }, {},
                    Modifier.requiredSize(900.dp, 600.dp))
            }
        }
        val grip = compose.onNodeWithContentDescription("Resize source and preview; arrow keys adjust")
        grip.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        grip.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(0.55f, tab.splitFraction) }
        grip.performMouseInput { moveTo(center); press(); moveTo(center + androidx.compose.ui.geometry.Offset(1000f, 0f), 500); release() }
        compose.runOnIdle { assertEquals(0.75f, tab.splitFraction) }
        grip.performMouseInput { moveTo(center); press(); moveTo(center - androidx.compose.ui.geometry.Offset(1000f, 0f), 500); release() }
        compose.runOnIdle { assertEquals(0.25f, tab.splitFraction); interactive = false }
        grip.performMouseInput { moveTo(center); press(); moveTo(center + androidx.compose.ui.geometry.Offset(500f, 0f), 500); release() }
        compose.runOnIdle { assertEquals(0.25f, tab.splitFraction) }
    }

    @Test
    fun tableOfContentsNavigatesToItsActualHeading() {
        val capabilities = Capabilities()
        capabilities.renderNodes = List(25) { RenderNode("paragraph", children = listOf(RenderNode("text", "Paragraph $it"))) } +
            RenderNode("h2", anchor = "end", children = listOf(RenderNode("text", "End")))
        capabilities.renderToc = listOf(RenderHeading(2, "End", "end"))
        compose.setContent { IntUiTheme(isDark = false) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Table of contents").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Table of contents").performClick()
        compose.onNodeWithContentDescription("Go to End").performClick()
        compose.onNode(hasText("End") and !hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun externalLinksRequireConfirmationAndGuardWorkspaceShortcuts() {
        val capabilities = Capabilities()
        capabilities.renderNodes = listOf(RenderNode("paragraph", children = listOf(
            RenderNode("link", target = "https://example.invalid", children = listOf(RenderNode("text", "External document"))))))
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.waitUntil { compose.onAllNodesWithText("External document").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("External document").performTouchInput { click(androidx.compose.ui.geometry.Offset(10f, 10f)) }
        compose.onNodeWithText("Open external link?").assertExists()
        compose.onNodeWithContentDescription("Toggle Search panel").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, capabilities.openedLinks) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("External document").performTouchInput { click(androidx.compose.ui.geometry.Offset(10f, 10f)) }
        compose.onNodeWithText("Open link").performClick()
        compose.runOnIdle { assertEquals(1, capabilities.openedLinks) }
    }

    @Test
    fun editsSurviveFailedSaveAndCanBeSavedAndCompared() {
        val capabilities = Capabilities()
        var dirty = false
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { value, _ -> dirty = value })
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("edited")
        compose.runOnIdle { assertTrue(dirty); capabilities.fail = true }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Conflict: edits not saved").assertExists()
        sourceField("Markdown body").assertTextContains("edited")
        compose.runOnIdle { assertTrue(dirty); capabilities.fail = false }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Draft saved.").assertExists()
        compose.runOnIdle { assertFalse(dirty) }
        compose.onNodeWithContentDescription("Toggle History panel").performClick()
        compose.onNodeWithText("Older revision").assertExists()
        compose.onNodeWithText("Compare").performClick()
        compose.onNodeWithText("-original\n+edited").assertExists()
    }

    @Test
    fun historyLoadingKeepsEditorResponsiveAndRetainsEdits() {
        val capabilities = Capabilities()
        val pending = CompletableDeferred<Result<DocumentHistorySnapshot>>()
        capabilities.historyPending = pending
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { _, _ -> })
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithText("Show history").performClick()
        compose.onNodeWithText("Loading verified history...").assertExists()
        sourceField("Markdown body").performTextReplacement("edited while history loads")
        compose.onNodeWithText("Save").assertIsEnabled()
        sourceField("Markdown body").assertTextContains("edited while history loads")

        compose.runOnIdle {
            pending.complete(Result.success(DocumentHistorySnapshot(listOf(capabilities.first), emptyList())))
        }
        compose.onNodeWithText("No lifecycle events recorded.").assertExists()
        sourceField("Markdown body").assertTextContains("edited while history loads")
    }

    @Test
    fun documentListLoadingDoesNotBlockDraftEditing() {
        val capabilities = Capabilities()
        val pending = CompletableDeferred<Result<List<DocumentSnapshot>>>()
        capabilities.listPending = pending
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { _, _ -> })
            }
        }
        compose.onNodeWithText("Loading documents...").assertExists()
        compose.onNodeWithText("New document").assertIsEnabled().performClick()
        sourceField("Document title").performTextReplacement("Draft during list load")
        sourceField("Markdown body").performTextReplacement("unsaved")
        compose.runOnIdle {
            pending.complete(Result.success(listOf(capabilities.first)))
        }
        compose.onNodeWithText("Policy (Draft)").assertExists()
        sourceField("Document title").assertTextContains("Draft during list load")
        sourceField("Markdown body").assertTextContains("unsaved")
    }

    @Test
    fun newDocumentDoesNotWriteBeforeSaveAndBusySaveCannotRepeat() {
        val capabilities = Capabilities()
        val pending = CompletableDeferred<Result<DocumentSnapshot>>()
        capabilities.pending = pending
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        sourceField("Document title").performTextInput("New policy")
        compose.runOnIdle { assertEquals(0, capabilities.saves) }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("New document").assertIsNotEnabled()
        shortcut(Key.Four)
        compose.onNodeWithContentDescription("Toggle History panel").assertIsNotEnabled().assertIsNotSelected()
        compose.onNodeWithContentDescription("Toggle Review panel").assertIsSelected()
        compose.runOnIdle { assertEquals(1, capabilities.saves); pending.complete(Result.success(capabilities.first)) }
        compose.onNodeWithText("Draft saved.").assertExists()
        compose.waitUntil(3000) { capabilities.prepares == 0 }
        compose.onNodeWithText("Refresh documents").performClick()
        compose.runOnIdle { assertEquals(0, capabilities.prepares) }
    }

    @Test
    fun historicalSearchPreservesDirtyLatestTabAndCannotOverwriteIt() {
        val capabilities = Capabilities()
        capabilities.saved = capabilities.first.copy(title = "Latest policy", body = "latest", revision = "second")
        val hit = SearchHit(capabilities.first.id, "Policy", "first", "Test", "1", "original")
        capabilities.searchResult = capabilities.searchResult.copy(
            historical = true, documents = SearchPage(listOf(hit), null),
        )
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { _, _ -> })
            }
        }
        compose.onNodeWithText("Latest policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("unsaved latest")
        searchField().performTextReplacement("original")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        compose.onNodeWithText("History matches", substring = true).assertExists()
        compose.onNodeWithText("Policy (Saved revision)", substring = true).performClick()
        compose.onNodeWithText("Saved revision - read-only").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        sourceField("Markdown body").assertTextContains("original")
        compose.runOnIdle {
            assertEquals(listOf(capabilities.first.id to "first"), capabilities.reads)
            assertEquals(0, capabilities.saves)
            assertEquals("latest", capabilities.saved.body)
        }
        compose.onNodeWithText("Return to latest document").performClick()
        sourceField("Markdown body").assertTextContains("unsaved latest")
        compose.onNodeWithText("Save").assertIsEnabled()
        searchField().performTextClearance()
        compose.onNodeWithContentDescription("Toggle Project panel").performClick()
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
                DocumentScreen("/project", capabilities, { _, _ -> }, onSearchResultsLaidOut = { timings += it })
            }
        }
        searchField().performTextReplacement("policy")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        compose.onNodeWithText("Policy", substring = false).performClick()
        compose.runOnIdle {
            assertEquals("policy", timings.single().query)
            assertFalse(timings.single().historical)
            assertTrue(timings.single().elapsedMilliseconds >= 0)
            assertEquals(1, capabilities.prepares)
            assertEquals(listOf(capabilities.first.id), capabilities.latestReads)
        }
        sourceField("Markdown body").performTextReplacement("unsaved text")
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
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        searchField().performTextReplacement("policy")
        compose.waitUntil(3000) { capabilities.queries.isNotEmpty() }
        compose.runOnIdle {
            capabilities.saved = capabilities.first.copy(revision = "second", body = "external update")
        }
        compose.onNodeWithText("Policy", substring = false).performClick()
        sourceField("Markdown body").assertTextContains("external update")
        compose.runOnIdle {
            capabilities.saved = capabilities.saved.copy(revision = "third", body = "newer external update")
        }
        sourceField("Markdown body").performTextReplacement("my edit")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Conflict: draft changed").assertExists()
        sourceField("Markdown body").assertTextContains("my edit")
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
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        searchField().performTextReplacement("title")
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
                DocumentScreen("/project", capabilities, { _, _ -> }, onSearchResultsLaidOut = { timings += it })
            }
        }
        val queries = listOf("And", "risk", "And", "trace", "risk")
        queries.forEachIndexed { index, query ->
            searchField().performTextReplacement(query)
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
    fun comparisonErrorsAreVisibleAndClosingPanelRestoresEditor() {
        val capabilities = Capabilities().apply { diffFails = true }
        compose.setContent {
            IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithContentDescription("Toggle History panel").performClick()
        compose.onNodeWithText("Compare").performClick()
        compose.onNodeWithText("Comparison unavailable").assertExists()
        compose.onNodeWithText("New document").assertIsEnabled()
        compose.onNodeWithContentDescription("Toggle History panel").performClick()
        compose.onNodeWithText("New document").assertIsEnabled()
        sourceField("Markdown body").assertTextContains("original")
    }

    @Test
    fun documentRowsSupportKeyboardFocusAndActivation() {
        val capabilities = Capabilities()
        compose.setContent {
            IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) }
        }
        compose.onNodeWithText("Policy (Draft)").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Policy (Draft)").assertIsFocused()
        compose.onNodeWithText("Policy (Draft)").performKeyInput { pressKey(Key.Enter) }
        sourceField("Markdown body").assertTextContains("original")
        compose.onNodeWithText("Policy (Draft)").assertIsSelected()
    }

    @Test
    fun confirmedLifecycleLocksContentAndShowsOnlyObservedStateOnFailure() {
        val capabilities = Capabilities()
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        compose.onNodeWithText("Policy (Draft)").performClick()
        compose.onNodeWithText("Submit for review").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, capabilities.transitions) }
        sourceField("Markdown body").performTextReplacement("dirty")
        compose.onNodeWithText("Submit for review").assertIsNotEnabled()
        compose.onNodeWithText("Save").assertIsDisplayed().performClick()
        for (action in listOf("Submit for review", "Approve", "Publish")) {
            compose.onNodeWithText(action).performScrollTo().performClick()
            compose.onNodeWithText("Confirm").performClick()
            compose.onNodeWithText("Save").assertIsNotEnabled()
        }
        compose.runOnIdle { capabilities.transitionFails = true }
        compose.onNodeWithText("Mark obsolete").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Stale lifecycle request").assertExists()
        compose.runOnIdle { assertEquals(DocumentStatus.Effective, capabilities.saved.status) }
        compose.runOnIdle { capabilities.transitionFails = false }
        compose.onNodeWithText("Mark obsolete").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(DocumentStatus.Obsolete, capabilities.saved.status) }
        compose.onNodeWithText("Mark obsolete").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun panelsAndTabsRetainTextAndCaretWhileDirtyCloseCanBeCancelled() {
        val capabilities = Capabilities()
        compose.setContent { IntUiTheme(isDark = true) { DocumentScreen("/project", capabilities, { _, _ -> }) } }
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("keep policy edits")
        sourceField("Markdown body").performTextInputSelection(TextRange(6))
        compose.onNodeWithText("New document").performClick()
        sourceField("Document title").performTextInput("Second")
        compose.onNodeWithContentDescription("Document tab Policy").performClick()
        sourceField("Markdown body").assertTextContains("keep policy edits")
        assertEquals(TextRange(6), sourceField("Markdown body").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        compose.onNodeWithContentDescription("Toggle Project panel").performClick()
        compose.onNodeWithContentDescription("Toggle Project panel").performClick()
        compose.onNodeWithContentDescription("Toggle Review panel").performClick()
        compose.onNodeWithContentDescription("Toggle Review panel").performClick()
        sourceField("Markdown body").assertTextContains("keep policy edits")
        compose.onNodeWithContentDescription("Close tab Policy").performClick()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        sourceField("Markdown body").assertTextContains("keep policy edits")
        compose.onNodeWithContentDescription("Close tab Policy").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        sourceField("Document title").assertTextContains("Second")
    }

    @Test
    fun minimumSizeKeepsEditorAccessibleAndPanelsHaveKeyboardControls() {
        val capabilities = Capabilities()
        compose.setContent {
            IntUiTheme(isDark = true) {
                Box(Modifier.requiredSize(960.dp, 720.dp)) {
                    DocumentScreen("/project", capabilities, { _, _ -> })
                }
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").assertIsDisplayed()
        assertTrue(sourceField("Markdown body").fetchSemanticsNode().boundsInRoot.width >= 180f)
        compose.onNodeWithContentDescription("Toggle Project panel")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Toggle Project panel").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("New document").assertDoesNotExist()
        compose.onNodeWithContentDescription("Toggle Project panel")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Toggle Project panel").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("New document").assertExists()
    }

    @Test
    fun workspaceCloseOffersSaveAllAndFailedSavePreventsNavigation() {
        val capabilities = Capabilities()
        val navigation = DocumentNavigationController()
        var closed = false
        compose.setContent {
            IntUiTheme(isDark = true) {
                DocumentScreen("/project", capabilities, { _, _ -> }, navigation = navigation)
            }
        }
        compose.onNodeWithText("Policy (Draft)").performClick()
        sourceField("Markdown body").performTextReplacement("first edit")
        compose.onNodeWithText("New document").performClick()
        sourceField("Document title").performTextInput("Second")
        compose.runOnIdle { navigation.request { closed = true } }
        compose.onNodeWithText("Save all").assertExists()
        compose.onNodeWithText("Discard all").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertFalse(closed)
            capabilities.fail = true
            navigation.request { closed = true }
        }
        compose.onNodeWithText("Save all").performClick()
        sourceField("Markdown body").assertTextContains("first edit")
        compose.onNodeWithText("Conflict: edits not saved").assertExists()
        compose.runOnIdle {
            assertFalse(closed)
            capabilities.fail = false
            navigation.request { closed = true }
        }
        compose.onNodeWithText("Save all").performClick()
        compose.runOnIdle { assertTrue(closed); assertEquals(3, capabilities.saves) }
    }
}
