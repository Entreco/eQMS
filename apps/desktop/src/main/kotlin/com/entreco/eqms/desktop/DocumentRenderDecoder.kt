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
        root.getValue("semantic_schema").jsonPrimitive.int == 2 &&
        root.getValue("renderer").jsonPrimitive.content == "eqms-markdown-2") { "Unsupported document renderer/schema." }
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
        return RenderNode(kind, text, target, anchor, children)
    }
    val source = root.getValue("source").jsonObject
    val nodes = root.getValue("nodes").jsonArray.map { node(it, 1, true) }
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
