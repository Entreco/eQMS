package com.entreco.eqms.desktop

import com.entreco.eqms.feature.scaffold.BrandingDraft
import com.entreco.eqms.feature.scaffold.GeneratedLogoShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopProjectBrandingTest {
    private fun envelope(command: String, data: String, status: String = "ok", error: String = "null") =
        """{"schema_version":2,"status":"$status","command":"$command","timestamp_unix_ms":1,"findings":[],"references":[],"data":$data,"error":$error}"""

    private val preview = """{"name":"Acme","accent":"#C2410C","on_accent":"#FFFFFF","persisted":false,"content_hash":null,"warnings":["low contrast"],"logo":{"kind":"generated","seed":"Acme","generated":{"color":"#C2410C","initials":"A","shape":"hexagon"}}}"""

    private fun capabilities(result: ProcessResult, commands: MutableList<List<String>> = mutableListOf()) =
        DesktopProjectCapabilities(WorkspaceCli("qms") { _, args -> commands += args; result })

    @Test
    fun previewUsesTheWorkspaceFreeCommandAndMapsLogoInstructions() {
        val commands = mutableListOf<List<String>>()
        val result = runBlocking {
            capabilities(ProcessResult(0, envelope("project.branding.preview", preview)), commands)
                .previewBranding(BrandingDraft("-Acme", "-seed", "auto"))
        }.getOrThrow()
        assertEquals(
            listOf(listOf("project", "branding", "preview", "--json", "--name=-Acme", "--seed=-seed", "--accent=auto")),
            commands,
        )
        assertEquals(listOf("low contrast"), result.tokens.warnings)
        assertEquals("A", result.generated?.initials)
        assertEquals(GeneratedLogoShape.Hexagon, result.generated?.shape)
    }

    @Test
    fun pngLogoReplacesSeedAndOmitsAutoAccent() {
        assertEquals(
            listOf("--name=Acme", "--logo-file=/p/logo.png"),
            brandingArguments(BrandingDraft("Acme", "ignored", "auto", "/p/logo.png")),
        )
        assertEquals(
            listOf("--name=Acme", "--logo-file=/p/logo.png", "--accent=#112233"),
            brandingArguments(BrandingDraft("Acme", "ignored", "#112233", "/p/logo.png")),
        )
    }

    @Test
    fun previewRejectsErrorsWrongCommandsAndBadShapes() {
        fun preview(result: ProcessResult) = runBlocking { capabilities(result).previewBranding(BrandingDraft("A")) }
        val error = envelope("project.branding.preview", "null", "error", """{"code":"OPERATION_FAILED","message":"accent must be #RRGGBB"}""")
        assertEquals("accent must be #RRGGBB", preview(ProcessResult(1, error)).exceptionOrNull()?.message)
        assertTrue(preview(ProcessResult(0, "not json")).isFailure)
        assertTrue(preview(ProcessResult(0, envelope("project.branding.show", this.preview))).isFailure)
        assertTrue(preview(ProcessResult(0, envelope("project.branding.preview", this.preview.replace("hexagon", "star")))).isFailure)
    }

    @Test
    fun createPassesBrandingToInitAndReportsFailureWithAHint() {
        val commands = mutableListOf<List<String>>()
        val ok = envelope("init", """{"path":"/p","created_resources":[],"branding_name":"Acme"}""")
        val path = runBlocking {
            capabilities(ProcessResult(0, ok), commands).createProject("/p", BrandingDraft("Acme", "Acme", "#112233"))
        }.getOrThrow()
        assertEquals("/p", path)
        assertEquals(listOf("init", "--json", "/p", "--name=Acme", "--seed=Acme", "--accent=#112233"), commands.single())

        val failed = envelope("init", "null", "error", """{"code":"OPERATION_FAILED","message":"logo is not a decodable PNG"}""")
        val failure = runBlocking { capabilities(ProcessResult(1, failed)).createProject("/p", BrandingDraft("Acme")) }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().startsWith("logo is not a decodable PNG"))
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("File > Open project"))
        assertNull(failure.getOrNull())
    }
}
