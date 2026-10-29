package com.entreco.eqms.desktop

import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class DocumentRenderDecoderTest {
    private fun payload(kind: String = "paragraph", schema: Int = 1): JsonObject = buildJsonObject {
        put("semantic_schema", schema); put("renderer", "eqms-markdown-1")
        put("title", "Document"); put("input_digest", bodyDigest("text"))
        put("source", buildJsonObject {
            put("kind", "unsaved"); put("id", ""); put("revision", ""); put("content_hash", "")
        })
        put("nodes", buildJsonArray { add(buildJsonObject { put("kind", kind); put("text", "text") }) })
        put("toc", JsonArray(emptyList())); put("diagnostics", JsonArray(emptyList()))
    }

    @Test
    fun schemaAndAllNodesAreStrictlyDecoded() {
        assertEquals("text", renderedDocument(payload()).nodes.single().text)
        assertFailsWith<IllegalArgumentException> { renderedDocument(payload(schema = 2)) }
        assertFailsWith<IllegalArgumentException> { renderedDocument(payload(kind = "executable_html")) }
        assertEquals("sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", bodyDigest(""))
    }
}
