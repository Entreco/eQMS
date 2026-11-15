package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.feature.ui.BrandLogo
import com.entreco.eqms.feature.ui.BrandTokens
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun tokens(name: String, accent: String = "#C2410C", seed: String = "acme") =
        BrandTokens.parse(name, accent, "#FFFFFF", BrandLogo.Generated(seed)).getOrThrow()

    private class Fake(var stored: BrandingPreview) : ProjectCapabilities {
        val saved = mutableListOf<BrandingDraft>()
        var failSave: String? = null
        override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing("/project", null, emptyList()))
        override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
        override suspend fun openProject(path: String, create: Boolean) = Result.success(path)
        override suspend fun projectBranding(path: String) = Result.success<BrandingPreview?>(stored)
        override suspend fun previewBranding(draft: BrandingDraft) = testBrandingPreview(draft)
        override suspend fun saveBranding(path: String, draft: BrandingDraft): Result<BrandingPreview> {
            failSave?.let { return Result.failure(IllegalStateException(it)) }
            saved += draft
            stored = BrandingPreview(
                BrandTokens.parse(draft.name, stored.tokens.accent.let { "#C2410C" }, "#FFFFFF", BrandLogo.Generated(draft.seed)).getOrThrow(),
                stored.generated,
            )
            return Result.success(stored)
        }
    }

    private fun open(fake: Fake) {
        val chrome = testChrome()
        compose.setContent { ProjectScreen(fake, chrome = chrome) }
        compose.menu(chrome, DesktopCommand.OpenProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project verified and opened.").assertExists()
    }

    private fun stored() = BrandingPreview(tokens("Acme Quality"), GeneratedLogo("AQ", GeneratedLogoShape.Circle))

    @Test
    fun headerShowsProductLogoAndProjectName() {
        open(Fake(stored()))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Acme Quality").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("DutchDoxx").assertExists()
        compose.onNodeWithContentDescription("Logo preview for Acme Quality").assertExists()
    }

    @Test
    fun settingsDialogChangesTheNameWithAnExplicitSave() {
        val fake = Fake(stored())
        open(fake)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Acme Quality").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Project Settings").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Project name").performTextReplacement("Beta Quality")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Save").assertIsEnabled() }.isSuccess }
        assertEquals(0, fake.saved.size)
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { fake.saved.isNotEmpty() }
        assertEquals("Beta Quality", fake.saved.single().name)
        assertEquals("#C2410C", fake.saved.single().accent)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Branding saved.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun saveFailureIsShownAndUnsavedChangesNeedConfirmationToClose() {
        val fake = Fake(stored()).apply { failSave = "disk full" }
        open(fake)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Acme Quality").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Project name").performTextReplacement("Beta")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Save").assertIsEnabled() }.isSuccess }
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("disk full").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Project Settings").assertExists()
        compose.onNodeWithText("Discard unsaved branding changes?", substring = true).assertExists()
        compose.onNodeWithText("Discard").performClick()
        compose.onAllNodesWithText("Project Settings").assertCountEquals(0)
    }

    @Test
    fun settingsWithoutAProjectOnlyOffersAppearance() {
        val chrome = testChrome()
        compose.setContent { ProjectScreen(chromeTestProjects(), chrome = chrome) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Use light appearance").assertExists()
        compose.onNodeWithText("Open a project to edit its branding.").assertExists()
        compose.onAllNodesWithText("Save").assertCountEquals(0)
    }
}
