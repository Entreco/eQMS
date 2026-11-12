package com.entreco.eqms.feature.document

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme

internal val RulerThickness: Dp = 22.dp

/**
 * Millimetre rulers on all four edges of the desk. [sheets] are the on-screen page rectangles in desk
 * coordinates; the lighter stretch of each ruler marks the text area between the page margins.
 */
@Composable
internal fun DeskRulers(setup: DocumentPageSetup, sheets: State<Map<Int, Rect>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colors = JewelTheme.globalColors
    val dark = JewelTheme.isDark
    val band = colors.borders.normal.copy(alpha = 0.3f).compositeOver(colors.panelBackground)
    val tick = colors.text.info
    val line = colors.borders.normal
    val textArea = if (dark) Color.White.copy(alpha = 0.12f) else Color.White
    val label = TextStyle(color = tick, fontSize = 9.sp)
    Canvas(modifier) {
        val t = RulerThickness.toPx()
        val pages = sheets.value.values.sortedBy { it.top }
        val first = pages.firstOrNull() ?: return@Canvas
        val pxPerMm = first.width / setup.pageWidthMm.toFloat()
        val left = setup.marginLeftMm.toFloat() * pxPerMm
        val right = setup.marginRightMm.toFloat() * pxPerMm

        fun horizontal(y: Float, outward: Float) {
            clipRect(0f, y, size.width, y + t) {
                drawRect(band, Offset(0f, y), Size(size.width, t))
                drawRect(textArea, Offset(first.left + left, y), Size(first.width - left - right, t))
                val from = ((t - first.left) / pxPerMm).toInt().coerceAtLeast(0)
                val to = ((size.width - first.left) / pxPerMm).toInt().coerceAtMost(setup.pageWidthMm.toInt())
                for (mm in from..to) {
                    val x = first.left + mm * pxPerMm
                    val len = t * if (mm % 10 == 0) 0.5f else if (mm % 5 == 0) 0.32f else 0.16f
                    val base = if (outward < 0) y + t else y
                    drawLine(tick, Offset(x, base), Offset(x, base + outward * len), 1f)
                    if (mm % 10 == 0 && mm > 0) {
                        val text = (mm / 10).toString()
                        val m = measurer.measure(text, label).size
                        drawText(measurer, text, Offset(x - m.width / 2f, y + (t - m.height) / 2f - outward * 1.5f), label)
                    }
                }
            }
            drawLine(line, Offset(0f, if (outward < 0) y + t else y), Offset(size.width, if (outward < 0) y + t else y), 1f)
        }

        fun vertical(x: Float, outward: Float) {
            clipRect(x, t, x + t, size.height - t) {
                drawRect(band, Offset(x, t), Size(t, size.height - 2 * t))
                for (page in pages) {
                    val top = page.top + setup.marginTopMm.toFloat() * pxPerMm
                    val bottom = page.bottom - setup.marginBottomMm.toFloat() * pxPerMm
                    if (bottom > top) drawRect(textArea, Offset(x, top), Size(t, bottom - top))
                    val from = ((t - page.top) / pxPerMm).toInt().coerceAtLeast(0)
                    val to = ((size.height - page.top) / pxPerMm).toInt().coerceAtMost((page.height / pxPerMm).toInt())
                    for (mm in from..to) {
                        val y = page.top + mm * pxPerMm
                        val len = t * if (mm % 10 == 0) 0.5f else if (mm % 5 == 0) 0.32f else 0.16f
                        val base = if (outward < 0) x + t else x
                        drawLine(tick, Offset(base, y), Offset(base + outward * len, y), 1f)
                        if (mm % 10 == 0 && mm > 0) {
                            val text = (mm / 10).toString()
                            val m = measurer.measure(text, label).size
                            drawText(measurer, text, Offset(x + (t - m.width) / 2f - outward * 1.5f, y - m.height / 2f), label)
                        }
                    }
                }
            }
            drawLine(line, Offset(if (outward < 0) x + t else x, t), Offset(if (outward < 0) x + t else x, size.height - t), 1f)
        }

        vertical(0f, -1f)
        vertical(size.width - t, 1f)
        horizontal(0f, -1f)
        horizontal(size.height - t, 1f)
    }
}
