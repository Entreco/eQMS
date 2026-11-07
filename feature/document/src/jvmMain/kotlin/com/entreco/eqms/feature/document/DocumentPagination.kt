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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val colors = JewelTheme.globalColors
    val base = JewelTheme.defaultTextStyle.copy(color = colors.text.normal, fontSize = 15.sp, lineHeight = 24.sp)
    val linkColor = colors.text.normal
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
            val headerHeight = measure(
                AnnotatedString(renderTemplate(setup.headerTemplate, document.title, 1000000, 1000000)),
                furnitureStyle
            ).size.height
            val footerHeight = measure(
                AnnotatedString(renderTemplate(setup.footerTemplate, document.title, 1000000, 1000000)),
                furnitureStyle
            ).size.height
            val furniture = max(headerHeight, footerHeight).toFloat()
            val capacity = (height - top - bottom - furniture * 2 - gap * 2).toInt()
            require(capacity >= with(density) { 48.dp.roundToPx() }) {
                "Page furniture leaves too little content space at this width. Widen the preview or shorten the header/footer. Markdown remains available."
            }
            val fragments = mutableListOf<PageFragment>()
            var tableSequence = 0
            fun anchors(node: RenderNode): Set<String> =
                (if (node.anchor.isBlank()) emptySet() else setOf(node.anchor)) + node.children.flatMap { anchors(it) }

            fun textFragments(text: AnnotatedString, style: TextStyle, targets: Set<String>) {
                val measured = measure(text, style)
                if (measured.size.height <= capacity) {
                    fragments += PageFragment(
                        measured.size.height, text, style,
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
                        partHeight, part, style,
                        anchors = (if (first == 0) targets else emptySet()) +
                                part.getStringAnnotations("anchor", 0, part.length).map { it.item },
                    )
                    first = last + 1
                }
            }

            fun add(node: RenderNode, prefix: String = "", inherited: Set<String> = emptySet()) {
                val targets = inherited + if (node.anchor.isBlank()) emptySet() else setOf(node.anchor)
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
                            contentWidth / ratio,
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
                        val style = renderTextStyle(node, base)
                        val text =
                            if (node.kind in listOf("code_block", "literal")) AnnotatedString(prefix + node.plainText())
                            else AnnotatedString(prefix) + inlineText(
                                node.children.ifEmpty { listOf(node) },
                                navigate,
                                linkColor
                            )
                        textFragments(text, style, targets)
                        fun images(child: RenderNode): List<RenderNode> =
                            if (child.kind == "image") listOf(child) else child.children.flatMap(::images)
                        node.children.flatMap(::images).forEach { add(it) }
                    }
                }
            }
            textFragments(
                AnnotatedString(document.title.ifBlank { "Untitled document" }),
                base.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold), emptySet()
            )
            document.nodes.forEach { add(it) }
            Result.success(
                PaginatedLayout(
                    paginateFragments(fragments, capacity, gap), width, height,
                    left, right, top, bottom, furniture, capacity, gap
                )
            )
        } catch (failure: IllegalArgumentException) {
            Result.failure(failure)
        }
    }
}

internal fun renderTextStyle(node: RenderNode, base: TextStyle): TextStyle {
    val level = node.kind.takeIf { it.matches(Regex("h[1-6]")) }?.last()?.digitToInt()
    return when {
        level != null -> base.copy(
            fontSize = (29 - level * 2).sp,
            lineHeight = (37 - level * 2).sp,
            fontWeight = FontWeight.SemiBold
        )

        node.kind in listOf("code_block", "literal") -> base.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        else -> base
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
        color = JewelTheme.globalColors.text.info,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
    Column(
        Modifier.width(px(layout.width)).height(px(layout.height))
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, JewelTheme.globalColors.borders.normal)
            .padding(start = px(layout.left), end = px(layout.right), top = px(layout.top), bottom = px(layout.bottom))
            .semantics { contentDescription = "Preview page ${index + 1} of ${layout.pages.size}" },
    ) {
        BasicText(
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
                                    .then(if (fragment.style.fontFamily == FontFamily.Monospace)
                                        Modifier.background(JewelTheme.globalColors.borders.normal.copy(alpha = 0.15f)) else Modifier)
                                    .then(if (fragment.style.fontWeight == FontWeight.SemiBold) Modifier.semantics { heading() } else Modifier),
                                style = fragment.style)
                        } else fragment.node?.let { renderBlock(it, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
        Spacer(Modifier.height(px(layout.gap.toFloat())))
        BasicText(
            renderTemplate(setup.footerTemplate, title, index + 1, layout.pages.size),
            Modifier.fillMaxWidth().height(px(layout.furnitureHeight)), style = base
        )
    }
}
