package com.entreco.eqms.feature.document

import kotlin.test.*

class WysiwygEditorStateTest {
    private fun text(s: String) = RenderNode("text", s)
    private fun render(nodes: List<RenderNode>, body: String) =
        RenderedDocument("T", body, RenderSource("unsaved", "", "", ""), nodes, emptyList(), emptyList())

    private fun para(s: String, start: Int) =
        RenderNode("paragraph", children = listOf(text(s)), start = start, end = start + s.length)

    private fun tab(body: String, nodes: List<RenderNode>) = DocumentTab(1, null).also {
        it.body = body
        it.editor.sync(render(nodes, body))
    }

    @Test
    fun wysiwygIsTheDefaultMode() {
        assertEquals(DocumentViewMode.Wysiwyg, DocumentTab(1, null).viewMode)
    }

    @Test
    fun typingPatchesOnlyTheTouchedBlockAndIsOneUndoStep() {
        val body = "One\n\nTwo"
        val tab = tab(body, listOf(para("One", 0), para("Two", 5)))
        tab.editor.changeText(FieldId(1), InlineText("Two!"))
        assertEquals("One\n\nTwo!", tab.body)
        assertTrue(tab.dirty)
        tab.undoBodyEdit()
        assertEquals(body, tab.body)
    }

    @Test
    fun enterSplitsAndBackspaceMerges() {
        val tab = tab("Alpha", listOf(para("Alpha", 0)))
        tab.editor.enter(FieldId(0), 2)
        assertEquals("Al\n\npha", tab.body)
        tab.editor.backspaceAtStart(FieldId(1))
        assertEquals("Alpha", tab.body)
    }

    @Test
    fun boldToggleAndHeadingConversionKeepMarkdown() {
        val tab = tab("Hello", listOf(para("Hello", 0)))
        tab.editor.changeText(FieldId(0), InlineText("Hello").toggle(0, 5, InlineMark.Bold))
        assertEquals("**Hello**", tab.body)
        tab.editor.focus = FieldFocus(FieldId(0), InlineText("Hello").toggle(0, 5, InlineMark.Bold), androidx.compose.ui.text.TextRange(0)) {}
        tab.editor.convert(BlockTarget.Heading2)
        assertEquals("## **Hello**", tab.body)
    }

    @Test
    fun nonDraftDocumentsAreNotEditable() {
        val tab = DocumentTab(1, null, historical = true)
        tab.body = "One"
        tab.editor.sync(render(listOf(para("One", 0)), "One"))
        assertFalse(tab.editor.canEdit)
    }

    @Test
    fun linkTargetsAreRestricted() {
        assertTrue(allowedEditorLink("https://example.com"))
        assertFalse(allowedEditorLink("javascript:alert(1)"))
    }
}
