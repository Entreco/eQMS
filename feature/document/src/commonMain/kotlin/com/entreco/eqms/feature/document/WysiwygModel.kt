package com.entreco.eqms.feature.document

sealed interface BlockContent {
    data class Rich(val level: Int, val text: InlineText) : BlockContent
    data class Items(val ordered: Boolean, val startAt: Long, val marker: Char, val items: List<InlineText>) : BlockContent
    data class Quote(val paragraphs: List<InlineText>) : BlockContent
    data class Table(val header: List<InlineText>, val rows: List<List<InlineText>>, val delimiter: List<String>) : BlockContent
    data object Rule : BlockContent
    data object PageBreak : BlockContent
    data class Raw(val text: String) : BlockContent
}

data class EditorBlock(
    val start: Int,
    val end: Int,
    val content: BlockContent,
    val node: RenderNode? = null,
) {
    val empty get() = start == end
}

const val PAGE_BREAK_MARKER = "<!-- pagebreak -->"

/**
 * Block view of one Markdown buffer. Every operation returns a new document whose body is a minimal
 * patch of the old body; text outside the edited block is never rewritten.
 */
data class WysiwygDocument(val body: String, val blocks: List<EditorBlock>) {
    private val newline get() = if ("\r\n" in body) "\r\n" else "\n"

    /** Ranges and kinds used to detect divergence from a native render; empty blocks are not Markdown. */
    fun signature(): List<Triple<Int, Int, String>> = blocks.filter { !it.empty }.map {
        Triple(it.start, it.end, if (it.content is BlockContent.Raw) "raw" else kind(it.content))
    }

    fun replace(index: Int, content: BlockContent): WysiwygDocument {
        val block = blocks[index]
        val text = serialize(content, newline)
        val delta = text.length - (block.end - block.start)
        val updated = blocks.mapIndexed { i, b ->
            when {
                i < index -> b
                i == index -> EditorBlock(block.start, block.start + text.length, content, null)
                else -> b.copy(start = b.start + delta, end = b.end + delta)
            }
        }
        return WysiwygDocument(body.substring(0, block.start) + text + body.substring(block.end), updated)
    }

    /** Inserts [content] after block [index] (-1 = before everything), separated by one blank line. */
    fun insertAfter(index: Int, content: BlockContent): WysiwygDocument {
        val nl = newline
        val text = serialize(content, nl)
        val hasContent = blocks.any { !it.empty }
        val (position, inserted, start) = when {
            !hasContent && index < 0 -> Triple(0, text, 0)
            index < 0 -> Triple(0, text + nl + nl, 0)
            else -> Triple(blocks[index].end, nl + nl + text, blocks[index].end + 2 * nl.length)
        }
        val created = EditorBlock(start, start + text.length, content)
        val before = blocks.take(index + 1)
        val after = blocks.drop(index + 1).map { it.copy(start = it.start + inserted.length, end = it.end + inserted.length) }
        return WysiwygDocument(body.substring(0, position) + inserted + body.substring(position), before + created + after)
    }

    fun delete(index: Int): WysiwygDocument {
        val block = blocks[index]
        val rest = blocks.filterIndexed { i, _ -> i != index }
        if (block.empty) return copy(blocks = rest)
        val next = blocks.getOrNull(index + 1)
        val previous = blocks.getOrNull(index - 1)?.takeIf { !it.empty }
        val removeStart = if (next == null && previous != null && body.substring(previous.end, block.start).isBlank()) previous.end else block.start
        val removeEnd = if (next != null && body.substring(block.end, next.start).isBlank()) next.start else block.end
        val removed = removeEnd - removeStart
        return WysiwygDocument(
            body.substring(0, removeStart) + body.substring(removeEnd),
            rest.mapIndexed { i, b -> if (i >= index) b.copy(start = b.start - removed, end = b.end - removed) else b },
        )
    }

