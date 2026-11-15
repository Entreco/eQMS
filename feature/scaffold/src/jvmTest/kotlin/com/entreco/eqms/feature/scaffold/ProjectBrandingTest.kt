package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.ui.BrandLogo
import com.entreco.eqms.feature.ui.BrandTokens
import org.junit.Rule
import org.junit.Test

class ProjectBrandingTest {
    @get:Rule
    val compose = createComposeRule()

    private fun projects(tokens: Result<BrandTokens?>) = object : ProjectCapabilities {
        override suspend fun listDirectory(path: String?) =
            Result.success(DirectoryListing(path ?: "/home", null, emptyList()))
        override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
        override suspend fun openProject(path: String, create: Boolean) = Result.success(path)
        override suspend fun brandTokens(path: String) = tokens
    }

    private val weak = BrandTokens.parse(
        "Acme", "#C2410C", "#FFFFFF", BrandLogo.Generated("s"),
        listOf("accent #C2410C has 2.67:1 contrast on the dark theme (3:1 recommended)"),
    ).getOrThrow()

    private fun open(capabilities: ProjectCapabilities, width: Int = 900) {
        val chrome = testChrome()
        compose.setContent { Box(Modifier.width(width.dp)) { ProjectScreen(capabilities, chrome = chrome) } }
        compose.menu(chrome, DesktopCommand.OpenProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project verified and opened.").assertExists()
    }

    @Test
    fun coreContrastWarningsAreShownAfterOpenAndDoNotBlock() {
        open(projects(Result.success(weak)))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Branding warning:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Branding warning: ${weak.warnings.single()}").assertExists()
        compose.onNodeWithContentDescription("Settings").assertIsEnabled()
    }

    @Test
    fun warningsRemainVisibleInANarrowWindow() {
        open(projects(Result.success(weak)), width = 360)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Branding warning:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun loadFailureIsReportedAndFallsBackToTheTheme() {
        open(projects(Result.failure(IllegalStateException("tampered"))))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Project branding unavailable: tampered").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun warningsAreClearedWhenTheProjectIsClosed() {
        val chrome = testChrome()
        compose.setContent { ProjectScreen(projects(Result.success(weak)), chrome = chrome) }
        compose.menu(chrome, DesktopCommand.OpenProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Branding warning:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.menu(chrome, DesktopCommand.CloseProject)
        compose.onAllNodesWithText("Branding warning:", substring = true).assertCountEquals(0)
    }
}
