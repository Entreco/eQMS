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
        }, root["style"]?.let { decodeStyle(it, toc.map { heading -> heading.anchor }.toSet()) })
}

private fun JsonObject.str(name: String): String {
    val value = requireNotNull(this[name]) { "Missing style $name." }.jsonPrimitive
    require(value.isString) { "Style $name must be text." }
    return value.content
}

private fun JsonObject.int(name: String, range: IntRange): Int {
    val value = requireNotNull(this[name]) { "Missing style $name." }.jsonPrimitive
    require(!value.isString) { "Style $name must be a number." }
    return requireNotNull(value.intOrNull) { "Style $name must be an integer." }
        .also { require(it in range) { "Style $name is out of range." } }
}

private fun JsonObject.bool(name: String): Boolean {
    val value = requireNotNull(this[name]) { "Missing style $name." }.jsonPrimitive
    require(!value.isString) { "Style $name must be true or false." }
    return requireNotNull(value.booleanOrNull) { "Style $name must be true or false." }
}

private inline fun <reified E : Enum<E>> JsonObject.choice(name: String): E {
    val text = str(name)
    return requireNotNull(enumValues<E>().firstOrNull { it.name.lowercase() == text.replace("_", "") }) {
        "Unknown style $name: $text."
    }
}

/** Decodes and validates the style model of the core render contract; unknown values are rejected. */
internal fun decodeStyle(data: JsonElement, anchors: Set<String>): DocumentStyle = try {
    decodeStyleUnchecked(data, anchors)
} catch (failure: NoSuchElementException) {
    throw IllegalArgumentException("Style is incomplete: ${failure.message}", failure)
}

private fun decodeStyleUnchecked(data: JsonElement, anchors: Set<String>): DocumentStyle {
    val root = data.jsonObject
    require(root.int("style_schema_version", 1..1) == 1) { "Unsupported style schema." }
    val template = root.getValue("template").jsonObject
    val color = root.str("heading_color")
    require(Regex("#[0-9A-F]{6}").matches(color)) { "Invalid heading colour." }
    val sizes = root.getValue("heading_sizes_pt").jsonArray.map {
        requireNotNull(it.jsonPrimitive.intOrNull?.takeIf { size -> size in 4..64 }) { "Invalid heading size." }
    }
    require(sizes.size == 6) { "Heading sizes must cover h1 to h6." }
    val tocValue = root.getValue("toc").jsonObject
    val toc = StyleToc(tocValue.bool("enabled"), tocValue.int("depth", 1..6), tocValue.bool("leaders"),
        tocValue.choice("placement"))
    val images = root.getValue("images").jsonObject.also { require(it.size <= 9) { "Too many style images." } }
        .mapValues { (key, value) ->
            val image = value.jsonObject
            val width = image.int("width", 1..1024)
            val height = image.int("height", 1..1024)
            val encoded = image.str("png_base64")
            require(encoded.length <= 700_000) { "Style image $key is too large." }
            DocumentImage(java.util.Base64.getDecoder().decode(encoded), width, height)
        }
    fun slot(value: JsonElement): StyleSlot {
        val item = value.jsonObject
        val segments = item.getValue("segments").jsonArray.map {
            val segment = it.jsonObject
            when (segment.str("kind")) {
                "text" -> StyleSegment.Text(segment.str("text"))
                "page" -> StyleSegment.Page
                "total" -> StyleSegment.Total
                else -> throw IllegalArgumentException("Unknown region segment.")
            }
        }
        val key = item["image"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
        require(key == null || key in images) { "Region image is missing." }
        return StyleSlot(segments, key)
    }
    fun region(value: JsonElement): StyleRegion {
        val item = value.jsonObject
        return StyleRegion(slot(item.getValue("left")), slot(item.getValue("centre")), slot(item.getValue("right")))
    }
    val regions = root["regions"]?.takeUnless { it is JsonNull }?.jsonObject?.let {
        StyleRegions(region(it.getValue("header")), region(it.getValue("footer")))
    }
    val numbers = root.getValue("numbers").jsonObject.mapValues { (_, value) ->
        require(value.jsonPrimitive.isString) { "Heading numbers must be text." }
        value.jsonPrimitive.content
    }
    require(anchors.containsAll(numbers.keys)) { "Heading numbers refer to unknown headings." }
    val pageBreak = root["page_break_before_level"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
    require(root["page_break_before_level"].let { it == null || it is JsonNull || pageBreak in 1..6 }) {
        "Invalid page-break level."
    }
    return DocumentStyle(
        templateId = template.str("id"), templateName = template.str("name"),
        templateVersion = template.int("version", 1..Int.MAX_VALUE), templateHash = template.str("hash"),
        origin = template.choice("origin"),
        bodyFont = root.choice("body_font"), headingFont = root.choice("heading_font"),
        bodySizePt = root.int("body_size_pt", 8..16), headingSizesPt = sizes,
        headingColorRgb = color.drop(1).toInt(16), headingNumbering = root.bool("heading_numbering"),
        table = root.choice("table"), imageMaxWidthPercent = root.int("image_max_width_percent", 10..100),
        imageAlign = root.choice("image_align"), toc = toc, coverPage = root.bool("cover_page"),
        pageBreakBeforeLevel = pageBreak, regions = regions, images = images, numbers = numbers,
    )
}
