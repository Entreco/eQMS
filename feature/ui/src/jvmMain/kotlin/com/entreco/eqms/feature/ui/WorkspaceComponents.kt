package com.entreco.eqms.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun WorkspaceItem(text: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        Modifier.fillMaxWidth()
            .background(if (selected) JewelTheme.globalColors.borders.normal.copy(alpha = 0.35f) else JewelTheme.globalColors.panelBackground)
            .onFocusChanged { focused = it.isFocused }
            .focusOutline(focused, RectangleShape, alignment = Stroke.Alignment.Inside)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
    )
}

@Composable
@OptIn(ExperimentalJewelApi::class)
fun WorkspaceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    textState: MutableState<TextFieldValue>? = null,
    onEdit: (TextFieldValue, TextFieldValue) -> Unit = { _, _ -> },
) {
    val localField = remember { mutableStateOf(TextFieldValue(value)) }
    var field by (textState ?: localField)
    val displayed = if (field.text == value) field else field.copy(text = value, selection = TextRange(value.length))
    val change: (TextFieldValue) -> Unit = {
        if (it.text != value) onEdit(displayed, it)
        field = it
        if (it.text != value) onValueChange(it.text)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = JewelTheme.globalColors.text.info)
        val input = Modifier.fillMaxWidth().semantics { contentDescription = label }
        if (singleLine) {
            TextField(displayed, change, input, enabled = enabled, readOnly = readOnly)
        } else {
            TextArea(
                displayed, change, input.heightIn(min = 100.dp).fillMaxHeight(),
                enabled = enabled, readOnly = readOnly,
                textStyle = JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

/**
 * When true, [WorkspaceDialog] opens a real, modal, draggable and resizable window with native chrome.
 * The in-scene fallback keeps Compose component tests able to see dialog content.
 */
val LocalDialogWindows = staticCompositionLocalOf { false }

@Composable
fun WorkspaceDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    windowTitle: String = "DutchDoxx",
    size: DpSize = DpSize(520.dp, 340.dp),
) {
    val body: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            title()
            Divider(Orientation.Horizontal)
            text()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                dismissButton()
                confirmButton()
            }
        }
    }
    if (!LocalDialogWindows.current) {
        Dialog(onDismissRequest = onDismissRequest) {
            body(
                Modifier.widthIn(min = 360.dp, max = 760.dp)
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.normal)
                    .padding(20.dp),
            )
        }
        return
    }
    DialogWindow(
        onCloseRequest = onDismissRequest,
        state = rememberDialogState(position = WindowPosition(androidx.compose.ui.Alignment.Center), size = size),
        title = windowTitle,
        resizable = true,
        onKeyEvent = {
            (it.key == Key.Escape && it.type == KeyEventType.KeyDown).also { escape -> if (escape) onDismissRequest() }
        },
    ) {
        SideEffect { window.minimumSize = java.awt.Dimension(420, 260) }
        ThemedWindowContent(windowTitle) {
            Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                title()
                Divider(Orientation.Horizontal)
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { text() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
