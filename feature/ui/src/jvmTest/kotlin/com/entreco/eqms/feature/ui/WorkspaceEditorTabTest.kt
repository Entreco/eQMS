package com.entreco.eqms.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class WorkspaceEditorTabTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactTabHasSelectionKeyboardAndIndependentCloseWithoutSelectingOrBypassingDisabledState() {
        var selected by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var selections = 0
        var closes = 0
        compose.setContent {
            IntUiTheme(isDark = true) {
                WorkspaceEditorTab(
                    "Policy", "Document tab Policy", "Close tab Policy",
                    selected, enabled, enabled, { selections++; selected = true }, { closes++ },
                    dirty = true,
                )
            }
        }
        val tab = compose.onNodeWithContentDescription("Document tab Policy")
        val close = compose.onNodeWithContentDescription("Close tab Policy")
        tab.assertHeightIsEqualTo(30.dp).assertIsNotSelected()
        compose.onNodeWithText("Policy").assertExists()
        compose.onNodeWithContentDescription("Unsaved changes", useUnmergedTree = true).assertExists()
        close.performClick()
        compose.runOnIdle { assertEquals(1, closes); assertEquals(0, selections) }
        tab.assertIsNotSelected().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        tab.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        tab.assertIsSelected()
        close.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        close.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(2, closes); assertEquals(1, selections); enabled = false }
        tab.assertIsNotEnabled()
        close.assertIsNotEnabled()
    }
}
