package com.entreco.eqms.feature.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

class JewelThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun standaloneThemeRendersOnJbr() {
        compose.setContent { IntUiTheme(isDark = true) { Text("Jewel desktop") } }
        compose.onNodeWithText("Jewel desktop").assertExists()
    }
}
