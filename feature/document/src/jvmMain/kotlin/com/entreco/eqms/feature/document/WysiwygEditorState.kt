package com.entreco.eqms.feature.document

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange

internal const val MAX_EDITOR_BLOCKS = 1500

internal data class FieldId(val block: Int, val part: Int = 0, val cell: Int = 0)

/** The focused rich field as seen by the toolbar; [apply] pushes a changed text back into the field. */
internal data class FieldFocus(
    val id: FieldId,
    val text: InlineText,
    val selection: TextRange,
    val apply: (InlineText) -> Unit,
)

internal data class FocusRequest(val id: FieldId, val selection: TextRange, val serial: Long)

internal enum class BlockTarget { Paragraph, Heading1, Heading2, Heading3, Heading4, Heading5, Heading6, Bullets, Numbered, Quote }

internal fun allowedEditorLink(target: String) =
    target.none { it.isISOControl() || it.isWhitespace() } &&
        (target.startsWith("#") || target.startsWith("https://") || target.startsWith("http://") || target.startsWith("mailto:"))

/** Editing state and operations of the WYSIWYG view; every change is a patch of the tab's one Markdown buffer. */
internal class WysiwygEditorState(private val tab: DocumentTab) {
    var doc by mutableStateOf<WysiwygDocument?>(null)
        private set
    var unavailable by mutableStateOf<String?>(null)
        private set
    var focus by mutableStateOf<FieldFocus?>(null)
    var focusRequest by mutableStateOf<FocusRequest?>(null)
        private set
    private var pendingBits: Int? = null
    private var pendingAt = -1
    private var serial = 0L

    val synced get() = doc?.body == tab.body
    val canEdit get() = tab.editable && synced

    /** Adopts a native render of exactly the current body when it differs from the local patched view. */
    fun sync(current: RenderedDocument) {
        val fresh = WysiwygDocument.fromRendered(tab.body, current.nodes)
        unavailable = when {
            fresh == null -> "The editor could not map this document's blocks. Use Markdown to edit it."
            fresh.blocks.size > MAX_EDITOR_BLOCKS -> "This document is too large for visual editing. Use Markdown to edit it."
            else -> null
        }
        if (fresh == null || unavailable != null) return
        val local = doc
        if (local == null || local.body != tab.body || local.signature() != fresh.signature()) doc = fresh
    }

    private fun commit(next: WysiwygDocument) {
        tab.applyBodyEdit(next.body)
        doc = next
    }

    fun request(id: FieldId, selection: TextRange = TextRange(0)) {
        focusRequest = FocusRequest(id, selection, ++serial)
    }

    fun consumeRequest(request: FocusRequest) {
        if (focusRequest == request) focusRequest = null
    }

    fun pendingFor(caret: Int): Int? = pendingBits.takeIf { pendingAt == caret }

    fun clearPending() { pendingBits = null; pendingAt = -1 }

    private fun content(index: Int) = doc?.blocks?.getOrNull(index)?.content

    fun changeText(id: FieldId, text: InlineText) {
        val d = doc ?: return
        if (!canEdit) return
        val next = when (val c = content(id.block)) {
            is BlockContent.Rich -> c.copy(text = text)
            is BlockContent.Items -> c.copy(items = c.items.setAt(id.part, text))
            is BlockContent.Quote -> c.copy(paragraphs = c.paragraphs.setAt(id.part, text))
            is BlockContent.Table -> c.setCell(id.part, id.cell, text)
            else -> return
        }
        commit(d.replace(id.block, next))
    }

    fun changeRaw(index: Int, text: String) {
        val d = doc ?: return
        if (canEdit && content(index) is BlockContent.Raw) commit(d.replace(index, BlockContent.Raw(text)))
    }

