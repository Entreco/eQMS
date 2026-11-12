package com.entreco.eqms.feature.document

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Material Design icons (Apache License 2.0), 24 dp viewport, tinted by the caller. */
internal object EditorIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    }.build()

    val Bold = icon("Bold", "M15.6,10.79c0.97,-0.67 1.65,-1.77 1.65,-2.79 0,-2.26 -1.75,-4 -4,-4H7v14h7.04c2.09,0 3.71,-1.7 3.71,-3.79 0,-1.52 -0.86,-2.82 -2.15,-3.42zM10,6.5h3c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5h-3v-3zM13.5,15.5L10,15.5v-3h3.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5z")
    val Italic = icon("Italic", "M10,4v3h2.21l-3.42,8H6v3h8v-3h-2.21l3.42,-8H18V4z")
    val Strike = icon("Strikethrough", "M10,19h4v-3h-4v3zM5,4v3h5v3h4V7h5V4H5zM3,14h18v-2H3v2z")
    val Code = icon("Code", "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z")
    val Link = icon("Link", "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4L11,7L7,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9L7,15.1c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2L8,11v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4L13,17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z")
    val Bullets = icon("Bullets", "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5 -0.67,-1.5 -1.5,-1.5zM4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 4.83,4.5 4,4.5zM4,16.5c-0.83,0 -1.5,0.68 -1.5,1.5s0.68,1.5 1.5,1.5 1.5,-0.68 1.5,-1.5 -0.67,-1.5 -1.5,-1.5zM7,19h14v-2L7,17v2zM7,13h14v-2L7,11v2zM7,5v2h14L21,5L7,5z")
    val Numbered = icon("Numbered", "M2,17h2v0.5L3,17.5v1h1v0.5L2,19v1h3v-4L2,16v1zM3,8h1L4,4L2,4v1h1v3zM2,11h1.8L2,13.1v0.9h3v-1L3.2,13L5,10.9L5,10L2,10v1zM7,5v2h14L21,5L7,5zM7,19h14v-2L7,17v2zM7,13h14v-2L7,11v2z")
    val Quote = icon("Quote", "M6,17h3l2,-4L11,7L5,7v6h3zM14,17h3l2,-4L19,7h-6v6h3z")
    val Table = icon("Table", "M3,3v18h18L21,3L3,3zM8,19L5,19v-3h3v3zM8,14L5,14v-3h3v3zM8,9L5,9L5,6h3v3zM13,19h-3v-3h3v3zM13,14h-3v-3h3v3zM13,9h-3L10,6h3v3zM19,19h-5v-3h5v3zM19,14h-5v-3h5v3zM19,9h-5L14,6h5v3z")
    val Rule = icon("Rule", "M4,11h16v2H4z")
    val PageBreak = icon("PageBreak", "M4,11h3v2H4zM10.5,11h3v2h-3zM17,11h3v2h-3zM8,4h8v5H8zM8,15h8v5H8z")
    val Undo = icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    val Redo = icon("Redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z")
}
