package com.entreco.eqms.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class ProjectScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private class Capabilities : ProjectCapabilities {
        val operations = mutableListOf<Pair<String, Boolean>>()
        val folders = mutableListOf<Pair<String, String>>()
        var workspaceResult: CompletableDeferred<Result<String>>? = null

        override suspend fun listDirectory(path: String?): Result<DirectoryListing> =
            if (path == "/missing") {
                Result.failure(IllegalArgumentException("Directory not found"))
            } else {
                Result.success(
                    DirectoryListing(path ?: "/home", if (path == null) null else "/home", emptyList()),
                )
            }

        override suspend fun createFolder(parent: String, name: String): Result<String> {
            if (name == "existing") return Result.failure(IllegalArgumentException("Already exists"))
            folders += parent to name
            return Result.success("$parent/$name")
        }

        override suspend fun openProject(path: String, create: Boolean): Result<String> {
            operations += path to create
            return workspaceResult?.await() ?: Result.success(path)
        }
    }

    @Test
    fun createFolderNavigatesAndInitializesOnlyAfterSelection() {
        val capabilities = Capabilities()
        compose.setContent { ProjectScreen(capabilities) }
        compose.onNodeWithText("Create project").performClick()
        compose.onNodeWithText("New folder").performClick()
        compose.onNodeWithText("Folder name").performTextInput("project with spaces")
        compose.onNodeWithText("Create folder").performClick()
        compose.onAllNodesWithText("/home/project with spaces").onFirst().assertExists()
        compose.runOnIdle {
            assertEquals(listOf("/home" to "project with spaces"), capabilities.folders)
            assertTrue(capabilities.operations.isEmpty())
        }
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project created successfully.").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("/home/project with spaces" to true), capabilities.operations)
        }
    }

    @Test
    fun openHasNoFolderCreationAndUsesVerification() {
        val capabilities = Capabilities()
        compose.setContent { ProjectScreen(capabilities) }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("New folder").assertDoesNotExist()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project verified and opened.").assertExists()
        compose.runOnIdle { assertEquals(listOf("/home" to false), capabilities.operations) }
    }

    @Test
    fun failedNavigationCannotSelectPreviousDirectory() {
        val capabilities = Capabilities()
        compose.setContent { ProjectScreen(capabilities) }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Directory path").performTextReplacement("/missing")
        compose.onNodeWithText("Select directory").assertIsNotEnabled()
        compose.onNodeWithText("Go").performClick()
        compose.onNodeWithText("Directory not found").assertExists()
        compose.onNodeWithText("Select directory").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(capabilities.operations.isEmpty()) }
    }

    @Test
    fun folderFailureIsVisibleAndCancellationDoesNotInitialize() {
        val capabilities = Capabilities()
        compose.setContent { ProjectScreen(capabilities) }
        compose.onNodeWithText("Create project").performClick()
        compose.onNodeWithText("New folder").performClick()
        compose.onNodeWithText("Folder name").performTextInput("existing")
        compose.onNodeWithText("Create folder").performClick()
        compose.onNodeWithText("Already exists").assertExists()
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertTrue(capabilities.folders.isEmpty())
            assertTrue(capabilities.operations.isEmpty())
        }
    }

    @Test
    fun cancellingAfterCreatingFolderDoesNotInitialize() {
        val capabilities = Capabilities()
        compose.setContent { ProjectScreen(capabilities) }
        compose.onNodeWithText("Create project").performClick()
        compose.onNodeWithText("New folder").performClick()
        compose.onNodeWithText("Folder name").performTextInput("kept folder")
        compose.onNodeWithText("Create folder").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Project selection cancelled.").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("/home" to "kept folder"), capabilities.folders)
            assertTrue(capabilities.operations.isEmpty())
        }
    }

    @Test
    fun workspaceOperationPreventsDuplicateSubmissionsAndShowsErrors() {
        val capabilities = Capabilities()
        val result = CompletableDeferred<Result<String>>()
        capabilities.workspaceResult = result
        compose.setContent { ProjectScreen(capabilities) }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Working...").assertExists()
        compose.onNodeWithText("Create project").assertIsNotEnabled()
        compose.onNodeWithText("Open project").assertIsNotEnabled()
        compose.runOnIdle { result.complete(Result.failure(IllegalArgumentException("Invalid workspace"))) }
        compose.onNodeWithText("Invalid workspace").assertExists()
        compose.onNodeWithText("Project verified and opened.").assertDoesNotExist()
        compose.onNodeWithText("Open project").assertIsEnabled()
        compose.runOnIdle { assertEquals(1, capabilities.operations.size) }
    }
}
