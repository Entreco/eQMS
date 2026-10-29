package com.entreco.eqms.feature.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

class WorkspaceToolButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactIconOnlyButtonsExposeSelectionFocusAndDisabledStateInBothThemes() {
        var dark by mutableStateOf(true)
        var selected by mutableStateOf(WorkspaceTool.Project)
        var enabled by mutableStateOf(true)
        compose.setContent {
            IntUiTheme(isDark = dark) {
                Column {
                    WorkspaceTool.entries.forEach { tool ->
                        WorkspaceToolButton(tool, selected == tool, enabled, true, { selected = tool })
                    }
                }
            }
        }
        for (theme in listOf(true, false)) {
            compose.runOnIdle { dark = theme; enabled = true }
            for (tool in WorkspaceTool.entries) {
                val button = compose.onNodeWithContentDescription("Toggle ${tool.name} panel")
                button.assertWidthIsEqualTo(36.dp)
                compose.onNodeWithText(tool.name).assertDoesNotExist()
                button.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                button.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
                button.assertIsSelected()
            }
            compose.runOnIdle { enabled = false }
            compose.onNodeWithContentDescription("Toggle Project panel").assertIsNotEnabled()
        }
    }

    @Test
    fun realHoverShowsNameAndPlatformShortcutAndExitDismissesIt() {
        var mac by mutableStateOf(true)
        compose.setContent {
            IntUiTheme(isDark = true) {
                WorkspaceToolButton(WorkspaceTool.Project, true, true, mac, {})
            }
        }
        val button = compose.onNodeWithContentDescription("Toggle Project panel")
        for (platform in listOf(true, false)) {
            compose.runOnIdle { mac = platform }
            button.performMouseInput { enter(center); moveTo(center) }
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(WorkspaceTool.Project.shortcut(platform)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Project").assertExists()
            button.performMouseInput { exit(Offset(-10f, -10f)) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Project").fetchSemanticsNodes().isEmpty() }
        }
    }
}
