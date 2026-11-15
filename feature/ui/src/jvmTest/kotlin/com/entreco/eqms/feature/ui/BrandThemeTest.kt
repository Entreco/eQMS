package com.entreco.eqms.feature.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.junit.Rule
import org.junit.Test

class BrandThemeTest {
    @get:Rule val compose = createComposeRule()

    private val tokens = BrandTokens.parse("Acme", "#C2410C", "#FFFFFF", BrandLogo.Generated("s")).getOrThrow()

    @Test
    fun defaultButtonsUseTheProjectAccentAndThemeStyleWithoutBranding() {
        for (dark in listOf(false, true)) {
            var branded: androidx.compose.ui.graphics.Brush? = null
            var content = Color.Unspecified
            var plain: androidx.compose.ui.graphics.Brush? = null
            compose.setContent {
                IntUiTheme(isDark = dark) {
                    BrandTheme(tokens) {
                        branded = JewelTheme.defaultButtonStyle.colors.background
                        content = JewelTheme.defaultButtonStyle.colors.content
                    }
                    BrandTheme(null) { plain = JewelTheme.defaultButtonStyle.colors.background }
                }
            }
            compose.waitForIdle()
            assertEquals(androidx.compose.ui.graphics.SolidColor(tokens.accent), branded)
            assertEquals(tokens.onAccent, content)
            assert(plain != branded)
        }
    }

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
