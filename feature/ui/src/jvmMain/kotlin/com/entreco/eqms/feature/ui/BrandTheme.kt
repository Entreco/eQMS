package com.entreco.eqms.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultButtonStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.jetbrains.jewel.foundation.theme.JewelTheme

private val LocalBrandTokens = compositionLocalOf<BrandTokens?> { null }

/** Layers project brand tokens over the surrounding Jewel theme; `null` keeps the theme's own accent. */
@Composable
fun BrandTheme(tokens: BrandTokens?, content: @Composable () -> Unit) {
    val base = JewelTheme.defaultButtonStyle
    val style = remember(tokens, base) { tokens?.let { base.withAccent(it.accent, it.onAccent) } ?: base }
    CompositionLocalProvider(
        LocalBrandTokens provides tokens,
        LocalDefaultButtonStyle provides style,
        content = content,
    )
}

/** Recolours the primary (default) button with the project accent; disabled colours stay Jewel's. */
internal fun ButtonStyle.withAccent(accent: Color, onAccent: Color): ButtonStyle {
    val shade = if (accent.luminance() > 0.5f) Color.Black else Color.White
    fun fill(amount: Float) = SolidColor(lerp(accent, shade, amount))
    return ButtonStyle(
        colors = ButtonColors(
            background = fill(0f), backgroundDisabled = colors.backgroundDisabled,
            backgroundFocused = fill(0f), backgroundPressed = fill(0.22f), backgroundHovered = fill(0.12f),
            content = onAccent, contentDisabled = colors.contentDisabled,
            contentFocused = onAccent, contentPressed = onAccent, contentHovered = onAccent,
            border = fill(0f), borderDisabled = colors.borderDisabled,
            borderFocused = fill(0f), borderPressed = fill(0.22f), borderHovered = fill(0.12f),
        ),
        metrics = metrics,
        focusOutlineAlignment = focusOutlineAlignment,
    )
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
