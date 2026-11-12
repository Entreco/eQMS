package com.entreco.eqms.feature.document

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
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
    rulers: Boolean = false,
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
    Box(modifier.width(width)) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = minHeight)
                .shadow(3.dp, RectangleShape).background(Paper.sheet).border(1.dp, Paper.rule),
        ) { content(margins) }
        if (rulers) Rulers(setup, Modifier.matchParentSize())
    }
}

private val RulerThickness = 18.dp

/** Millimetre rulers drawn just outside the top and left edges; the white part marks the text area between the margins. */
@Composable
private fun Rulers(setup: DocumentPageSetup, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = Paper.muted, fontSize = 8.sp)
    val band = Color(0xFFC4C7CC)
    Canvas(modifier) {
        val t = RulerThickness.toPx()
        val pxPerMm = size.width / setup.pageWidthMm.toFloat()
        fun ticks(lengthMm: Float, horizontal: Boolean) {
            for (mm in 0..lengthMm.toInt()) {
                val at = mm * pxPerMm
                val len = if (mm % 10 == 0) t * 0.55f else if (mm % 5 == 0) t * 0.35f else t * 0.18f
                if (horizontal) drawLine(Paper.muted, Offset(at, -len), Offset(at, 0f), 1f)
                else drawLine(Paper.muted, Offset(-len, at), Offset(0f, at), 1f)
                if (mm % 10 == 0 && mm > 0) {
                    val text = (mm / 10).toString()
                    val size = measurer.measure(text, label).size
                    val corner = if (horizontal) Offset(at - size.width / 2f, -t * 0.95f) else Offset(-t * 0.95f, at - size.height / 2f)
                    drawText(measurer, text, corner, label)
                }
            }
        }
        // Horizontal ruler
        drawRect(band, Offset(0f, -t), Size(size.width, t))
        drawRect(Color.White, Offset(setup.marginLeftMm.toFloat() * pxPerMm, -t), Size(size.width - (setup.marginLeftMm.toFloat() + setup.marginRightMm.toFloat()) * pxPerMm, t))
        ticks(setup.pageWidthMm.toFloat(), true)
        // Vertical ruler
        drawRect(band, Offset(-t, 0f), Size(t, size.height))
        val textHeight = size.height - (setup.marginTopMm.toFloat() + setup.marginBottomMm.toFloat()) * pxPerMm
        drawRect(Color.White, Offset(-t, setup.marginTopMm.toFloat() * pxPerMm), Size(t, textHeight.coerceAtLeast(0f)))
        ticks(size.height / pxPerMm, false)
    }
}

internal data class Margins(val left: Dp, val right: Dp, val top: Dp, val bottom: Dp)
