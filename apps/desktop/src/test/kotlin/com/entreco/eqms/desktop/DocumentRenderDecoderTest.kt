package com.entreco.eqms.desktop

import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class DocumentRenderDecoderTest {
    private fun payload(
        kind: String = "paragraph",
        schema: Int = 3,
        renderer: String = "eqms-markdown-3",
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

    private fun ranged(vararg ranges: Pair<Any, Any>, nested: Boolean = false): JsonObject {
        val base = payload()
        return JsonObject(base + ("nodes" to buildJsonArray {
            ranges.forEach { (start, end) ->
                add(buildJsonObject {
                    put("kind", "paragraph")
                    put("start", if (start is String) JsonPrimitive(start) else JsonPrimitive(start as Int))
                    put("end", if (end is String) JsonPrimitive(end) else JsonPrimitive(end as Int))
                    if (nested) put("children", buildJsonArray {
                        add(buildJsonObject { put("kind", "text"); put("start", 0); put("end", 1) })
                    })
                })
            }
        }))
    }

    @Test
    fun sourceRangesAreValidatedStrictly() {
        val decoded = renderedDocument(ranged(0 to 5, 7 to 9))
        assertEquals(listOf(0 to 5, 7 to 9), decoded.nodes.map { it.start to it.end })
        assertEquals(-1, renderedDocument(payload()).nodes.single().start)
        assertFailsWith<IllegalArgumentException> { renderedDocument(ranged(5 to 3)) }
        assertFailsWith<IllegalArgumentException> { renderedDocument(ranged(-1 to 3)) }
        assertFailsWith<IllegalArgumentException> { renderedDocument(ranged(0 to 5, 4 to 9)) }
        assertFailsWith<IllegalArgumentException> { renderedDocument(ranged("0" to "5")) }
        assertFailsWith<IllegalArgumentException> { renderedDocument(ranged(0 to 5, nested = true)) }
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

    private val goldenStyle: JsonObject by lazy {
        Json.parseToJsonElement(java.io.File("../../core/rust/tests/golden/style_formal_pinned.json").readText()).jsonObject
    }
    private val anchors = setOf("audience", "procedure", "purpose", "scope", "steps")

    @Test
    fun coreStyleGoldenIsDecodedIntoTheStyleModel() {
        val style = decodeStyle(goldenStyle, anchors)
        assertEquals("Formal SOP", style.templateName)
        assertEquals(com.entreco.eqms.feature.document.StyleOrigin.Pinned, style.origin)
        assertEquals(com.entreco.eqms.feature.document.StyleFamily.Serif, style.bodyFont)
        assertEquals(com.entreco.eqms.feature.document.StyleTocPlacement.AfterCover, style.toc.placement)
        assertTrue(style.coverPage)
        assertEquals(1, style.pageBreakBeforeLevel)
        assertEquals("2.0.1", style.numbers["steps"])
        assertEquals(setOf("project_logo"), style.images.keys)
        assertNotNull(style.regions)
    }

    @Test
    fun malformedOrUnknownStyleValuesAreRejected() {
        fun mutate(change: (MutableMap<String, JsonElement>) -> Unit) =
            JsonObject(goldenStyle.toMutableMap().also(change))
        listOf(
            mutate { it["style_schema_version"] = JsonPrimitive(2) },
            mutate { it["body_font"] = JsonPrimitive("comic") },
            mutate { it["heading_color"] = JsonPrimitive("red") },
            mutate { it["body_size_pt"] = JsonPrimitive(99) },
            mutate { it["heading_sizes_pt"] = buildJsonArray { add(JsonPrimitive(12)) } },
            mutate { it.remove("toc") },
            mutate { it["numbers"] = buildJsonObject { put("ghost", "1") } },
        ).forEach { assertFailsWith<IllegalArgumentException>(it.toString().take(40)) { decodeStyle(it, anchors) } }
    }

    @Test
    fun renderedDocumentCarriesStyleWhenPresent() {
        val withStyle = JsonObject(payload().toMutableMap().also { it["style"] = goldenStyle })
        assertNull(renderedDocument(payload()).style)
        val headings = anchors.sorted()
        val document = JsonObject(withStyle.toMutableMap().also {
            it["nodes"] = buildJsonArray {
                headings.forEach { a ->
                    add(buildJsonObject {
                        put("kind", "h1"); put("anchor", a)
                        put("children", buildJsonArray { add(buildJsonObject { put("kind", "text"); put("text", a) }) })
                    })
                }
            }
            it["toc"] = buildJsonArray {
                headings.forEach { a -> add(buildJsonObject { put("level", 1); put("title", a); put("anchor", a) }) }
            }
        })
        assertNotNull(renderedDocument(document).style)
    }
}
