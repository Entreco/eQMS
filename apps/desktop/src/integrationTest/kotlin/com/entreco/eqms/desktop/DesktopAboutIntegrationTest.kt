package com.entreco.eqms.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.entreco.eqms.feature.scaffold.*
import org.junit.Rule
import org.junit.Test

class DesktopAboutIntegrationTest {
    init {
        requireNativeIntegrationEnvironment()
    }

    @get:Rule val compose = createComposeRule()

    @Test
    fun actualAboutDialogShowsAccessibleLogoAndVersionInBothThemes() {
        val chrome = DesktopChrome(
            desktopVersion(), Result.success(true), { Result.success(Unit) },
            { error("No update request expected") }, { error("No browser expected") },
            aboutLogo = { DesktopLogo() },
        )
        val projects = object : ProjectCapabilities {
            override suspend fun listDirectory(path: String?): Result<DirectoryListing> = error("No directory request expected")
            override suspend fun createFolder(parent: String, name: String): Result<String> = error("No creation expected")
            override suspend fun openProject(path: String, create: Boolean): Result<String> = error("No project request expected")
        }
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        repeat(2) { theme ->
            compose.runOnIdle { chrome.request(DesktopCommand.About) }
            compose.onNodeWithContentDescription("DutchDoxx logo").assertIsDisplayed()
            compose.onNodeWithText("DutchDoxx ${desktopVersion()}").assertIsDisplayed()
            compose.onNodeWithText("An eQMS quality workspace. Drafts are not approvals.").assertIsDisplayed()
            compose.onNodeWithText("Close").performSemanticsAction(SemanticsActions.OnClick) { it() }
            if (theme == 0) {
                compose.onNodeWithContentDescription("Settings").performClick()
                compose.onNodeWithText("Use light appearance").performClick()
                compose.onNodeWithText("Close").performClick()
            }
        }
    }
}
