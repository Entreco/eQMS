package com.entreco.eqms.feature.document

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal data class PageFragment(
    val height: Int,
    val text: AnnotatedString? = null,
    val style: TextStyle = TextStyle.Default,
    val node: RenderNode? = null,
    val anchors: Set<String> = emptySet(),
    val pageBreak: Boolean = false,
    val overflow: Boolean = false,
    val tableGroup: Int? = null,
    val code: Boolean = false,
)

internal fun paginateFragments(fragments: List<PageFragment>, capacity: Int, gap: Int): List<List<PageFragment>> {
    require(capacity > 0 && gap >= 0)
    val pages = mutableListOf<List<PageFragment>>()
    var page = mutableListOf<PageFragment>()
    var used = 0
    fun flush() {
        if (page.isNotEmpty()) {
            val grouped = mutableListOf<PageFragment>()
            page.forEach { fragment ->
                val previous = grouped.lastOrNull()
                if (previous?.node?.kind == "table" && fragment.node?.kind == "table" &&
                    previous.tableGroup != null && previous.tableGroup == fragment.tableGroup &&
                    !previous.overflow && !fragment.overflow
                ) {
                    grouped[grouped.lastIndex] = previous.copy(
                        height = previous.height + fragment.height,
                        node = previous.node.copy(children = previous.node.children + fragment.node.children),
                        anchors = previous.anchors + fragment.anchors,
                    )
                } else grouped += fragment
            }
            pages += grouped
        }
        page = mutableListOf()
        used = 0
    }
    for (fragment in fragments) {
        if (fragment.pageBreak) {
            flush()
            continue
        }
        require(fragment.height >= 0)
        val spacing =
            if (page.isEmpty() || (fragment.tableGroup != null && page.last().tableGroup == fragment.tableGroup)) 0 else gap
        if (page.isNotEmpty() && used + spacing + fragment.height > capacity) flush()
        used += (if (page.isEmpty()) 0 else spacing) + min(fragment.height, capacity)
        page += fragment.copy(overflow = fragment.height > capacity)
    }
    flush()
    return pages.ifEmpty { listOf(emptyList()) }
}

internal data class PaginatedLayout(
    val pages: List<List<PageFragment>>,
    val width: Float,
    val height: Float,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val furnitureHeight: Float,
    val contentHeight: Int,
    val gap: Int,
    val style: DocumentStyle = DocumentStyle.Fallback,
) {
    val anchorPages: Map<String, Int>
        get() = buildMap {
            pages.forEachIndexed { page, fragments ->
                fragments.forEach { fragment ->
                    fragment.anchors.forEach { putIfAbsent(it, page) }
                }
            }
        }
}