    fun enter(id: FieldId, caret: Int): Boolean {
        val d = doc ?: return false
        if (!canEdit) return false
        when (val c = content(id.block)) {
            is BlockContent.Rich -> {
                val at = caret.coerceIn(0, c.text.length)
                commit(d.replace(id.block, c.copy(text = c.text.slice(0, at)))
                    .insertAfter(id.block, BlockContent.Rich(0, c.text.slice(at, c.text.length))))
                request(FieldId(id.block + 1))
            }
            is BlockContent.Items -> {
                val item = c.items[id.part]
                if (item.length == 0 && id.part == c.items.lastIndex) exitList(d, id.block, c.items.dropLast(1)) { c.copy(items = it) }
                else {
                    val at = caret.coerceIn(0, item.length)
                    val items = c.items.take(id.part) + item.slice(0, at) + item.slice(at, item.length) + c.items.drop(id.part + 1)
                    commit(d.replace(id.block, c.copy(items = items)))
                    request(FieldId(id.block, id.part + 1))
                }
            }
            is BlockContent.Quote -> {
                val item = c.paragraphs[id.part]
                if (item.length == 0 && id.part == c.paragraphs.lastIndex) exitList(d, id.block, c.paragraphs.dropLast(1)) { c.copy(paragraphs = it) }
                else {
                    val at = caret.coerceIn(0, item.length)
                    val paragraphs = c.paragraphs.take(id.part) + item.slice(0, at) + item.slice(at, item.length) + c.paragraphs.drop(id.part + 1)
                    commit(d.replace(id.block, c.copy(paragraphs = paragraphs)))
                    request(FieldId(id.block, id.part + 1))
                }
            }
            is BlockContent.Table -> {
                if (id.part < c.rows.size) request(FieldId(id.block, id.part + 1, id.cell))
            }
            else -> return false
        }
        return true
    }

    private fun exitList(d: WysiwygDocument, index: Int, remaining: List<InlineText>, rebuild: (List<InlineText>) -> BlockContent) {
        if (remaining.isEmpty()) {
            commit(d.replace(index, BlockContent.Rich(0, InlineText())))
            request(FieldId(index))
        } else {
            commit(d.replace(index, rebuild(remaining)).insertAfter(index, BlockContent.Rich(0, InlineText())))
            request(FieldId(index + 1))
        }
    }

    /** Backspace with the caret at the start of a field. */
    fun backspaceAtStart(id: FieldId): Boolean {
        val d = doc ?: return false
        if (!canEdit) return false
        when (val c = content(id.block)) {
            is BlockContent.Rich -> {
                val previous = content(id.block - 1)
                when {
                    previous is BlockContent.Rich -> {
                        commit(d.replace(id.block - 1, previous.copy(text = previous.text + c.text)).delete(id.block))
                        request(FieldId(id.block - 1), TextRange(previous.text.length))
                    }
                    previous == BlockContent.Rule || previous == BlockContent.PageBreak -> {
                        commit(d.delete(id.block - 1))
                        request(FieldId(id.block - 1))
                    }
                    c.level > 0 -> {
                        commit(d.replace(id.block, c.copy(level = 0)))
                        request(id)
                    }
                    else -> return false
                }
            }
            is BlockContent.Items -> when {
                id.part > 0 -> {
                    val previous = c.items[id.part - 1]
                    val items = c.items.take(id.part - 1) + (previous + c.items[id.part]) + c.items.drop(id.part + 1)
                    commit(d.replace(id.block, c.copy(items = items)))
                    request(FieldId(id.block, id.part - 1), TextRange(previous.length))
                }
                c.items.size == 1 -> { commit(d.replace(id.block, BlockContent.Rich(0, c.items[0]))); request(FieldId(id.block)) }
                else -> return false
            }
            is BlockContent.Quote -> when {
                id.part > 0 -> {
                    val previous = c.paragraphs[id.part - 1]
                    val paragraphs = c.paragraphs.take(id.part - 1) + (previous + c.paragraphs[id.part]) + c.paragraphs.drop(id.part + 1)
                    commit(d.replace(id.block, c.copy(paragraphs = paragraphs)))
                    request(FieldId(id.block, id.part - 1), TextRange(previous.length))
                }
                c.paragraphs.size == 1 -> { commit(d.replace(id.block, BlockContent.Rich(0, c.paragraphs[0]))); request(FieldId(id.block)) }
                else -> return false
            }
            else -> return false
        }
        return true
    }

    fun deleteBlock(index: Int) {
        val d = doc ?: return
        if (canEdit && index in d.blocks.indices) commit(d.delete(index))
    }

    fun toggleMark(bit: Int) {
        val f = focus ?: return
        if (!canEdit) return
        val selection = f.selection
        if (!selection.collapsed) f.apply(f.text.toggle(selection.min, selection.max, bit))
        else {
            pendingAt = selection.start
            pendingBits = (pendingBits ?: f.text.bitsAt(selection.start)) xor bit
        }
        request(f.id, selection)
    }

    fun markActive(bit: Int): Boolean {
        val f = focus ?: return false
        val selection = f.selection
        return if (selection.collapsed) ((pendingFor(selection.start) ?: f.text.bitsAt(selection.start)) and bit) != 0
        else f.text.hasAll(selection.min, selection.max, bit)
    }

