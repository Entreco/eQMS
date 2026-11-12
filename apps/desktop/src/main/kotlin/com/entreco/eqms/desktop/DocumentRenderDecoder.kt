package com.entreco.eqms.desktop

import com.entreco.eqms.feature.document.*
import kotlinx.serialization.json.*
import java.security.MessageDigest

internal fun bodyDigest(body: String): String = bodyDigest(body.toByteArray(Charsets.UTF_8))
internal fun bodyDigest(bytes: ByteArray): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun renderedDocument(data: JsonElement): RenderedDocument {
    val root = data.jsonObject
    require(!root.getValue("semantic_schema").jsonPrimitive.isString &&
        root.getValue("semantic_schema").jsonPrimitive.int == 3 &&
        root.getValue("renderer").jsonPrimitive.content == "eqms-markdown-3") { "Unsupported document renderer/schema." }
    var count = 0
    val kinds = setOf("text", "paragraph", "h1", "h2", "h3", "h4", "h5", "h6",
        "quote", "code_block", "ordered", "unordered", "item", "emphasis", "strong",
        "strike", "link", "image", "table", "table_header", "table_row", "table_cell",
        "footnote", "literal", "code", "break", "rule", "note_link", "section", "page_break")
    fun JsonObject.text(name: String, optional: Boolean = false): String {
        val item = this[name]
        if (item == null && optional) return ""
        val value = requireNotNull(item) { "Missing $name." }.jsonPrimitive
        require(value.isString) { "$name must be text." }
        return value.content
    }
    fun node(value: JsonElement, depth: Int, topLevel: Boolean): RenderNode {
        require(++count <= 100_000 && depth <= 64) { "Rendering tree exceeds limits." }
        val item = value.jsonObject
        val kind = item.text("kind")
        require(kind in kinds) { "Unsupported rendering node: $kind." }
        val text = item.text("text", true)
        val target = item.text("target", true)
        val anchor = item.text("anchor", true)
        val children = item["children"]?.jsonArray?.map { node(it, depth + 1, false) }.orEmpty()
        if (kind == "page_break") {
            require(topLevel && text.isEmpty() && target.isEmpty() && anchor.isEmpty() && children.isEmpty()) {
                "Page-break nodes must be empty and top-level."
            }
        }
        fun JsonObject.offset(name: String): Int {
            val item = this[name] ?: return -1
            val value = item.jsonPrimitive
            require(!value.isString) { "$name must be a number." }
            return requireNotNull(value.intOrNull) { "$name must be an integer." }.also {
                require(it >= 0) { "$name must not be negative." }
            }
        }
        val start = item.offset("start")
        val end = item.offset("end")
        require(topLevel || (start < 0 && end < 0)) { "Source ranges are only valid on top-level nodes." }
        require((start < 0) == (end < 0) && (start < 0 || start <= end)) { "Invalid source range." }
        return RenderNode(kind, text, target, anchor, children, start, end)
    }
    val source = root.getValue("source").jsonObject
    val nodes = root.getValue("nodes").jsonArray.map { node(it, 1, true) }
    var previousEnd = 0
    nodes.forEach {
        if (it.start >= 0) {
            require(it.start >= previousEnd) { "Source ranges overlap or are out of order." }
            previousEnd = it.end
        }
    }
    val toc = root.getValue("toc").jsonArray.map {
        val value = it.jsonObject
        val level = value.getValue("level").jsonPrimitive.int
        require(!value.getValue("level").jsonPrimitive.isString && level in 1..6)
        RenderHeading(level, value.text("title"), value.text("anchor"))
    }
    require(toc.map { it.anchor }.distinct().size == toc.size) { "Duplicate heading anchors." }
    fun headings(node: RenderNode): List<RenderHeading> =
        (if (node.kind.matches(Regex("h[1-6]"))) listOf(RenderHeading(node.kind.last().digitToInt(),
            node.plainText(), node.anchor)) else emptyList()) + node.children.flatMap(::headings)
    require(nodes.flatMap(::headings) == toc) { "Table of contents does not match document headings." }
    return RenderedDocument(root.text("title"), root.text("input_digest"),
        RenderSource(source.text("kind"), source.text("id"), source.text("revision"), source.text("content_hash")),
        nodes, toc, root.getValue("diagnostics").jsonArray.map {
            require(it.jsonPrimitive.isString) { "Rendering diagnostics must be text." }
            it.jsonPrimitive.content
        })
}