@Composable
internal fun rememberPaginatedLayout(
    document: RenderedDocument,
    setup: DocumentPageSetup,
    availableWidth: Float,
    navigate: (String) -> Unit,
    imageRatios: Map<String, Float>,
): Result<PaginatedLayout> {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val style = document.style ?: DocumentStyle.Fallback
    val base = JewelTheme.defaultTextStyle.copy(color = Paper.ink, fontFamily = style.bodyFont.family(),
        fontSize = style.bodySizePt.ptToSp(), lineHeight = (style.bodySizePt * 2.1f).sp)
    val linkColor = Paper.ink
    return remember(document, setup, availableWidth, density, base, linkColor, imageRatios) {
        try {
            setup.validate()
            val pixelsPerMm = with(density) { (96f / 25.4f).dp.toPx() }
            val width = min(availableWidth, setup.pageWidthMm.toFloat() * pixelsPerMm).coerceAtLeast(1f)
            val scale = width / (setup.pageWidthMm.toFloat() * pixelsPerMm)
            fun mm(value: Double) = value.toFloat() * pixelsPerMm * scale
            val height = mm(setup.pageHeightMm)
            val left = mm(setup.marginLeftMm)
            val right = mm(setup.marginRightMm)
            val top = mm(setup.marginTopMm)
            val bottom = mm(setup.marginBottomMm)
            val contentWidth = (width - left - right).toInt().coerceAtLeast(1)
            val gap = with(density) { 12.dp.roundToPx() }
            val furnitureStyle = base.copy(fontSize = 11.sp, lineHeight = 16.sp)
            fun measure(text: AnnotatedString, style: TextStyle, atWidth: Int = contentWidth) =
                measurer.measure(text, style, constraints = Constraints(maxWidth = atWidth))
            // Reserve a conservative digit count so total-page substitution cannot change page fit.
            val regions = style.regions
            fun regionHeight(region: StyleRegion?, template: String): Int {
                if (region == null) return measure(
                    AnnotatedString(renderTemplate(template, document.title, 1000000, 1000000)), furnitureStyle
                ).size.height
                val slotWidth = (contentWidth / 3).coerceAtLeast(1)
                return listOf(region.left, region.centre, region.right).maxOf { slot ->
                    max(
                        if (slot.segments.isEmpty()) 0 else
                            measure(AnnotatedString(slotText(slot, 1000000, 1000000)), furnitureStyle, slotWidth).size.height,
                        if (slot.imageKey == null) 0 else with(density) { REGION_IMAGE_HEIGHT.dp.roundToPx() },
                    )
                }
            }
            val headerHeight = regionHeight(regions?.header, setup.headerTemplate)
            val footerHeight = regionHeight(regions?.footer, setup.footerTemplate)
            val furniture = max(headerHeight, footerHeight).toFloat()
            val capacity = (height - top - bottom - furniture * 2 - gap * 2).toInt()
            require(capacity >= with(density) { 48.dp.roundToPx() }) {
                "Page furniture leaves too little content space at this width. Widen the preview or shorten the header/footer. Markdown remains available."
            }
            val fragments = mutableListOf<PageFragment>()
            var tableSequence = 0
            var tocPages: Map<String, Int> = emptyMap()
            fun anchors(node: RenderNode): Set<String> =
                (if (node.anchor.isBlank()) emptySet() else setOf(node.anchor)) + node.children.flatMap { anchors(it) }

            fun textFragments(text: AnnotatedString, style: TextStyle, targets: Set<String>, code: Boolean = false) {
                val measured = measure(text, style)
                if (measured.size.height <= capacity) {
                    fragments += PageFragment(
                        measured.size.height, text, style, code = code,
                        anchors = targets + text.getStringAnnotations("anchor", 0, text.length).map { it.item })
                    return
                }
                var first = 0
                while (first < measured.lineCount) {
                    var last = first
                    while (last + 1 < measured.lineCount &&
                        measured.getLineBottom(last + 1) - measured.getLineTop(first) <= capacity
                    ) last++
                    val start = measured.getLineStart(first)
                    var end = if (last == measured.lineCount - 1) text.length else measured.getLineStart(last + 1)
                    var part = text.subSequence(start, end)
                    var partHeight = measure(part, style).size.height
                    while (partHeight > capacity && last > first) {
                        last--
                        end = measured.getLineStart(last + 1)
                        part = text.subSequence(start, end)
                        partHeight = measure(part, style).size.height
                    }
                    fragments += PageFragment(
                        partHeight, part, style, code = code,
                        anchors = (if (first == 0) targets else emptySet()) +
                                part.getStringAnnotations("anchor", 0, part.length).map { it.item },
                    )
                    first = last + 1
                }
            }

            fun add(node: RenderNode, prefix: String = "", inherited: Set<String> = emptySet()) {
                val targets = inherited + if (node.anchor.isBlank()) emptySet() else setOf(node.anchor)
                val headingLevel = node.kind.takeIf { it.matches(Regex("h[1-6]")) }?.last()?.digitToInt()
                val breakLevel = style.pageBreakBeforeLevel
                if (headingLevel != null && breakLevel != null && headingLevel <= breakLevel &&
                    fragments.isNotEmpty() && !fragments.last().pageBreak
                ) fragments += PageFragment(0, pageBreak = true)
                when (node.kind) {
                    "page_break" -> fragments += PageFragment(0, pageBreak = true)
                    "table" -> {
                        val tableGroup = ++tableSequence
                        val columns = node.children.maxOfOrNull { it.children.size }?.coerceAtLeast(1) ?: 1
                        val cellWidth = max(with(density) { 150.dp.roundToPx() }, contentWidth / columns)
                        val padding = with(density) { 20.dp.roundToPx() }
                        node.children.forEach { row ->
                            val rowHeight = row.children.maxOfOrNull { cell ->
                                measure(
                                    inlineText(cell.children, navigate, linkColor),
                                    base.copy(fontWeight = if (row.kind == "table_header") FontWeight.SemiBold else FontWeight.Normal),
                                    (cellWidth - padding).coerceAtLeast(1)
                                ).size.height + padding
                            } ?: 0
                            fragments += PageFragment(
                                rowHeight + with(density) { 2.dp.roundToPx() },
                                node = node.copy(children = listOf(row)),
                                anchors = targets + anchors(row),
                                tableGroup = tableGroup
                            )
                        }
                    }

                    "image" -> {
                        val ratio = imageRatios[node.target] ?: 1.5f
                        val imageHeight = min(
                            contentWidth * style.imageMaxWidthPercent / 100f / ratio,
                            min(with(density) { 400.dp.toPx() }, (capacity - gap * 2).toFloat())
                        ).coerceAtLeast(1f)
                        fragments += PageFragment(ceil(imageHeight + gap * 2).toInt(), node = node, anchors = targets)
                    }

                    "rule" -> fragments += PageFragment(
                        with(density) { 1.dp.roundToPx() },
                        node = node,
                        anchors = targets
                    )

                    "unordered", "ordered" -> node.children.forEachIndexed { index, item ->
                        add(
                            item,
                            if (node.kind == "ordered") "${(node.target.toLongOrNull() ?: 1) + index}. " else "\u2022 ",
                            if (index == 0) targets else emptySet()
                        )
                    }

                    "quote", "section", "item", "footnote" -> {
                        if (node.kind == "footnote") textFragments(AnnotatedString("Note ${node.text}"), base, targets)
                        node.children.forEachIndexed { index, child ->
                            add(child, if (index == 0) prefix else "", if (index == 0) targets else emptySet())
                        }
                        if (node.kind == "footnote") {
                            val back = RenderNode("link", "Back to reference", "#reference-${node.anchor}")
                            textFragments(inlineText(listOf(back), navigate, linkColor), base, emptySet())
                        }
                    }

                    else -> {
                        val textStyle = renderTextStyle(node, base, style)
                        val number = style.numbers[node.anchor]?.takeIf { headingLevel != null }
                        val isCode = node.kind in listOf("code_block", "literal")
                        val text =
                            if (isCode) AnnotatedString(prefix + node.plainText())
                            else AnnotatedString(prefix + (number?.let { "$it  " } ?: "")) + inlineText(
                                node.children.ifEmpty { listOf(node) },
                                navigate,
                                linkColor
                            )
                        textFragments(text, textStyle, targets, isCode)
                        fun images(child: RenderNode): List<RenderNode> =
                            if (child.kind == "image") listOf(child) else child.children.flatMap(::images)
                        node.children.flatMap(::images).forEach { add(it) }
                    }
                }
            }
            fun contents() {
                val entries = document.toc.filter { it.level <= style.toc.depth }
                if (!style.toc.enabled || entries.isEmpty()) return
                textFragments(AnnotatedString("Contents"), base.copy(fontWeight = FontWeight.SemiBold,
                    fontFamily = style.headingFont.family(), fontSize = style.headingSizesPt[1].ptToSp()), emptySet())
                val shallowest = entries.minOf { it.level }
                val lines = entries.joinToString("\n") { heading ->
                    val number = style.numbers[heading.anchor]?.let { "$it  " } ?: ""
                    val page = ((tocPages[heading.anchor] ?: 998) + 1).toString().padStart(3, '\u2007')
                    "${"    ".repeat(heading.level - shallowest)}$number${heading.title}" +
                        (if (style.toc.leaders) " ${"\u00B7".repeat(8)} " else "  ") + page
                }
                textFragments(AnnotatedString(lines), base, emptySet())
            }
            fun buildAll() {
                fragments.clear()
                tableSequence = 0
                textFragments(
                    AnnotatedString(document.title.ifBlank { "Untitled document" }),
                    base.copy(fontFamily = style.headingFont.family(), fontSize = (style.headingSizesPt[0] * 4f / 3f * 1.2f).sp,
                        lineHeight = (style.headingSizesPt[0] * 2.4f).sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF000000 or style.headingColorRgb.toLong())),
                    emptySet(),
                )
                val onCover = style.coverPage
                if (onCover && style.toc.placement == StyleTocPlacement.Cover) contents()
                if (onCover) fragments += PageFragment(0, pageBreak = true)
                if (onCover && style.toc.placement == StyleTocPlacement.AfterCover && style.toc.enabled) {
                    contents()
                    fragments += PageFragment(0, pageBreak = true)
                }
                if (!onCover || style.toc.placement == StyleTocPlacement.Start) contents()
                document.nodes.forEach { add(it) }
            }
            // The page-number column has a fixed width, so the second pass cannot change pagination.
            buildAll()
            if (style.toc.enabled) {
                tocPages = PaginatedLayout(paginateFragments(fragments, capacity, gap), width, height,
                    left, right, top, bottom, furniture, capacity, gap).anchorPages
                buildAll()
            }
            Result.success(
                PaginatedLayout(
                    paginateFragments(fragments, capacity, gap), width, height,
                    left, right, top, bottom, furniture, capacity, gap, style
                )
            )
        } catch (failure: IllegalArgumentException) {
            Result.failure(failure)
        }
    }
}

