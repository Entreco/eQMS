package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

class LogoPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun drawsEveryGeneratedShape() {
        val base = testBrandingPreview(BrandingDraft("Acme")).getOrThrow()
        compose.setContent {
            IntUiTheme(isDark = false) {
                Column {
                    for (shape in GeneratedLogoShape.entries) {
                        LogoPreview(base.copy(generated = GeneratedLogo("AC", shape)), logoFile = null)
                    }
                }
            }
        }
        compose.onAllNodesWithContentDescription("Logo preview for Acme").assertCountEquals(3)
    }

    @Test
    fun fallsBackToTheGeneratedLogoWhenThePngCannotBeRead() {
        val preview = testBrandingPreview(BrandingDraft("Acme")).getOrThrow()
        compose.setContent { IntUiTheme(isDark = false) { LogoPreview(preview, logoFile = "/definitely/missing.png") } }
        compose.onNodeWithContentDescription("Logo preview for Acme").assertExists()
    }
}
