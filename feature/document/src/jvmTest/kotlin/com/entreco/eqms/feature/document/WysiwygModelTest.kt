package com.entreco.eqms.feature.document

import kotlin.test.*

class WysiwygModelTest {
    private fun text(value: String, vararg marks: Pair<IntRange, Int>): InlineText {
        var result = InlineText(value)
        marks.forEach { (range, bit) -> result = result.toggle(range.first, range.last + 1, bit) }
        return result
    }

    private fun n(kind: String, vararg children: RenderNode, text: String = "", target: String = "", start: Int = -1, end: Int = -1) =
        RenderNode(kind, text, target, "", children.toList(), start, end)

    private fun t(value: String) = n("text", text = value)

    @Test
    fun inlineSerializationCoversStylesAndEscapes() {
        assertEquals("**bold** and *it*", text("bold and it", 0..3 to InlineMark.Bold, 9..10 to InlineMark.Italic).toMarkdown())
        assertEquals("~~gone~~", text("gone", 0..3 to InlineMark.Strike).toMarkdown())
        assertEquals("`x`", text("x", 0..0 to InlineMark.Code).toMarkdown())
        assertEquals("``a`b``", text("a`b", 0..2 to InlineMark.Code).toMarkdown())
        assertEquals("1 \\* 2 \\[x\\] snake_case \\_lead", text("1 * 2 [x] snake_case _lead").toMarkdown())
        assertEquals("a \\| b", text("a | b").toMarkdown(table = true))
        assertEquals("**a *b***", text("a b", 0..2 to InlineMark.Bold, 2..2 to InlineMark.Italic).toMarkdown())
        assertEquals(" **x** ", text(" x ", 0..2 to InlineMark.Bold).toMarkdown())
        assertEquals("[go](https://x.invalid)", text("go").withLink(0, 2, "https://x.invalid").toMarkdown())
        assertEquals("[go](<https://x.invalid/a(b)>)", text("go").withLink(0, 2, "https://x.invalid/a(b)").toMarkdown())
    }

    @Test
    fun typingInheritsStyleAndToggleRoundTrips() {
        val bold = text("ab", 0..1 to InlineMark.Bold)
        val typed = bold.applyTextChange("abc")
        assertEquals(listOf(1, 1, 1), typed.bits)
        assertEquals(listOf(1, 1, 0), bold.applyTextChange("abc", pending = 0).bits)
        assertEquals(bold.text, typed.applyTextChange("ab").text)
        val toggled = typed.toggle(0, 3, InlineMark.Bold)
        assertEquals(listOf(0, 0, 0), toggled.bits)
        assertEquals(typed, toggled.toggle(0, 3, InlineMark.Bold))
        val linked = text("a link b").withLink(2, 6, "#x")
        assertEquals("#x", linked.applyTextChange("a liXnk b").links[4])
        assertNull(linked.applyTextChange("a linkX b").links[6])
    }

    @Test
    fun fromNodesReadsStylesAndRejectsUnsupportedInlines() {
        val parsed = InlineText.fromNodes(listOf(t("a "), n("strong", t("b"), n("emphasis", t("c"))), n("break", text = "\n"),
            n("link", t("d"), target = "#t"), n("code", text = "e")))!!
        assertEquals("a bc\nde", parsed.text)
        assertEquals(listOf(0, 0, 1, 3, 0, 0, 8), parsed.bits)
        assertEquals("a **b*c***\n[d](#t)`e`", parsed.toMarkdown())
        assertNull(InlineText.fromNodes(listOf(n("image", t("x"), target = "assets/a.png"))))
        assertNull(InlineText.fromNodes(listOf(n("note_link", text = "[1]"))))
        assertNull(InlineText.fromNodes(listOf(n("literal", text = "<b>"))))
    }

    @Test
    fun blocksComeFromRangesAndUnsupportedBlocksAreRaw() {
        val body = "# T\n\nhello\n\n```\ncode\n```\n\n- a\n- b\n\n| h |\n|:-:|\n| c |\n"
        val nodes = listOf(
            n("h1", t("T"), start = 0, end = 3),
            n("paragraph", t("hello"), start = 5, end = 10),
            n("code_block", t("code\n"), start = 12, end = 24),
            n("unordered", n("item", n("paragraph", t("a"))), n("item", n("paragraph", t("b"))), start = 26, end = 33),
            n("table", n("table_header", n("table_cell", t("h"))), n("table_row", n("table_cell", t("c"))), start = 35, end = 53),
        )
        val doc = WysiwygDocument.fromRendered(body, nodes)!!
        assertEquals(listOf("h1", "paragraph", "raw", "unordered", "table"), doc.blocks.map { WysiwygDocument.kind(it.content) })
        assertEquals("```\ncode\n```", (doc.blocks[2].content as BlockContent.Raw).text)
        assertEquals(listOf(":-:"), (doc.blocks[4].content as BlockContent.Table).delimiter)
        assertNull(WysiwygDocument.fromRendered("short", listOf(n("paragraph", t("x"), start = 0, end = 99))))
        assertNull(WysiwygDocument.fromRendered(body, listOf(n("paragraph", start = 5, end = 9), n("paragraph", start = 3, end = 4))))
    }

    private fun doc(body: String, vararg blocks: Triple<Int, Int, BlockContent>) =
        WysiwygDocument(body, blocks.map { EditorBlock(it.first, it.second, it.third) })

    private fun para(value: String) = BlockContent.Rich(0, InlineText(value))

