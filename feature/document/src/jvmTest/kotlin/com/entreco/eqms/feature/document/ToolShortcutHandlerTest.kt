package com.entreco.eqms.feature.document

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.entreco.eqms.feature.ui.WorkspaceTool
import kotlin.test.*
import org.junit.Test

class ToolShortcutHandlerTest {
    @Test
    fun bothPlatformsMatchAllToolsAndDisplayTheirActualShortcut() {
        for (mac in listOf(true, false)) {
            val handler = ToolShortcutHandler(mac)
            val observed = mutableListOf<WorkspaceTool>()
            for (tool in WorkspaceTool.entries) {
                assertEquals(if (mac) "\u2318${tool.number}" else "Alt+${tool.number}", tool.shortcut(mac))
                assertTrue(handler.handle(tool.key, KeyEventType.KeyDown, mac, !mac, false, false, true, observed::add))
                assertTrue(handler.handle(tool.key, KeyEventType.KeyUp, false, false, false, false, true, observed::add))
            }
            assertEquals(WorkspaceTool.entries.toList(), observed)
        }
    }

    @Test
    fun rejectsPlainDigitsOtherModifiersAndUnrelatedKeys() {
        for (mac in listOf(true, false)) {
            val handler = ToolShortcutHandler(mac)
            for (meta in listOf(true, false)) for (alt in listOf(true, false))
                for (ctrl in listOf(true, false)) for (shift in listOf(true, false)) {
                    if (!ctrl && !shift && meta == mac && alt != mac) continue
                    assertFalse(handler.handle(Key.One, KeyEventType.KeyDown, meta, alt, ctrl, shift, true) {
                        fail("Unexpected toggle for $mac $meta $alt $ctrl $shift")
                    })
                }
            assertFalse(handler.handle(Key.Five, KeyEventType.KeyDown, mac, !mac, false, false, true) {
                fail("Unrelated key")
            })
        }
    }

    @Test
    fun repeatedAndBlockedPressesNeverToggleLaterAndResetAllowsNewPress() {
        for (mac in listOf(true, false)) {
            val handler = ToolShortcutHandler(mac)
            val observed = mutableListOf<WorkspaceTool>()
            fun down(enabled: Boolean) =
                handler.handle(Key.One, KeyEventType.KeyDown, mac, !mac, false, false, enabled, observed::add)
            down(false)
            repeat(3) { assertTrue(down(true)) }
            assertTrue(observed.isEmpty(), "Blocked presses must not queue")
            handler.handle(Key.One, KeyEventType.KeyUp, false, false, false, false, true, observed::add)
            down(true)
            repeat(3) { down(true) }
            assertEquals(listOf(WorkspaceTool.Project), observed)
            handler.reset()
            down(true)
            assertEquals(listOf(WorkspaceTool.Project, WorkspaceTool.Project), observed)
        }
    }
}
