package com.entreco.eqms.feature.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

enum class WorkspaceTool(val isLeft: Boolean, val number: Int, val key: Key, val icon: IconKey) {
    Project(true, 1, Key.One, AllIconsKeys.Toolwindows.ToolWindowProject),
    Search(true, 2, Key.Two, AllIconsKeys.Toolwindows.ToolWindowFind),
    Review(false, 3, Key.Three, AllIconsKeys.Actions.Checked),
    History(false, 4, Key.Four, AllIconsKeys.General.History);

    fun shortcut(isMac: Boolean): String = if (isMac) "\u2318$number" else "Alt+$number"
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun WorkspaceToolButton(
    tool: WorkspaceTool,
    selected: Boolean,
    enabled: Boolean,
    isMac: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tooltip(tooltip = {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tool.name)
            Text(tool.shortcut(isMac), color = JewelTheme.globalColors.text.info)
        }
    }) {
        SelectableIconButton(
            selected = selected, enabled = enabled, onClick = onClick,
            modifier = modifier.size(36.dp).semantics {
                contentDescription = "Toggle ${tool.name} panel"
            },
        ) {
            Icon(tool.icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
