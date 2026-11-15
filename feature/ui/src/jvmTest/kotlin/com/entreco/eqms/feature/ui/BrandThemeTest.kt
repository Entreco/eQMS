package com.entreco.eqms.feature.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

class BrandThemeTest {
    @get:Rule val compose = createComposeRule()

    private val tokens = BrandTokens.parse("Acme", "#C2410C", "#FFFFFF", BrandLogo.Generated("s")).getOrThrow()

    @Test
    fun providesTokensInLightAndDarkThemes() {
        for (dark in listOf(false, true)) {
            var accent = Color.Unspecified
            var onAccent = Color.Unspecified
            compose.setContent {
                IntUiTheme(isDark = dark) {
                    BrandTheme(tokens) { accent = Brand.accent; onAccent = Brand.onAccent }
                }
            }
            compose.waitForIdle()
            assertEquals(tokens.accent, accent)
            assertEquals(tokens.onAccent, onAccent)
        }
    }

    @Test
    fun withoutTokensAccentDerivesFromTheActiveTheme() {
        for (dark in listOf(false, true)) {
            var accent = Color.Unspecified
            var focus = Color.Unspecified
            var current: BrandTokens? = tokens
            compose.setContent {
                IntUiTheme(isDark = dark) {
                    BrandTheme(null) {
                        accent = Brand.accent
                        focus = JewelTheme.globalColors.outlines.focused
                        current = Brand.tokens
                    }
                }
            }
            compose.waitForIdle()
            assertEquals(focus, accent)
            assertNull(current)
        }
    }
}