internal const val REGION_IMAGE_HEIGHT = 24

internal fun StyleFamily.family(): FontFamily = when (this) {
    StyleFamily.Sans -> FontFamily.SansSerif
    StyleFamily.Serif -> FontFamily.Serif
    StyleFamily.Mono -> FontFamily.Monospace
}

/** Points to scaled pixels at 96 dpi. */
internal fun Int.ptToSp() = (this * 4f / 3f).sp

internal fun slotText(slot: StyleSlot, page: Int, total: Int): String = slot.segments.joinToString("") {
    when (it) {
        is StyleSegment.Text -> it.text
        StyleSegment.Page -> "$page"
        StyleSegment.Total -> "$total"
    }
}

internal fun renderTextStyle(node: RenderNode, base: TextStyle, style: DocumentStyle = DocumentStyle.Fallback): TextStyle {
    val level = node.kind.takeIf { it.matches(Regex("h[1-6]")) }?.last()?.digitToInt()
    return when {
        level != null -> {
            val points = style.headingSizesPt[level - 1]
            base.copy(
                fontFamily = style.headingFont.family(),
                fontSize = points.ptToSp(),
                lineHeight = (points * 1.8f).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF000000 or style.headingColorRgb.toLong()),
            )
        }

        node.kind in listOf("code_block", "literal") -> base.copy(
            fontFamily = FontFamily.Monospace, fontSize = (style.bodySizePt * 0.87f).ptToSp(),
        )
        else -> base
    }
}

