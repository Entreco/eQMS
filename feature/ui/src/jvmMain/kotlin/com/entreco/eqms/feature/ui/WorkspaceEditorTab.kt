package com.entreco.eqms.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.editorTabStyle

@Composable
fun WorkspaceEditorTab(
    title: String,
    description: String,
    closeDescription: String,
    selected: Boolean,
    enabled: Boolean,
    closeEnabled: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    dirty: Boolean = false,
    historical: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val state = TabState.of(selected = selected, active = true, hovered = hovered)
    val background by JewelTheme.editorTabStyle.colors.backgroundFor(state)
    val foreground by JewelTheme.editorTabStyle.colors.contentFor(state)
    val shape = RoundedCornerShape(5.dp)
    val reveal = remember { BringIntoViewRequester() }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(selected, measuredSize) {
        if (selected && measuredSize.width > 0) reveal.bringIntoView()
    }
    Row(
        Modifier.bringIntoViewRequester(reveal).onSizeChanged { measuredSize = it }
            .height(30.dp).widthIn(max = 280.dp)
            .alpha(if (enabled) 1f else 0.5f).clip(shape).background(background)
            .border(if (selected) 1.dp else 0.dp,
                if (selected) JewelTheme.globalColors.borders.normal else androidx.compose.ui.graphics.Color.Transparent, shape)
            .focusOutline(focused, shape)
            .selectable(selected, enabled = enabled, role = Role.Tab,
                interactionSource = interactions, indication = null, onClick = onSelect)
            .semantics { contentDescription = description }
            .padding(start = 8.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AllIconsKeys.FileTypes.Markdown, null, Modifier.size(16.dp))
        Text(title, Modifier.weight(1f, fill = false), color = foreground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (dirty) Text("*", color = foreground, modifier = Modifier.semantics {
            contentDescription = "Unsaved changes"
        })
        if (historical) Text("[saved revision]", color = foreground)
        IconButton(onClick = onClose, enabled = closeEnabled,
            modifier = Modifier.size(22.dp).semantics { contentDescription = closeDescription }) {
            Icon(JewelTheme.editorTabStyle.icons.close, null, Modifier.size(12.dp))
        }
    }
}
