package com.entreco.eqms.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class WorkspaceEditorTabTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun wholeTabChangesOnHoverIncludingCloseChildButNotWhenDisabledInBothThemes() {
        var dark by mutableStateOf(true)
        var selected by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        compose.setContent {
            IntUiTheme(isDark = dark) {
                Box(Modifier.background(JewelTheme.globalColors.panelBackground).padding(20.dp)) {
                    WorkspaceEditorTab("Policy", "Document tab Policy", "Close tab Policy",
                        selected, enabled, enabled, {}, {})
                }
            }
        }
        val tab = compose.onNodeWithContentDescription("Document tab Policy")
        val close = compose.onNodeWithContentDescription("Close tab Policy")
        fun pixel() = tab.captureToImage().toPixelMap().let { it[8, 3] }
        for (theme in listOf(true, false)) for (active in listOf(true, false)) {
            compose.runOnIdle { dark = theme; selected = active; enabled = true }
            tab.performMouseInput { moveTo(Offset(-10f, -10f)) }
            val normal = pixel()
            tab.performMouseInput { moveTo(Offset(30f, 4f)) }
            val hovered = pixel()
            assertTrue(abs(normal.red - hovered.red) > 0.03f, "Whole tab must visibly change")
            close.performMouseInput { moveTo(center) }
            assertEquals(hovered, pixel(), "Close hover must retain the whole-tab highlight")
            compose.runOnIdle { enabled = false }
            tab.performMouseInput { moveTo(Offset(-10f, -10f)) }
            val disabled = pixel()
            tab.performMouseInput { moveTo(Offset(30f, 4f)) }
            assertEquals(disabled, pixel(), "Disabled tabs must not highlight")
        }
    }

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
