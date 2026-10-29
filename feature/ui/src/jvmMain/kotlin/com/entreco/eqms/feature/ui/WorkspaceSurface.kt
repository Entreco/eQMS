package com.entreco.eqms.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun workspaceSurfaceColor(): Color {
    val shell = JewelTheme.globalColors.panelBackground
    return if (shell.luminance() < 0.5f) lerp(shell, Color.Black, 0.24f)
    else lerp(shell, Color.White, 0.95f)
}