    companion object {
        fun empty(body: String = "") = WysiwygDocument(body, emptyList())

        fun kind(content: BlockContent) = when (content) {
            is BlockContent.Rich -> if (content.level == 0) "paragraph" else "h${content.level}"
            is BlockContent.Items -> if (content.ordered) "ordered" else "unordered"
            is BlockContent.Quote -> "quote"
            is BlockContent.Table -> "table"
            BlockContent.Rule -> "rule"
            BlockContent.PageBreak -> "page_break"
            is BlockContent.Raw -> "raw"
        }

        /** Builds blocks from a native semantic render of exactly [body]; null if ranges do not fit it. */
        fun fromRendered(body: String, nodes: List<RenderNode>): WysiwygDocument? {
            var previous = 0
            val blocks = nodes.map { node ->
                if (node.start < previous || node.end < node.start || node.end > body.length) return null
                previous = node.end
                EditorBlock(node.start, node.end, content(body.substring(node.start, node.end), node), node)
            }
            return WysiwygDocument(body, blocks)
        }

        private fun content(source: String, node: RenderNode): BlockContent {
            val raw = BlockContent.Raw(source)
            return when (node.kind) {
                "paragraph" -> InlineText.fromNodes(node.children)?.let { BlockContent.Rich(0, it) } ?: raw
                "h1", "h2", "h3", "h4", "h5", "h6" ->
                    InlineText.fromNodes(node.children)?.let { BlockContent.Rich(node.kind.last().digitToInt(), it) } ?: raw
                "unordered", "ordered" -> {
                    val items = node.children.map { item ->
                        when {
                            item.kind != "item" -> return raw
                            item.children.isEmpty() -> InlineText()
                            item.children.size == 1 && item.children[0].kind == "paragraph" ->
                                InlineText.fromNodes(item.children[0].children) ?: return raw
                            else -> return raw
                        }
                    }
                    val marker = source.trimStart().firstOrNull() ?: '-'
                    val ordered = node.kind == "ordered"
                    val delimiter = if (ordered) source.trimStart().dropWhile { it.isDigit() }.firstOrNull() ?: '.' else marker
                    if (items.isEmpty() || (!ordered && marker !in "-*+") || (ordered && delimiter !in ".)")) raw
                    else BlockContent.Items(ordered, node.target.toLongOrNull() ?: 1, delimiter, items)
                }
                "quote" -> {
                    if (node.children.isEmpty() || node.children.any { it.kind != "paragraph" }) return raw
                    BlockContent.Quote(node.children.map { InlineText.fromNodes(it.children) ?: return raw })
                }
                "table" -> table(source, node) ?: raw
                "rule" -> BlockContent.Rule
                "page_break" -> BlockContent.PageBreak
                else -> raw
            }
        }

        private fun table(source: String, node: RenderNode): BlockContent.Table? {
            val rows = node.children.map { row ->
                row.children.map { InlineText.fromNodes(it.children) ?: return null }
            }
            if (rows.isEmpty() || node.children.first().kind != "table_header" || rows[0].isEmpty()) return null
            val width = rows[0].size
            val padded = rows.map { it + List((width - it.size).coerceAtLeast(0)) { InlineText() } }.map { it.take(width) }
            val delimiter = source.lines().getOrNull(1)?.trim()?.trim('|')?.split('|')?.map { it.trim() }.orEmpty()
            val valid = delimiter.size == width && delimiter.all { Regex(":?-+:?").matches(it) }
            return BlockContent.Table(padded[0], padded.drop(1), if (valid) delimiter else List(width) { "---" })
        }

        fun serialize(content: BlockContent, newline: String = "\n"): String {
            val markdown = when (content) {
                is BlockContent.Raw -> content.text.replace("\r\n", "\n")
                BlockContent.Rule -> "---"
                BlockContent.PageBreak -> PAGE_BREAK_MARKER
                is BlockContent.Rich -> {
                    val text = content.text.toMarkdown()
                    if (content.level == 0) escapeLineStarts(text)
                    else {
                        val line = text.replace('\n', ' ').trim().let { if (it.endsWith("#")) it.dropLast(1) + "\\#" else it }
                        ("#".repeat(content.level) + " " + line).trimEnd()
                    }
                }
                is BlockContent.Items -> content.items.mapIndexed { i, item ->
                    val marker = if (content.ordered) "${content.startAt + i}${content.marker}" else content.marker.toString()
                    val indent = " ".repeat(marker.length + 1)
                    val lines = escapeLineStarts(item.toMarkdown()).split('\n')
                    (marker + " " + lines.first()).trimEnd() + lines.drop(1).joinToString("") { "\n$indent$it" }
                }.joinToString("\n")
                is BlockContent.Quote -> content.paragraphs.joinToString("\n>\n") { paragraph ->
                    escapeLineStarts(paragraph.toMarkdown()).split('\n').joinToString("\n") { "> $it".trimEnd() }
                }.ifEmpty { ">" }
                is BlockContent.Table -> {
                    fun row(cells: List<InlineText>) =
                        cells.joinToString(" | ", "| ", " |") { it.toMarkdown(table = true).replace('\n', ' ') }
                    (listOf(row(content.header), content.delimiter.joinToString(" | ", "| ", " |")) + content.rows.map(::row))
                        .joinToString("\n")
                }
            }
            return if (newline == "\n") markdown else markdown.replace("\n", newline)
        }
    }
}

/** Table edit helpers; tables keep at least one column and a header row. */
fun BlockContent.Table.setCell(row: Int, column: Int, text: InlineText): BlockContent.Table =
    if (row == 0) copy(header = header.replaced(column, text))
    else copy(rows = rows.mapIndexed { i, cells -> if (i == row - 1) cells.replaced(column, text) else cells })

fun BlockContent.Table.addRow(after: Int): BlockContent.Table =
    copy(rows = rows.take(after.coerceAtLeast(0)) + listOf(header.map { InlineText() }) + rows.drop(after.coerceAtLeast(0)))

fun BlockContent.Table.removeRow(row: Int): BlockContent.Table =
    if (row < 1 || row > rows.size) this else copy(rows = rows.filterIndexed { i, _ -> i != row - 1 })

fun BlockContent.Table.addColumn(after: Int): BlockContent.Table {
    val at = (after + 1).coerceIn(0, header.size)
    fun <T> List<T>.insert(value: T) = take(at) + value + drop(at)
    return copy(header = header.insert(InlineText()), rows = rows.map { it.insert(InlineText()) }, delimiter = delimiter.insert("---"))
}

fun BlockContent.Table.removeColumn(column: Int): BlockContent.Table =
    if (header.size <= 1 || column !in header.indices) this else copy(
        header = header.filterIndexed { i, _ -> i != column },
        rows = rows.map { it.filterIndexed { i, _ -> i != column } },
        delimiter = delimiter.filterIndexed { i, _ -> i != column },
    )

private fun List<InlineText>.replaced(index: Int, text: InlineText) = mapIndexed { i, old -> if (i == index) text else old }
