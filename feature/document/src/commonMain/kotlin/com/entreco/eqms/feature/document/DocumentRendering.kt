package com.entreco.eqms.feature.document

data class RenderNode(
    val kind: String,
    val text: String = "",
    val target: String = "",
    val anchor: String = "",
    val children: List<RenderNode> = emptyList(),
    val start: Int = -1,
    val end: Int = -1,
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
    /** Resolved template style from the core (EQMS-115); null when none was requested. */
    val style: DocumentStyle? = null,
)
data class DocumentImage(val bytes: ByteArray, val width: Int, val height: Int)

/** Template identity pinned in a document or chosen for a preview. */
data class TemplatePin(val id: String, val version: Int, val hash: String)
data class TemplateSummary(val id: String, val version: Int, val name: String, val documentType: String, val source: String)

/** What to preview with: the document's own pin, or an installed template chosen for preview only. */
data class StyleRequest(val saved: DocumentSnapshot? = null, val chosen: TemplateSummary? = null)

enum class StyleFamily { Sans, Serif, Mono }
enum class StyleTable { Plain, Grid, Banded }
enum class StyleAlign { Left, Center }
enum class StyleTocPlacement { Cover, AfterCover, Start }
enum class StyleOrigin(val label: String) { Pinned("pinned"), Selected("preview only"), Default("default") }

data class StyleToc(val enabled: Boolean, val depth: Int, val leaders: Boolean, val placement: StyleTocPlacement)

sealed interface StyleSegment {
    data class Text(val text: String) : StyleSegment
    data object Page : StyleSegment
    data object Total : StyleSegment
}

data class StyleSlot(val segments: List<StyleSegment>, val imageKey: String?) {
    val isEmpty: Boolean get() = segments.isEmpty() && imageKey == null
}
data class StyleRegion(val left: StyleSlot, val centre: StyleSlot, val right: StyleSlot) {
    val isEmpty: Boolean get() = left.isEmpty && centre.isEmpty && right.isEmpty
}
data class StyleRegions(val header: StyleRegion, val footer: StyleRegion)

data class DocumentStyle(
    val templateId: String,
    val templateName: String,
    val templateVersion: Int,
    val templateHash: String,
    val origin: StyleOrigin,
    val bodyFont: StyleFamily,
    val headingFont: StyleFamily,
    val bodySizePt: Int,
    /** Sizes of h1..h6 in points. */
    val headingSizesPt: List<Int>,
    /** Opaque RGB, 0xRRGGBB. */
    val headingColorRgb: Int,
    val headingNumbering: Boolean,
    val table: StyleTable,
    val imageMaxWidthPercent: Int,
    val imageAlign: StyleAlign,
    val toc: StyleToc,
    val coverPage: Boolean,
    val pageBreakBeforeLevel: Int?,
    val regions: StyleRegions?,
    val images: Map<String, DocumentImage>,
    /** Heading anchor to number; empty when numbering is off. */
    val numbers: Map<String, String>,
) {
    val label: String get() = "$templateName v$templateVersion (${origin.label})"

    companion object {
        /** Defaults equal to the built-in generic template, used when no style was loaded. */
        val Fallback = DocumentStyle(
            "generic", "Generic controlled document", 1, "", StyleOrigin.Default,
            StyleFamily.Sans, StyleFamily.Sans, 11, listOf(20, 17, 14, 13, 12, 11), 0x242628, false,
            StyleTable.Grid, 100, StyleAlign.Center, StyleToc(false, 3, true, StyleTocPlacement.Start),
            false, null, null, emptyMap(), emptyMap(),
        )
    }
}

enum class DocumentViewMode(val label: String) {
    Wysiwyg("Edit"), Source("Markdown"), Split("Markdown + Preview"), Preview("Preview"),
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
        require(margins.all { it in 5.0..50.0 && kotlin.math.abs(it * 10 - kotlin.math.round(it * 10)) < 1e-6 }) {
            "Page margins must be between 5.0 mm and 50.0 mm in 0.1 mm steps."
        }
        require(marginLeftMm + marginRightMm <= pageWidthMm - 40.0) {
            "Horizontal margins leave no editable area."
        }
        require(marginTopMm + marginBottomMm <= pageHeightMm - 60.0) {
            "Vertical margins leave no editable area."
        }
        require(headerTemplate.length <= 120 && footerTemplate.length <= 120) {
            "Header and footer templates must be at most 120 characters."
        }
        require(listOf(headerTemplate, footerTemplate).all(::validTemplate)) {
            "Header and footer may only use {title}, {page} and {total}, without control characters."
        }
        return this
    }
}

private fun validTemplate(text: String): Boolean {
    if (text.any { it.isISOControl() }) return false
    var rest = text
    while (true) {
        val open = rest.indexOfAny(charArrayOf('{', '}'))
        if (open < 0) return true
        if (rest[open] == '}') return false
        val close = rest.indexOf('}', open)
        if (close < 0 || rest.substring(open, close + 1) !in setOf("{title}", "{page}", "{total}")) return false
        rest = rest.substring(close + 1)
    }
}

data class DocumentRenderTiming(val inputDigest: String, val elapsedMillis: Double)
