package com.entreco.eqms.feature.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.math.abs

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

    @Test
    fun hintsOpenInwardAndStayVerticallyCenteredRegardlessOfPointerHeight() {
        compose.setContent {
            IntUiTheme(isDark = true) {
                Row(Modifier.requiredSize(500.dp, 300.dp).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    WorkspaceToolButton(WorkspaceTool.Project, true, true, true, {})
                    WorkspaceToolButton(WorkspaceTool.History, false, true, true, {})
                }
            }
        }
        for (tool in listOf(WorkspaceTool.Project, WorkspaceTool.History)) {
            val button = compose.onNodeWithContentDescription("Toggle ${tool.name} panel")
            for (height in listOf(2f, 33f)) {
                button.performMouseInput { enter(Offset(center.x, height)); moveTo(Offset(center.x, height)) }
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithText(tool.shortcut(true)).fetchSemanticsNodes().isNotEmpty()
                }
                val icon = button.fetchSemanticsNode().boundsInRoot
                val hint = compose.onNodeWithText(tool.name).fetchSemanticsNode().boundsInRoot
                assertTrue(abs(icon.center.y - hint.center.y) <= 1f, "$tool: icon=$icon hint=$hint")
                if (tool.isLeft) assertTrue(hint.left >= icon.right + 7f)
                else assertTrue(hint.right <= icon.left - 7f)
                button.performMouseInput { exit(Offset(-10f, -10f)) }
                compose.waitUntil(5_000) { compose.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty() }
            }
        }
    }
}
