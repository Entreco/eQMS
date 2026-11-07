package com.entreco.eqms.feature.document

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentPaginationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun exactFitOverflowAndRedundantBreaksPreserveFragmentOrder() {
        fun part(text: String, height: Int) = PageFragment(height, AnnotatedString(text))
        val fragments = listOf(
            PageFragment(0, pageBreak = true), part("a", 40), part("b", 50),
            PageFragment(0, pageBreak = true), PageFragment(0, pageBreak = true),
            part("oversized", 120), part("end", 10), PageFragment(0, pageBreak = true)
        )
        val pages = paginateFragments(fragments, 100, 10)
        assertEquals(3, pages.size)
        assertEquals(listOf("a", "b"), pages.first().map { it.text?.text })
        assertTrue(pages[1].single().overflow)
        assertEquals(listOf("a", "b", "oversized", "end"), pages.flatten().map { it.text?.text })
        assertEquals(1, paginateFragments(emptyList(), 100, 10).size)
        assertFailsWith<IllegalArgumentException> { paginateFragments(fragments, 0, 10) }
    }

    @Test
    fun insertionPreservesSelectionAndLineEndingsAndOnlyDraftSourceCanInsert() {
        val value = TextFieldValue("before\r\nafter", TextRange(2, 8))
        val result = insertPageBreak(value)
        assertEquals("before\r\n\r\n<!-- pagebreak -->\r\n\r\nafter", result.text)
        assertEquals(TextRange(result.text.indexOf("after")), result.selection)
        assertTrue(result.text.startsWith(value.text.substring(0, value.selection.end)))
        assertTrue(result.text.endsWith(value.text.substring(value.selection.end)))
        val tab = DocumentTab(1, null)
        tab.bodyField.value = value
        tab.insertPageBreak()
        assertTrue(tab.dirty)
        assertEquals(result, tab.bodyField.value)
        tab.undoBodyEdit()
        assertEquals(value, tab.bodyField.value)
        tab.undoBodyEdit(redo = true)
        assertEquals(result, tab.bodyField.value)
        tab.viewMode = DocumentViewMode.Preview
        tab.insertPageBreak()
        assertEquals(result, tab.bodyField.value)
        val historical = DocumentTab(2, null, historical = true)
        historical.insertPageBreak()
        assertEquals("", historical.body)
    }

    @Test
    fun invalidOrOvercrowdedPageSetupFailsExplicitlyWithoutChangingSource() {
        val document = document(listOf(RenderNode("paragraph", children = listOf(RenderNode("text", "Preserved")))))
            .copy(title = "Title ".repeat(100))
        var setup by mutableStateOf(DocumentPageSetup(marginLeftMm = -1.0))
        var result: Result<PaginatedLayout>? = null
        compose.setContent {
            IntUiTheme(isDark = false) {
                val measured = rememberPaginatedLayout(document, setup, 200f, {}, emptyMap())
                SideEffect { result = measured }
            }
        }
        compose.runOnIdle {
            assertTrue(requireNotNull(result).isFailure)
            assertTrue(requireNotNull(result).exceptionOrNull()?.message.orEmpty().contains("margins"))
            setup = DocumentPageSetup(headerTemplate = "{title}".repeat(15))
        }
        compose.runOnIdle {
            assertTrue(requireNotNull(result).isFailure)
            assertEquals("Preserved", document.nodes.single().plainText())
        }
    }

    @Test
    fun pageGeometryAndMeasuredLongTextPreserveAllContentAcrossSettings() {
        val body = ("Long Unicode paragraph \u65e5\u672c\u8a9e with words and wrapped content. ").repeat(200)
        val document = document(listOf(RenderNode("paragraph", children = listOf(RenderNode("text", body)))))
        var setup by mutableStateOf(DocumentPageSetup())
        var layout: PaginatedLayout? = null
        compose.setContent {
            IntUiTheme(isDark = true) {
                val measured = rememberPaginatedLayout(document, setup, 760f, {}, emptyMap()).getOrThrow()
                SideEffect { layout = measured }
            }
        }
        for (size in PageSize.entries) for (orientation in PageOrientation.entries) for (margin in listOf(
            5.0,
            25.4,
            50.0
        )) {
            compose.runOnIdle {
                setup = DocumentPageSetup(
                    size = size, orientation = orientation,
                    marginTopMm = margin, marginRightMm = margin, marginBottomMm = margin, marginLeftMm = margin
                )
            }
            compose.runOnIdle {
                val current = requireNotNull(layout)
                assertEquals(
                    setup.pageWidthMm / setup.pageHeightMm,
                    (current.width / current.height).toDouble(), 0.0001
                )
                assertTrue(current.pages.size > 1)
                assertEquals("Title$body", current.pages.flatten().joinToString("") { it.text?.text.orEmpty() })
                current.pages.forEach { page ->
                    assertTrue(page.sumOf { it.height } + (page.size - 1).coerceAtLeast(0) * current.gap <= current.contentHeight)
                }
            }
        }
    }

    @Test
    fun longCodeAndFootnoteReferencesRetainLinesAndActualFragmentAnchors() {
        val code = "line with code\n".repeat(150)
        val document = document(
            listOf(
                RenderNode("code_block", code),
                RenderNode(
                    "paragraph", children = listOf(
                        RenderNode("text", "Long text ".repeat(200)),
                        RenderNode("note_link", "[note]", "#note", "reference-note")
                    )
                )
            )
        )
        var layout: PaginatedLayout? = null
        compose.setContent {
            IntUiTheme(isDark = false) {
                val measured = rememberPaginatedLayout(document, DocumentPageSetup(), 600f, {}, emptyMap()).getOrThrow()
                SideEffect { layout = measured }
            }
        }
        compose.runOnIdle {
            val current = requireNotNull(layout)
            assertEquals(
                "Title$code${"Long text ".repeat(200)}[note]",
                current.pages.flatten().joinToString("") { it.text?.text.orEmpty() })
            assertTrue(current.pages.flatten().none { it.overflow })
            assertEquals(current.pages.lastIndex, current.anchorPages["reference-note"])
        }
    }

    @Test
    fun tableRowsBreaksAndAnchorsAreMappedToPagesWithoutLostContent() {
        fun cell(text: String) = RenderNode("table_cell", children = listOf(RenderNode("text", text)))
        val rows = List(30) { RenderNode("table_row", children = listOf(cell("Row $it"), cell("Value $it"))) }
        val document = document(
            listOf(
                RenderNode("table", children = rows),
                RenderNode("page_break"), RenderNode("page_break"),
                RenderNode("h2", anchor = "end", children = listOf(RenderNode("text", "End"))), RenderNode("page_break")
            )
        )
        var layout: PaginatedLayout? = null
        compose.setContent {
            IntUiTheme(isDark = false) {
                val measured = rememberPaginatedLayout(document, DocumentPageSetup(), 600f, {}, emptyMap()).getOrThrow()
                SideEffect { layout = measured }
            }
        }
        compose.runOnIdle {
            val current = requireNotNull(layout)
            assertTrue(current.pages.size > 2)
            assertEquals(
                rows,
                current.pages.flatten().mapNotNull { it.node?.takeIf { it.kind == "table" } }.flatMap { it.children })
            assertEquals(current.pages.lastIndex, current.anchorPages["end"])
            assertTrue(current.pages.none { it.isEmpty() })
        }
    }

    @Test
    fun furnitureTemplatesResolveEachPageAndImageDimensionsInvalidateLayout() {
        assertEquals("Document 2/7", renderTemplate("{title} {page}/{total}", "Document", 2, 7))
        val document = document(listOf(RenderNode("image", target = "assets/image.png")))
        var ratios by mutableStateOf<Map<String, Float>>(emptyMap())
        var height = 0
        compose.setContent {
            IntUiTheme(isDark = false) {
                val layout = rememberPaginatedLayout(document, DocumentPageSetup(), 600f, {}, ratios).getOrThrow()
                SideEffect { height = layout.pages.flatten().first { it.node?.kind == "image" }.height }
            }
        }
        var firstHeight = 0
        compose.runOnIdle { firstHeight = height; ratios = mapOf("assets/image.png" to 4f) }
        compose.runOnIdle { assertTrue(height < firstHeight) }
    }

    @Test
    fun annotatedFragmentSlicingRetainsStylesAndLinkTargets() {
        val text = inlineText(
            listOf(
            RenderNode("strong", children = listOf(RenderNode("text", "bold"))),
            RenderNode("link", "link", "https://example.invalid")
        ), {}, Color.Black
        )
        assertEquals("boldlink", text.text)
        assertTrue(text.subSequence(0, 4).spanStyles.isNotEmpty())
        assertTrue(text.subSequence(4, 8).getLinkAnnotations(0, 4).isNotEmpty())
    }

    private fun document(nodes: List<RenderNode>) = RenderedDocument(
        "Title", "digest",
        RenderSource("unsaved", "", "", ""), nodes, emptyList(), emptyList()
    )
}
