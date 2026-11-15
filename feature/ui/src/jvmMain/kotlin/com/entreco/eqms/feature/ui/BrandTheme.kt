package com.entreco.eqms.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.jetbrains.jewel.foundation.theme.JewelTheme

private val LocalBrandTokens = compositionLocalOf<BrandTokens?> { null }

/** Layers project brand tokens over the surrounding Jewel theme; `null` keeps the theme's own accent. */
@Composable
fun BrandTheme(tokens: BrandTokens?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrandTokens provides tokens, content = content)
}

object Brand {
    val tokens: BrandTokens?
        @Composable @ReadOnlyComposable get() = LocalBrandTokens.current

    val accent: Color
        @Composable @ReadOnlyComposable get() = tokens?.accent ?: JewelTheme.globalColors.outlines.focused

    val onAccent: Color
        @Composable @ReadOnlyComposable get() =
            tokens?.onAccent ?: if (accent.luminance() > 0.5f) Color.Black else Color.White
}
