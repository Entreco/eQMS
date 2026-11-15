package com.entreco.eqms.feature.scaffold

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import kotlin.test.*
import org.junit.Test

class ScaffoldAboutIntegrationTest : ScaffoldDocumentTestSupport(nativeIntegration = true) {
    @Test
    fun settingsProjectChooserAndAboutDoNotToggleBackgroundTools() {
        val chrome = chrome()
        val projects = Projects()
        val documents = Documents()
        compose.setContent { ProjectScreen(projects, documents, chrome = chrome) }
        compose.menu(chrome, DesktopCommand.OpenProject)
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
        compose.menu(chrome, DesktopCommand.OpenProject)
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
        compose.menu(chrome, DesktopCommand.OpenProject)
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
}
