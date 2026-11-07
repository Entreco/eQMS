package com.entreco.eqms.feature.document

data class RenderNode(
    val kind: String,
    val text: String = "",
    val target: String = "",
    val anchor: String = "",
    val children: List<RenderNode> = emptyList(),
) {
    fun plainText(): String = text + children.joinToString("") { it.plainText() }
}

data class RenderHeading(val level: Int, val title: String, val anchor: String)
data class RenderSource(val kind: String, val id: String, val revision: String, val contentHash: String)
data class RenderedDocument(
    val title: String,
    val inputDigest: String,
    val source: RenderSource,
    val nodes: List<RenderNode>,
    val toc: List<RenderHeading>,
    val diagnostics: List<String>,
)
data class DocumentImage(val bytes: ByteArray, val width: Int, val height: Int)

enum class DocumentViewMode(val label: String) {
    Source("Markdown"), Split("Markdown + Preview"), Preview("Preview"),
}

enum class PageSize(val label: String, val widthMm: Double, val heightMm: Double) {
    A4("A4", 210.0, 297.0),
    Letter("US Letter", 215.9, 279.4),
}

enum class PageOrientation { Portrait, Landscape }

data class DocumentPageSetup(
    val size: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.Portrait,
    val marginTopMm: Double = 25.4,
    val marginRightMm: Double = 25.4,
    val marginBottomMm: Double = 25.4,
    val marginLeftMm: Double = 25.4,
    val headerTemplate: String = "{title}",
    val footerTemplate: String = "Page {page} of {total}",
) {
    val pageWidthMm: Double get() = if (orientation == PageOrientation.Portrait) size.widthMm else size.heightMm
    val pageHeightMm: Double get() = if (orientation == PageOrientation.Portrait) size.heightMm else size.widthMm

    fun validate(): DocumentPageSetup {
        val margins = listOf(marginTopMm, marginRightMm, marginBottomMm, marginLeftMm)
        require(margins.all { it in 5.0..50.0 }) { "Page margins must be between 5.0 mm and 50.0 mm." }
        require(marginLeftMm + marginRightMm <= pageWidthMm - 40.0) {
            "Horizontal margins leave no editable area."
        }
        require(marginTopMm + marginBottomMm <= pageHeightMm - 60.0) {
            "Vertical margins leave no editable area."
        }
        require(headerTemplate.length <= 120 && footerTemplate.length <= 120) {
            "Header and footer templates must be at most 120 characters."
        }
        return this
    }
}

data class DocumentRenderTiming(val inputDigest: String, val elapsedMillis: Double)
