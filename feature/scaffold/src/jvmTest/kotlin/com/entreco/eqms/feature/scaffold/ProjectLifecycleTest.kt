package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class ProjectLifecycleTest {
    @get:Rule val compose = createComposeRule()

    private class Projects : ProjectCapabilities {
        val opened = mutableListOf<Pair<String, Boolean>>()
        var gate: CompletableDeferred<Result<String>>? = null
        var failures = mutableSetOf<String>()

        override suspend fun listDirectory(path: String?) =
            Result.success(DirectoryListing(path ?: "/home", null, emptyList()))
        override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
        override suspend fun openProject(path: String, create: Boolean): Result<String> {
            opened += path to create
            gate?.let { return it.await() }
            return if (path in failures) Result.failure(IllegalArgumentException("Verification failed"))
            else Result.success(path)
        }
    }

    private class Saves {
        val calls = mutableListOf<Pair<String?, List<String>>>()
        var fail = false
        val save: suspend (String?, List<String>) -> Result<Unit> = { project, recents ->
            calls += project to recents
            if (fail) Result.failure(IllegalStateException("disk full")) else Result.success(Unit)
        }
    }

    @Test
    fun windowHasNoInWindowProjectControlsAndPointsToFileMenu() {
        val chrome = testChrome()
        compose.setContent { ProjectScreen(Projects(), chrome = chrome) }
        compose.onNodeWithText("Your quality workspace").assertExists()
        compose.onNodeWithText("Use File > New project or File > Open project to get started.").assertExists()
        for (text in listOf("Create project", "Open project", "Project", "No project open", "Workspace open")) {
            compose.onNodeWithText(text).assertDoesNotExist()
        }
        compose.runOnIdle { assertFalse(chrome.hasProject) }
    }

    @Test
    fun storedProjectIsVerifiedAndReopenedOnLaunch() {
        val projects = Projects()
        val saves = Saves()
        val chrome = testChrome(project = Result.success("/stored"), recents = Result.success(listOf("/old")), saveProjects = saves.save)
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Project restored and verified.").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("/stored" to false), projects.opened)
            assertTrue(chrome.hasProject)
            assertEquals(listOf("/stored", "/old"), chrome.recents)
            assertEquals(listOf<Pair<String?, List<String>>>("/stored" to listOf("/stored", "/old")), saves.calls)
        }
    }

    @Test
    fun windowRendersBeforeSlowRestoreCompletes() {
        val projects = Projects().apply { gate = CompletableDeferred() }
        val chrome = testChrome(project = Result.success("/slow"))
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Your quality workspace").assertExists()
        compose.onNodeWithText("Working...").assertExists()
        compose.runOnIdle { assertFalse(chrome.enabled); assertFalse(chrome.hasProject) }
        compose.runOnIdle { projects.gate!!.complete(Result.success("/slow")) }
        compose.onNodeWithText("Project restored and verified.").assertExists()
        compose.runOnIdle { assertTrue(chrome.enabled); assertTrue(chrome.hasProject) }
    }

    @Test
    fun failedRestoreShowsErrorKeepsEmptyStateAndDoesNotRewriteStoredPath() {
        val projects = Projects().apply { failures += "/gone" }
        val saves = Saves()
        val chrome = testChrome(project = Result.success("/gone"), saveProjects = saves.save)
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Could not reopen the last project: Verification failed").assertExists()
        compose.onNodeWithText("Your quality workspace").assertExists()
        compose.runOnIdle {
            assertFalse(chrome.hasProject)
            assertTrue(chrome.enabled)
            assertTrue(saves.calls.isEmpty())
        }
    }

    @Test
    fun invalidStoredValuesAreSurfacedWithoutBlockingUse() {
        val projects = Projects()
        val chrome = testChrome(project = Result.failure(IllegalArgumentException("Invalid stored project path.")))
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Could not load the last project: Invalid stored project path.").assertExists()
        compose.runOnIdle { assertTrue(projects.opened.isEmpty()); assertTrue(chrome.enabled) }

        val other = testChrome(recents = Result.failure(IllegalArgumentException("bad recent")))
        compose.setContent { ProjectScreen(projects, chrome = other) }
        compose.onNodeWithText("Could not load recent projects: bad recent").assertExists()
    }

    @Test
    fun openSavesProjectAndRecentsCloseClearsProjectButKeepsRecents() {
        val projects = Projects()
        val saves = Saves()
        val chrome = testChrome(recents = Result.success(listOf("/a", "/b", "/c", "/d", "/e")), saveProjects = saves.save)
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.menu(chrome, DesktopCommand.OpenProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project verified and opened.").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("/home", "/a", "/b", "/c", "/d"), chrome.recents)
            assertEquals("/home" to listOf("/home", "/a", "/b", "/c", "/d"), saves.calls.last())
        }
        compose.menu(chrome, DesktopCommand.CloseProject)
        compose.runOnIdle {
            assertFalse(chrome.hasProject)
            assertEquals(null to listOf("/home", "/a", "/b", "/c", "/d"), saves.calls.last())
        }
    }

    @Test
    fun recentProjectOpensWithVerificationAndFailureKeepsEntry() {
        val projects = Projects().apply { failures += "/b" }
        val saves = Saves()
        val chrome = testChrome(recents = Result.success(listOf("/a", "/b")), saveProjects = saves.save)
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.openRecent(chrome, "/unknown")
        compose.runOnIdle { assertTrue(projects.opened.isEmpty()) }
        compose.openRecent(chrome, "/b")
        compose.onNodeWithText("Verification failed").assertExists()
        compose.runOnIdle { assertEquals(listOf("/a", "/b"), chrome.recents); assertTrue(saves.calls.isEmpty()) }
        compose.openRecent(chrome, "/a")
        compose.onNodeWithText("Project verified and opened.").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("/b" to false, "/a" to false), projects.opened)
            assertEquals(listOf("/a", "/b"), chrome.recents)
            assertTrue(chrome.hasProject)
        }
    }

    @Test
    fun recentCommandsAreRefusedWhileBusy() {
        val projects = Projects().apply { gate = CompletableDeferred() }
        val chrome = testChrome(project = Result.success("/slow"), recents = Result.success(listOf("/other")))
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Working...").assertExists()
        compose.openRecent(chrome, "/other")
        compose.runOnIdle {
            assertEquals(listOf("/slow" to false), projects.opened)
            projects.gate!!.complete(Result.success("/slow"))
        }
    }

    @Test
    fun preferenceSaveFailureIsShownAndProjectStaysOpen() {
        val saves = Saves().apply { fail = true }
        val chrome = testChrome(saveProjects = saves.save)
        compose.setContent { ProjectScreen(Projects(), chrome = chrome) }
        compose.menu(chrome, DesktopCommand.OpenProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Could not save project preference: disk full").assertExists()
        compose.runOnIdle { assertTrue(chrome.hasProject); assertTrue(chrome.enabled) }
    }
}
