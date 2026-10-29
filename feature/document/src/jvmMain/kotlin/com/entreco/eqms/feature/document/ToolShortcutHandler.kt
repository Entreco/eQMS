package com.entreco.eqms.feature.document

import androidx.compose.ui.input.key.*
import com.entreco.eqms.feature.ui.WorkspaceTool

internal class ToolShortcutHandler(private val isMac: Boolean) {
    private val held = mutableSetOf<Key>()

    fun reset() = held.clear()

    fun handle(event: KeyEvent, enabled: Boolean, toggle: (WorkspaceTool) -> Unit): Boolean =
        handle(event.key, event.type, event.isMetaPressed, event.isAltPressed,
            event.isCtrlPressed, event.isShiftPressed, enabled, toggle)

    internal fun handle(
        key: Key, type: KeyEventType, meta: Boolean, alt: Boolean, ctrl: Boolean, shift: Boolean,
        enabled: Boolean, toggle: (WorkspaceTool) -> Unit,
    ): Boolean {
        val tool = WorkspaceTool.entries.find { it.key == key } ?: return false
        val matches = !ctrl && !shift && if (isMac) meta && !alt else alt && !meta
        if (type == KeyEventType.KeyUp) return held.remove(key)
        if (type != KeyEventType.KeyDown) return false
        if (key in held) return true
        if (!matches) return false
        held.add(key)
        if (enabled) toggle(tool)
        return true
    }
}