    @Test
    fun replacePatchesOnlyTheEditedBlockAndShiftsFollowingRanges() {
        val body = "One\r\n\r\nTwo\r\n\r\nThree\r\n"
        val d = doc(body, Triple(0, 3, para("One")), Triple(7, 10, para("Two")), Triple(14, 19, para("Three")))
        val edited = d.replace(1, BlockContent.Rich(0, text("Two plus", 0..2 to InlineMark.Bold)))
        assertEquals("One\r\n\r\n**Two** plus\r\n\r\nThree\r\n", edited.body)
        assertEquals(listOf(0 to 3, 7 to 19, 23 to 28), edited.blocks.map { it.start to it.end })
        edited.blocks.forEach { assertTrue(it.end <= edited.body.length) }
        assertEquals("Three", edited.body.substring(edited.blocks[2].start, edited.blocks[2].end))
        assertEquals("One", edited.body.substring(0, 3))
    }

    @Test
    fun insertAndDeleteKeepSeparatorsAndPreserveGaps() {
        val d = doc("A\n\n[r]: https://x.invalid\n\nB\n", Triple(0, 1, para("A")), Triple(27, 28, para("B")))
        val inserted = d.insertAfter(0, BlockContent.Rule)
        assertEquals("A\n\n---\n\n[r]: https://x.invalid\n\nB\n", inserted.body)
        assertEquals("---", inserted.body.substring(inserted.blocks[1].start, inserted.blocks[1].end))
        assertEquals("B", inserted.body.substring(inserted.blocks[2].start, inserted.blocks[2].end))
        val front = d.insertAfter(-1, para("Z"))
        assertEquals("Z\n\nA\n\n[r]: https://x.invalid\n\nB\n", front.body)
        val deletedFirst = d.delete(0)
        assertEquals("\n\n[r]: https://x.invalid\n\nB\n", deletedFirst.body)
        assertEquals("B", deletedFirst.body.substring(deletedFirst.blocks[0].start, deletedFirst.blocks[0].end))
        val deletedLast = d.delete(1)
        assertEquals("A\n\n[r]: https://x.invalid\n\n\n", deletedLast.body)
        val first = WysiwygDocument.empty().insertAfter(-1, para("Hi"))
        assertEquals("Hi", first.body)
        assertEquals("", first.delete(0).body)
    }

    @Test
    fun emptyParagraphBecomesTextInPlace() {
        val d = doc("A\n\nB", Triple(0, 1, para("A")), Triple(3, 4, para("B")))
        val withEmpty = d.insertAfter(0, para(""))
        assertEquals("A\n\n\n\nB", withEmpty.body)
        assertTrue(withEmpty.blocks[1].empty)
        val typed = withEmpty.replace(1, para("mid"))
        assertEquals("A\n\nmid\n\nB", typed.body)
        assertEquals("B", typed.body.substring(typed.blocks[2].start, typed.blocks[2].end))
        assertEquals(listOf(Triple(0, 1, "paragraph"), Triple(5, 6, "paragraph")), withEmpty.signature())
    }

    @Test
    fun blocksSerializeToCompactMarkdown() {
        fun s(c: BlockContent) = WysiwygDocument.serialize(c)
        assertEquals("## Title \\#", s(BlockContent.Rich(2, InlineText("Title #"))))
        assertEquals("# a", s(BlockContent.Rich(1, InlineText("a\n "))))
        assertEquals("\\# not heading\n\\- nor list\n1\\. nor", s(para("# not heading\n- nor list\n1. nor")))
        assertEquals("- a\n- b", s(BlockContent.Items(false, 1, '-', listOf(InlineText("a"), InlineText("b")))))
        assertEquals("3. a\n   more\n4.", s(BlockContent.Items(true, 3, '.', listOf(InlineText("a\nmore"), InlineText()))))
        assertEquals("> a\n>\n> b", s(BlockContent.Quote(listOf(InlineText("a"), InlineText("b")))))
        assertEquals("---", s(BlockContent.Rule))
        assertEquals("<!-- pagebreak -->", s(BlockContent.PageBreak))
        assertEquals("a\n> b", WysiwygDocument.serialize(BlockContent.Raw("a\r\n> b")))
        val table = BlockContent.Table(listOf(InlineText("h"), InlineText("i")), listOf(listOf(InlineText("a|b"), InlineText())), listOf(":--", "--:"))
        assertEquals("| h | i |\n| :-- | --: |\n| a\\|b |  |", s(table))
    }

    @Test
    fun tableOperationsKeepAValidShape() {
        val table = BlockContent.Table(listOf(InlineText("h")), listOf(listOf(InlineText("a"))), listOf("---"))
        val wide = table.addColumn(0).setCell(1, 1, InlineText("b"))
        assertEquals(listOf("h", ""), wide.header.map { it.text })
        assertEquals(listOf("a", "b"), wide.rows[0].map { it.text })
        assertEquals(2, wide.delimiter.size)
        assertEquals(table.header.size, wide.removeColumn(1).header.size)
        assertSame(table, table.removeColumn(0))
        val tall = table.addRow(1)
        assertEquals(2, tall.rows.size)
        assertEquals(1, tall.removeRow(2).rows.size)
        assertSame(tall, tall.removeRow(0))
        assertEquals("H", table.setCell(0, 0, InlineText("H")).header[0].text)
    }

    @Test
    fun rawEditPatchesExactSliceAndHonoursCrlf() {
        val d = doc("A\r\n\r\n```\r\nx\r\n```\r\n", Triple(0, 1, para("A")), Triple(5, 16, BlockContent.Raw("```\r\nx\r\n```")))
        val edited = d.replace(1, BlockContent.Raw("```\ny\n```"))
        assertEquals("A\r\n\r\n```\r\ny\r\n```\r\n", edited.body)
    }
}