private fun Float.ptToSp() = (this * 4f / 3f).sp

@Composable
private fun RegionRow(region: StyleRegion, style: DocumentStyle, page: Int, total: Int, textStyle: TextStyle,
    modifier: Modifier) {
    Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        listOf(
            region.left to androidx.compose.ui.Alignment.CenterStart,
            region.centre to androidx.compose.ui.Alignment.Center,
            region.right to androidx.compose.ui.Alignment.CenterEnd,
        ).forEach { (slot, alignment) ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = alignment) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val bitmap = slot.imageKey?.let { style.images[it] }?.let { image ->
                        remember(image) {
                            runCatching {
                                org.jetbrains.skia.Image.makeFromEncoded(image.bytes).toComposeImageBitmap()
                            }.getOrNull()
                        }
                    }
                    bitmap?.let {
                        androidx.compose.foundation.Image(it, "Template image", Modifier.height(REGION_IMAGE_HEIGHT.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    }
                    if (slot.segments.isNotEmpty()) BasicText(slotText(slot, page, total), style = textStyle)
                }
            }
        }
    }
}

@Composable
internal fun DocumentPage(
    layout: PaginatedLayout,
    index: Int,
    setup: DocumentPageSetup,
    title: String,
    renderBlock: @Composable (RenderNode, Modifier) -> Unit,
) {
    val density = LocalDensity.current
    fun px(value: Float) = with(density) { value.toDp() }
    val base = JewelTheme.defaultTextStyle.copy(
        color = Paper.muted,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
    Column(
        Modifier.width(px(layout.width)).height(px(layout.height))
            .shadow(3.dp, RectangleShape)
            .background(Paper.sheet)
            .border(1.dp, Paper.rule)
            .padding(start = px(layout.left), end = px(layout.right), top = px(layout.top), bottom = px(layout.bottom))
            .semantics { contentDescription = "Preview page ${index + 1} of ${layout.pages.size}" },
    ) {
        val regions = layout.style.regions
        if (regions != null) RegionRow(regions.header, layout.style, index + 1, layout.pages.size, base,
            Modifier.fillMaxWidth().height(px(layout.furnitureHeight))
                .semantics { contentDescription = "Page header" })
        else BasicText(
            renderTemplate(setup.headerTemplate, title, index + 1, layout.pages.size),
            Modifier.fillMaxWidth().height(px(layout.furnitureHeight)), style = base
        )
        Spacer(Modifier.height(px(layout.gap.toFloat())))
        Column(
            Modifier.fillMaxWidth().height(px(layout.contentHeight.toFloat())),
            verticalArrangement = Arrangement.spacedBy(px(layout.gap.toFloat()))
        ) {
            layout.pages[index].forEach { fragment ->
                val fragmentModifier =
                    Modifier.fillMaxWidth().height(px(min(fragment.height, layout.contentHeight).toFloat()))
                Column(fragmentModifier) {
                    if (fragment.overflow) Text(
                        "Content exceeds page height; scroll within this block.",
                        color = JewelTheme.globalColors.text.error
                    )
                    Column(
                        if (fragment.overflow) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier
                    ) {
                        if (fragment.text != null) SelectionContainer {
                            BasicText(
                                fragment.text,
                                Modifier.fillMaxWidth()
                                    .then(if (fragment.code)
                                        Modifier.background(Paper.codeBackground) else Modifier)
                                    .then(if (fragment.style.fontWeight == FontWeight.SemiBold) Modifier.semantics { heading() } else Modifier),
                                style = fragment.style)
                        } else fragment.node?.let { node ->
                            if (node.kind == "image") Box(Modifier.fillMaxWidth(),
                                contentAlignment = if (layout.style.imageAlign == StyleAlign.Center)
                                    androidx.compose.ui.Alignment.TopCenter else androidx.compose.ui.Alignment.TopStart) {
                                renderBlock(node, Modifier.fillMaxWidth(layout.style.imageMaxWidthPercent / 100f))
                            } else renderBlock(node, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(px(layout.gap.toFloat())))
        if (regions != null) RegionRow(regions.footer, layout.style, index + 1, layout.pages.size, base,
            Modifier.fillMaxWidth().height(px(layout.furnitureHeight))
                .semantics { contentDescription = "Page footer" })
        else BasicText(
            renderTemplate(setup.footerTemplate, title, index + 1, layout.pages.size),
            Modifier.fillMaxWidth().height(px(layout.furnitureHeight)), style = base
        )
    }
}