    /** Returns an error message, or null when applied. A blank [url] removes the link. */
    fun applyLink(url: String): String? {
        val f = focus ?: return "Select text in a paragraph first."
        if (!canEdit) return "This document is read-only."
        val selection = f.selection
        if (selection.collapsed) return "Select the text to link first."
        val target = url.trim()
        if (target.isNotEmpty() && !allowedEditorLink(target)) return "Links must start with https://, http://, mailto: or #, without spaces."
        f.apply(f.text.withLink(selection.min, selection.max, target.ifEmpty { null }))
        request(f.id, selection)
        return null
    }

    fun linkAtSelection(): String? {
        val f = focus ?: return null
        val at = if (f.selection.collapsed) (f.selection.start - 1).coerceAtLeast(0) else f.selection.min
        return f.text.links.getOrNull(at)
    }

    fun focusedBlock(): BlockContent? = focus?.let { content(it.id.block) }

    fun convert(target: BlockTarget) {
        val d = doc ?: return
        val index = focus?.id?.block ?: return
        if (!canEdit) return
        val current = content(index) ?: return
        val paragraphs = when (current) {
            is BlockContent.Rich -> listOf(current.text)
            is BlockContent.Items -> current.items
            is BlockContent.Quote -> current.paragraphs
            else -> return
        }
        val effective = when {
            target == BlockTarget.Bullets && current is BlockContent.Items && !current.ordered -> BlockTarget.Paragraph
            target == BlockTarget.Numbered && current is BlockContent.Items && current.ordered -> BlockTarget.Paragraph
            target == BlockTarget.Quote && current is BlockContent.Quote -> BlockTarget.Paragraph
            else -> target
        }
        val blocks: List<BlockContent> = when (effective) {
            BlockTarget.Bullets -> listOf(BlockContent.Items(false, 1, '-', paragraphs))
            BlockTarget.Numbered -> listOf(BlockContent.Items(true, 1, '.', paragraphs))
            BlockTarget.Quote -> listOf(BlockContent.Quote(paragraphs))
            else -> paragraphs.map { BlockContent.Rich(effective.ordinal.coerceAtMost(6), it) }
        }
        var next = d.replace(index, blocks.first())
        blocks.drop(1).forEachIndexed { i, content -> next = next.insertAfter(index + i, content) }
        commit(next)
        request(FieldId(index), focus?.selection ?: TextRange(0))
    }

    private fun insert(content: BlockContent, focusAfter: FieldId?) {
        val d = doc ?: return
        if (!canEdit) return
        val index = focus?.id?.block ?: d.blocks.lastIndex
        var next = d.insertAfter(index, content)
        val last = index + 1 == next.blocks.lastIndex
        if (last) next = next.insertAfter(index + 1, BlockContent.Rich(0, InlineText()))
        commit(next)
        request(focusAfter ?: FieldId(minOf(index + 2, next.blocks.lastIndex)))
    }

    fun startTyping() {
        val d = doc ?: return
        if (!canEdit || d.blocks.isNotEmpty()) return
        commit(d.insertAfter(-1, BlockContent.Rich(0, InlineText())))
        request(FieldId(0))
    }

    fun insertRule() = insert(BlockContent.Rule, null)

    fun insertPageBreakBlock() = insert(BlockContent.PageBreak, null)

    fun insertTable() {
        val index = focus?.id?.block ?: doc?.blocks?.lastIndex ?: -1
        insert(BlockContent.Table(listOf(InlineText("Column 1"), InlineText("Column 2")),
            listOf(listOf(InlineText(), InlineText())), listOf("---", "---")), FieldId(index + 1))
    }

    fun tableAction(action: (BlockContent.Table, FieldId) -> BlockContent.Table) {
        val d = doc ?: return
        val f = focus ?: return
        val table = content(f.id.block) as? BlockContent.Table ?: return
        if (!canEdit) return
        val result = action(table, f.id)
        commit(d.replace(f.id.block, result))
        request(f.id.copy(part = f.id.part.coerceAtMost(result.rows.size), cell = f.id.cell.coerceAtMost(result.header.size - 1)))
    }

    fun addRow() = tableAction { t, id -> t.addRow(id.part) }
    fun removeRow() = tableAction { t, id -> t.removeRow(id.part) }
    fun addColumn() = tableAction { t, id -> t.addColumn(id.cell) }
    fun removeColumn() = tableAction { t, id -> t.removeColumn(id.cell) }
}

private fun <T> List<T>.setAt(index: Int, value: T) = mapIndexed { i, old -> if (i == index) value else old }
