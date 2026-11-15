package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule

class ProductIdentityUiTest {
    @get:Rule val compose = createComposeRule()
    private val projects = chromeTestProjects()

    @Test
    fun aboutShowsIdentityAndMakesNoCapabilityClaims() {
        compose.setContent { IntUiTheme(isDark = false) { AboutContent("1.2.3", {}) } }
        compose.onNodeWithText("DutchDoxx").assertExists()
        compose.onNodeWithText("DutchDoxx 1.2.3").assertExists()
        compose.onNodeWithText("Author: Entreco").assertExists()
        compose.onNodeWithText(ProductIdentity.LICENCE_NOTICE).assertExists()
        compose.onNodeWithText("Powered by DutchDoxx").assertExists()
        compose.onNodeWithText(ProductIdentity.DESCRIPTION).assertExists()
        compose.onAllNodesWithText("signature", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun footerAttributionIsAlwaysVisibleDespiteProjectNaming() {
        val chrome = testChrome()
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Powered by DutchDoxx").assertExists()
        compose.runOnIdle { chrome.dark = false }
        compose.onNodeWithText("Powered by DutchDoxx").assertExists()
    }

    @Test
    fun openProjectNamedLikeAnotherBrandCannotHideAttribution() {
        val chrome = testChrome()
        val named = object : ProjectCapabilities by projects {
            override suspend fun openProject(path: String, create: Boolean) = Result.success("/work/Acme Corp QMS")
        }
        compose.setContent { ProjectScreen(named, chrome = chrome) }
        compose.menu(chrome, DesktopCommand.OpenProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.onAllNodesWithText("Powered by DutchDoxx").assertCountEquals(1)
        assertEquals("DutchDoxx", ProductIdentity.windowTitle("Acme Corp QMS").substringAfterLast(' '))
    }
}
