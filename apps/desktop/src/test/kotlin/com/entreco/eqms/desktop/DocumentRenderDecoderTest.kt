package com.entreco.eqms.desktop

import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class DocumentRenderDecoderTest {
    private fun payload(
        kind: String = "paragraph",
        schema: Int = 2,
        renderer: String = "eqms-markdown-2",
        pageBreakText: String? = null,
        pageBreakChildren: JsonArray = JsonArray(emptyList()),
        nestedPageBreak: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("semantic_schema", schema); put("renderer", renderer)
        put("title", "Document"); put("input_digest", bodyDigest("text"))
        put("source", buildJsonObject {
            put("kind", "unsaved"); put("id", ""); put("revision", ""); put("content_hash", "")
        })
        put("nodes", buildJsonArray {
            if (nestedPageBreak) {
                add(buildJsonObject {
                    put("kind", "paragraph")
                    put("children", buildJsonArray { add(buildJsonObject { put("kind", "page_break") }) })
                })
            } else {
                add(buildJsonObject {
                    put("kind", kind)
                    if (kind == "page_break") {
                        if (pageBreakText != null) put("text", pageBreakText)
                        if (pageBreakChildren.isNotEmpty()) put("children", pageBreakChildren)
                    } else {
                        put("text", "text")
                    }
                })
            }
        })
        put("toc", JsonArray(emptyList())); put("diagnostics", JsonArray(emptyList()))
    }

    @Test
    fun versionedSchemaAndAllNodesAreStrictlyDecoded() {
        assertEquals("text", renderedDocument(payload()).nodes.single().text)
        assertFailsWith<IllegalArgumentException> { renderedDocument(payload(schema = 1, renderer = "eqms-markdown-1")) }
        assertFailsWith<IllegalArgumentException> { renderedDocument(payload(renderer = "eqms-markdown-1")) }
        assertFailsWith<IllegalArgumentException> { renderedDocument(payload(kind = "executable_html")) }
        assertEquals("sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", bodyDigest(""))
    }

    @Test
    fun pageBreakIsAcceptedOnlyAsAnEmptySemanticNodeAndKeepsProvenance() {
        val decoded = renderedDocument(payload(kind = "page_break"))
        assertEquals("page_break", decoded.nodes.single().kind)
        assertEquals(bodyDigest("text"), decoded.inputDigest)
        assertEquals("unsaved", decoded.source.kind)
        assertEquals("", decoded.source.contentHash)

        assertFailsWith<IllegalArgumentException> {
            renderedDocument(payload(kind = "page_break", pageBreakText = "unexpected"))
        }
        assertFailsWith<IllegalArgumentException> {
            renderedDocument(
                payload(kind = "page_break", pageBreakChildren = buildJsonArray {
                    add(buildJsonObject { put("kind", "text"); put("text", "unexpected") })
                }),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            renderedDocument(payload(nestedPageBreak = true))
        }
    }
}
