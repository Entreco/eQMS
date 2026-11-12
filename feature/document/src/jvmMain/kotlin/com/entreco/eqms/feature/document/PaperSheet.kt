package com.entreco.eqms.feature.document

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** Paper colours are fixed so pages stay white with dark ink in light and dark IDE themes. */
internal object Paper {
    val sheet = Color(0xFFFFFFFF)
    val ink = Color(0xFF1F2328)
    val muted = Color(0xFF59636E)
    val rule = Color(0xFFD1D9E0)
    val codeBackground = Color(0xFFF1F3F5)
    val link = Color(0xFF0B57D0)

    @Composable
    fun desk(): Color = if (JewelTheme.isDark) Color(0xFF2B2D30) else Color(0xFFDADCE0)

    const val DpPerMm = 96f / 25.4f
}

/** A white page sheet at the page-setup proportions, scaled down to fit [availableWidth]. */
@Composable
internal fun PaperSheet(
    setup: DocumentPageSetup,
    availableWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(Margins) -> Unit,
) {
    val natural = (setup.pageWidthMm * Paper.DpPerMm).dp
    val width = if (availableWidth < natural) availableWidth else natural
    val scale = width.value / natural.value
    val margins = Margins(
        (setup.marginLeftMm * Paper.DpPerMm * scale).dp, (setup.marginRightMm * Paper.DpPerMm * scale).dp,
        (setup.marginTopMm * Paper.DpPerMm * scale).dp, (setup.marginBottomMm * Paper.DpPerMm * scale).dp,
    )
    val minHeight = (setup.pageHeightMm * Paper.DpPerMm * scale).dp
    Column(
        modifier.width(width).heightIn(min = minHeight)
            .shadow(3.dp, RectangleShape).background(Paper.sheet).border(1.dp, Paper.rule),
    ) { content(margins) }
}

internal data class Margins(val left: Dp, val right: Dp, val top: Dp, val bottom: Dp)
