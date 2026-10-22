package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test

class DesktopChromeTest {
    @get:Rule val compose = createComposeRule()
    private val projects = object : ProjectCapabilities {
        override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing("/project", null, emptyList()))
        override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
        override suspend fun openProject(path: String, create: Boolean) = Result.success(path)
    }

    @Test
    fun aboutIsOneNativeDecoratedNonModalWindowWithIndependentLifecycle() {
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(true), { Result.success(Unit) },
            { error("No check expected") }, { error("No browser expected") },
        )
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project verified and opened.").assertExists()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertIsDisplayed()
        fun aboutFrames() = java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
            .filter { it.isShowing && it.title == "About DutchDoxx" }
        var about: java.awt.Frame? = null
        compose.runOnIdle {
            about = aboutFrames().single()
            assertFalse(about!!.isUndecorated)
            assertTrue(chrome.enabled)
            assertTrue(chrome.hasProject)
            assertTrue(java.awt.Window.getWindows().none { it.isShowing && it is java.awt.Dialog && it.isModal })
            chrome.request(DesktopCommand.About)
        }
        compose.runOnIdle { assertSame(about, aboutFrames().single()) }
        compose.onNodeWithText("Light theme").performClick()
        compose.onNodeWithText("Dark theme").assertExists()
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertIsDisplayed()
        compose.runOnIdle {
            about!!.extendedState = about!!.extendedState or java.awt.Frame.ICONIFIED
        }
        compose.waitUntil(3000) { about!!.extendedState and java.awt.Frame.ICONIFIED != 0 }
        compose.waitForIdle()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.waitUntil(3000) {
            about!!.extendedState and java.awt.Frame.ICONIFIED == 0
        }
        compose.runOnIdle {
            assertSame(about, aboutFrames().single())
            about!!.dispatchEvent(java.awt.event.WindowEvent(about, java.awt.event.WindowEvent.WINDOW_CLOSING))
        }
        compose.waitUntil(3000) { !about!!.isDisplayable }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertDoesNotExist()
        compose.onNodeWithText("Project verified and opened.").assertExists()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertIsDisplayed()
        compose.runOnIdle { assertNotSame(about, aboutFrames().single()) }
        compose.onNodeWithText("Close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertTrue(aboutFrames().isEmpty()); assertTrue(chrome.hasProject) }
    }

    @Test
    fun settingsSynchronizeToolbarAndSurfacePersistenceFailure() {
        var saved: Boolean? = null
        var fail = false
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(false),
            { value -> if (fail) Result.failure(IllegalStateException("disk failure")) else { saved = value; Result.success(Unit) } },
            { error("No update check expected") }, { error("No browser expected") },
        )
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Dark theme").performClick()
        compose.runOnIdle { assertEquals(true, saved); chrome.request(DesktopCommand.Settings) }
        compose.onNodeWithText("Appearance: Dark").assertExists()
        compose.onNodeWithText("Use light appearance").performClick()
        compose.onNodeWithText("Appearance: Light").assertExists()
        compose.runOnIdle { fail = true }
        compose.onNodeWithText("Use dark appearance").performClick()
        compose.onAllNodesWithText("Could not save appearance: disk failure").onFirst().assertExists()
        compose.onNodeWithText("Appearance: Light").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Dark theme").assertExists()
    }

    @Test
    fun invalidInitialPreferenceIsVisibleAndAboutShowsBuildVersion() {
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.failure(IllegalArgumentException("invalid value")),
            { Result.success(Unit) }, { error("No check expected") }, { error("No browser expected") },
        )
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Could not load appearance: invalid value").assertExists()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertExists()
    }

    @Test
    fun updateCheckStartsOnceCancelsOnDismissAndCanRetry() {
        var calls = 0
        var cancelled = false
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(true), { Result.success(Unit) },
            {
                calls++
                try { awaitCancellation() } finally { cancelled = true }
            }, { error("No browser expected") },
        )
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.runOnIdle { assertEquals(0, calls); chrome.request(DesktopCommand.CheckUpdates) }
        compose.onNodeWithText("Checking for updates...").assertExists()
        compose.runOnIdle {
            assertEquals(1, calls)
            assertFalse(chrome.enabled)
            chrome.request(DesktopCommand.CheckUpdates)
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(cancelled); chrome.request(DesktopCommand.CheckUpdates) }
        compose.onNodeWithText("Checking for updates...").assertExists()
        compose.runOnIdle { assertEquals(2, calls) }
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun updateAndBrowserFailuresAreDisplayed() {
        var fail = true
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(true), { Result.success(Unit) },
            {
                if (fail) Result.failure(IllegalStateException("offline"))
                else Result.success(ReleaseCheck("Available", "https://github.com/Entreco/eQMS/releases/tag/v1.0.0"))
            },
            { Result.failure(IllegalStateException("browser unavailable")) },
        )
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.runOnIdle { chrome.request(DesktopCommand.CheckUpdates) }
        compose.onNodeWithText("offline").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { fail = false; chrome.request(DesktopCommand.CheckUpdates) }
        compose.onNodeWithText("Available").assertExists()
        compose.onNodeWithText("Open release page").performClick()
        compose.onNodeWithText("browser unavailable").assertExists()
    }

    @Test
    fun workspaceBusyAndChooserRejectCompetingMenuCommands() {
        val result = CompletableDeferred<Result<String>>()
        val busyProjects = object : ProjectCapabilities by projects {
            override suspend fun openProject(path: String, create: Boolean) = result.await()
        }
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(true), { Result.success(Unit) },
            { error("No check expected") }, { error("No browser expected") },
        )
        compose.setContent { ProjectScreen(busyProjects, chrome = chrome) }
        compose.runOnIdle { chrome.request(DesktopCommand.NewProject) }
        compose.onNodeWithText("Select directory").assertExists()
        compose.runOnIdle { assertFalse(chrome.enabled); chrome.request(DesktopCommand.Settings) }
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Working...").assertExists()
        compose.runOnIdle { assertFalse(chrome.enabled); chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("About DutchDoxx").assertDoesNotExist()
        compose.runOnIdle { result.complete(Result.success("/project")) }
        compose.onNodeWithText("Project created successfully.").assertExists()
        compose.runOnIdle { assertTrue(chrome.enabled); assertTrue(chrome.hasProject) }
    }
}
