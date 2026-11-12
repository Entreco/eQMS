package com.entreco.eqms.feature.document

/** Inline style bits. Links are tracked separately per character. */
object InlineMark {
    const val Bold = 1
    const val Italic = 2
    const val Strike = 4
    const val Code = 8
}

/** Rich inline text with per-character styles; serialized to compact Markdown. */
data class InlineText(
    val text: String = "",
    val bits: List<Int> = List(text.length) { 0 },
    val links: List<String?> = List(text.length) { null },
) {
    init { require(bits.size == text.length && links.size == text.length) { "Inconsistent inline text." } }

    val length get() = text.length

    fun replace(start: Int, end: Int, inserted: String, bit: Int, link: String?): InlineText {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        return InlineText(
            text.substring(0, s) + inserted + text.substring(e),
            bits.subList(0, s) + List(inserted.length) { bit } + bits.subList(e, length),
            links.subList(0, s) + List(inserted.length) { link } + links.subList(e, length),
        )
    }

    fun slice(start: Int, end: Int) = InlineText(text.substring(start, end), bits.subList(start, end), links.subList(start, end))
    operator fun plus(other: InlineText) = InlineText(text + other.text, bits + other.bits, links + other.links)

    fun hasAll(start: Int, end: Int, bit: Int) = start < end && (start until end).all { bits[it] and bit != 0 }

    fun toggle(start: Int, end: Int, bit: Int): InlineText {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        if (s == e) return this
        val on = !hasAll(s, e, bit)
        return copy(bits = bits.mapIndexed { i, b -> if (i in s until e) (if (on) b or bit else b and bit.inv()) else b })
    }

    fun withLink(start: Int, end: Int, target: String?): InlineText {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        return copy(links = links.mapIndexed { i, l -> if (i in s until e) target else l })
    }

    fun bitsAt(caret: Int): Int = when {
        length == 0 -> 0
        caret > 0 -> bits[(caret - 1).coerceAtMost(length - 1)]
        else -> bits[0]
    }

    /** Applies a plain-text field change, giving inserted text [pending] or its neighbour's style. */
    fun applyTextChange(newText: String, pending: Int? = null): InlineText {
        if (newText == text) return this
        var prefix = 0
        val max = minOf(text.length, newText.length)
        while (prefix < max && text[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix && text[text.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
        val removedEnd = text.length - suffix
        val inserted = newText.substring(prefix, newText.length - suffix)
        val before = if (prefix > 0) prefix - 1 else -1
        val bit = pending ?: when {
            before >= 0 -> bits[before]
            length > 0 -> bits[0]
            else -> 0
        }
        val link = if (before >= 0 && removedEnd < length && links[before] == links[removedEnd]) links[before] else null
        return replace(prefix, removedEnd, inserted, bit, link)
    }

    fun toMarkdown(table: Boolean = false): String {
        val out = StringBuilder()
        var i = 0
        while (i < length) {
            var j = i
            val link = links[i]
            while (j < length && links[j] == link) j++
            val inner = emit(i, j, 0, table)
            if (link == null || inner.isBlank()) out.append(inner)
            else out.append('[').append(inner).append("](").append(destination(link)).append(')')
            i = j
        }
        return out.toString()
    }

    private fun emit(start: Int, end: Int, level: Int, table: Boolean): String {
        if (start >= end) return ""
        if (level == MARKERS.size) return emitCode(start, end, table)
        val (bit, marker) = MARKERS[level]
        val out = StringBuilder()
        var i = start
        while (i < end) {
            var j = i
            val member = bits[i] and bit != 0
            while (j < end && (bits[j] and bit != 0) == member) j++
            if (!member) out.append(emit(i, j, level + 1, table))
            else {
                var s = i
                var e = j
                while (s < e && text[s].isWhitespace()) s++
                while (e > s && text[e - 1].isWhitespace()) e--
                out.append(text, i, s)
                if (s < e) out.append(marker).append(emit(s, e, level + 1, table)).append(marker)
                out.append(text, e, j)
            }
            i = j
        }
        return out.toString()
    }

    private fun emitCode(start: Int, end: Int, table: Boolean): String {
        val out = StringBuilder()
        var i = start
        while (i < end) {
            var j = i
            val code = bits[i] and InlineMark.Code != 0
            while (j < end && (bits[j] and InlineMark.Code != 0) == code) j++
            val part = text.substring(i, j)
            if (!code) out.append(escapeInline(part, table, if (i > 0) text[i - 1] else null, if (j < length) text[j] else null))
            else if (part.isBlank()) out.append(part)
            else {
                val body = if (table) part.replace("|", "\\|") else part
                val longest = Regex("`+").findAll(body).maxOfOrNull { it.value.length } ?: 0
                val fence = "`".repeat(longest + 1)
                val pad = if (body.startsWith("`") || body.endsWith("`") || (body.startsWith(" ") && body.endsWith(" "))) " " else ""
                out.append(fence).append(pad).append(body).append(pad).append(fence)
            }
            i = j
        }
        return out.toString()
    }

    companion object {
        private val MARKERS = listOf(InlineMark.Bold to "**", InlineMark.Italic to "*", InlineMark.Strike to "~~")

        fun destination(target: String): String =
            if (target.any { it == '(' || it == ')' || it == '<' || it == '>' || it.isWhitespace() }) "<${target.replace(">", "%3E")}>" else target

        private fun escapeInline(part: String, table: Boolean, before: Char?, after: Char?): String {
            val out = StringBuilder()
            part.forEachIndexed { index, c ->
                val prev = if (index > 0) part[index - 1] else before
                val next = if (index + 1 < part.length) part[index + 1] else after
                val intraword = c == '_' && prev?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true
                if (c in "\\`*[]<>~&" || (c == '_' && !intraword)) out.append('\\')
                if (c == '|' && table) out.append('\\')
                out.append(c)
            }
            return out.toString()
        }

        /** Builds inline text from semantic nodes; null if an unsupported inline (image, note, HTML) is present. */
        fun fromNodes(nodes: List<RenderNode>): InlineText? {
            var result = InlineText()
            fun add(node: RenderNode, bit: Int, link: String?): Boolean {
                fun leaf(value: String, extra: Int = 0) { result += InlineText(value, List(value.length) { bit or extra }, List(value.length) { link }) }
                return when (node.kind) {
                    "text" -> { leaf(node.text); true }
                    "break" -> { leaf("\n"); true }
                    "code" -> { leaf(node.text, InlineMark.Code); true }
                    "strong" -> node.children.all { add(it, bit or InlineMark.Bold, link) }
                    "emphasis" -> node.children.all { add(it, bit or InlineMark.Italic, link) }
                    "strike" -> node.children.all { add(it, bit or InlineMark.Strike, link) }
                    "link" -> node.children.all { add(it, bit, node.target) }
                    else -> false
                }
            }
            return if (nodes.all { add(it, 0, null) }) result else null
        }
    }
}

/** Escapes characters that would start a block when they begin a line of paragraph-like text. */
internal fun escapeLineStarts(markdown: String): String = markdown.split('\n').joinToString("\n") { line ->
    val lead = line.length - line.trimStart().length
    val rest = line.substring(lead)
    when {
        Regex("^#{1,6}(\\s|$)").containsMatchIn(rest) -> line.substring(0, lead) + "\\" + rest
        rest.startsWith(">") -> line.substring(0, lead) + "\\" + rest
        Regex("^[-+=](\\s|$|[-=+])").containsMatchIn(rest) -> line.substring(0, lead) + "\\" + rest
        else -> Regex("^(\\d{1,9})([.)])(\\s|$)").find(rest)?.let {
            line.substring(0, lead) + it.groupValues[1] + "\\" + rest.substring(it.groupValues[1].length)
        } ?: line
    }
}
