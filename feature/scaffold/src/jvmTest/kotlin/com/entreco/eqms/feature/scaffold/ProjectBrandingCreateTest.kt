package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectBrandingCreateTest {
    @get:Rule
    val compose = createComposeRule()

    private class Projects(var createResult: Result<String>? = null) : ProjectCapabilities {
        val created = mutableListOf<Pair<String, BrandingDraft>>()
        val opened = mutableListOf<Pair<String, Boolean>>()
        override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing(path ?: "/work/acme", null, emptyList()))
        override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
        override suspend fun openProject(path: String, create: Boolean): Result<String> {
            opened += path to create
            return Result.success(path)
        }
        override suspend fun previewBranding(draft: BrandingDraft) = testBrandingPreview(draft)
        override suspend fun createProject(path: String, draft: BrandingDraft): Result<String> {
            created += path to draft
            return createResult ?: Result.success(path)
        }
        override suspend fun chooseLogoFile() = "/tmp/logo.png"
    }

    private fun startCreating(projects: Projects) {
        val chrome = testChrome()
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.menu(chrome, DesktopCommand.NewProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Project identity").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitCreateEnabled() = compose.waitUntil(5_000) {
        runCatching { compose.onNodeWithText("Create project").assertIsEnabled() }.isSuccess
    }

    private fun field(label: String) = compose.onNodeWithContentDescription(label)

    @Test
    fun cancellingTheIdentityStepCreatesNothing() {
        val projects = Projects()
        startCreating(projects)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Project selection cancelled.").assertExists()
        compose.runOnIdle { assertTrue(projects.created.isEmpty() && projects.opened.isEmpty()) }
    }

    @Test
    fun defaultsDeriveFromTheFolderAndCreateRecordsThePreviewedDraft() {
        val projects = Projects()
        startCreating(projects)
        awaitCreateEnabled()
        compose.onNodeWithContentDescription("Logo preview for acme").assertExists()
        field("Project name").performTextReplacement("Acme Quality")
        awaitCreateEnabled()
        compose.onNodeWithContentDescription("Logo preview for Acme Quality").assertExists()
        compose.onNodeWithText("Create project").performClick()
        compose.onNodeWithText("Project created successfully.").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("/work/acme" to BrandingDraft("Acme Quality", "Acme Quality", "auto", null)), projects.created)
            assertTrue(projects.opened.isEmpty())
        }
    }

    @Test
    fun shuffleChangesTheSeedAndAccentSuggestionIsUsed() {
        val projects = Projects()
        startCreating(projects)
        awaitCreateEnabled()
        compose.onNodeWithText("Shuffle logo").performClick()
        compose.onNodeWithContentDescription("Accent #1D4ED8").performClick()
        awaitCreateEnabled()
        compose.onNodeWithText("Create project").performClick()
        compose.waitUntil(5_000) { projects.created.isNotEmpty() }
        val draft = projects.created.single().second
        assertTrue(draft.seed.startsWith("acme#") && draft.seed != "acme", draft.seed)
        assertEquals("#1D4ED8", draft.accent)
    }

    @Test
    fun coreWarningsAreShownButDoNotBlockCreation() {
        startCreating(Projects())
        field("Accent colour (#RRGGBB or auto)").performTextReplacement("#FFFF00")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Warning:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        awaitCreateEnabled()
    }

    @Test
    fun invalidAccentAndBlankNameDisableCreateWithAReason() {
        startCreating(Projects())
        field("Accent colour (#RRGGBB or auto)").performTextReplacement("red")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("accent must be #RRGGBB").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create project").assertIsNotEnabled()
        field("Accent colour (#RRGGBB or auto)").performTextReplacement("auto")
        field("Project name").performTextReplacement("")
        compose.onNodeWithText("Enter a project name.").assertExists()
        compose.onNodeWithText("Create project").assertIsNotEnabled()
    }

    @Test
    fun browsingFillsTheLogoFileAndFailureIsReported() {
        val projects = Projects(Result.failure(IllegalStateException("branding rejected")))
        startCreating(projects)
        awaitCreateEnabled()
        compose.onNodeWithText("Browse").performClick()
        awaitCreateEnabled()
        compose.onNodeWithText("Create project").performClick()
        compose.onNodeWithText("branding rejected").assertExists()
        compose.runOnIdle { assertEquals("/tmp/logo.png", projects.created.single().second.logoFile) }
        compose.onNodeWithText("Shuffle logo").assertDoesNotExist()
    }
}
