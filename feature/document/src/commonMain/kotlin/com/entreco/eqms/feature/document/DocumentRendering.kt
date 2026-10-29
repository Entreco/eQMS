package com.entreco.eqms.feature.document

data class RenderNode(
    val kind: String,
    val text: String = "",
    val target: String = "",
    val anchor: String = "",
    val children: List<RenderNode> = emptyList(),
) {
    fun plainText(): String = text + children.joinToString("") { it.plainText() }
}

data class RenderHeading(val level: Int, val title: String, val anchor: String)
data class RenderSource(val kind: String, val id: String, val revision: String, val contentHash: String)
data class RenderedDocument(
    val title: String,
    val inputDigest: String,
    val source: RenderSource,
    val nodes: List<RenderNode>,
    val toc: List<RenderHeading>,
    val diagnostics: List<String>,
)
data class DocumentImage(val bytes: ByteArray, val width: Int, val height: Int)

enum class DocumentViewMode { Preview, Source, Split }

data class DocumentRenderTiming(val inputDigest: String, val elapsedMillis: Double)
