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
import androidx.compose.ui.window.Dialog
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
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    val displayed = if (field.text == value) field else field.copy(text = value, selection = TextRange(value.length))
    val change: (TextFieldValue) -> Unit = {
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

@Composable
fun WorkspaceDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            Modifier.widthIn(min = 360.dp, max = 760.dp)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            title()
            Divider(Orientation.Horizontal)
            text()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                dismissButton()
                confirmButton()
            }
        }
    }
}
