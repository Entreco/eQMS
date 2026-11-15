package com.entreco.eqms.desktop

import com.entreco.eqms.feature.ui.BrandLogo
import java.io.IOException
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopBrandTokensTest {
    private fun envelope(command: String, data: String, status: String = "ok", error: String = "null") =
        """{"schema_version":2,"status":"$status","command":"$command","timestamp_unix_ms":1,"findings":[],"references":[],"data":$data,"error":$error}"""

    private val good = """{"name":"Acme","accent":"#C2410C","on_accent":"#FFFFFF","persisted":true,"content_hash":null,"warnings":["low contrast"],"logo":{"kind":"generated","seed":"s","generated":{}}}"""

    private fun capabilities(result: ProcessResult, commands: MutableList<List<String>> = mutableListOf()) =
        DesktopProjectCapabilities(WorkspaceCli("qms") { _, args -> commands += args; result })

    private fun tokens(result: ProcessResult) =
        runBlocking { capabilities(result).brandTokens("/project with spaces") }

    @Test
    fun mapsCliDataToTokensUsingTheBrandingCommand() {
        val commands = mutableListOf<List<String>>()
        val result = runBlocking {
            capabilities(ProcessResult(0, envelope("project.branding.show", good)), commands)
                .brandTokens("/project with spaces")
        }.getOrThrow()!!
        assertEquals(listOf(listOf("project", "branding", "show", "--json", "/project with spaces")), commands)
        assertEquals("Acme", result.name)
        assertEquals(listOf("low contrast"), result.warnings)
        assertEquals(BrandLogo.Generated("s"), result.logo)
    }

    @Test
    fun mapsPngLogo() {
        val sha = "a".repeat(64)
        val data = good.replace(""""kind":"generated","seed":"s"""", """"kind":"png","sha256":"$sha"""")
        assertEquals(BrandLogo.Png(sha), tokens(ProcessResult(0, envelope("project.branding.show", data))).getOrThrow()!!.logo)
    }

    @Test
    fun reportsFailuresWithoutTokens() {
        val error = envelope("project.branding.show", "null", "error", """{"code":"OPERATION_FAILED","message":"tampered"}""")
        assertEquals("tampered", tokens(ProcessResult(1, error)).exceptionOrNull()?.message)
        assertTrue(tokens(ProcessResult(0, "not json")).isFailure)
        assertTrue(tokens(ProcessResult(0, envelope("verify", good))).isFailure)
        assertTrue(tokens(ProcessResult(0, envelope("project.branding.show", good.replace("#C2410C", "red")))).isFailure)
        assertTrue(tokens(ProcessResult(0, envelope("project.branding.show", good.replace("\"warnings\":[\"low contrast\"],", "")))).isFailure)
        assertTrue(tokens(ProcessResult(0, envelope("project.branding.show", """{"name":"A"}"""))).isFailure)
    }

    @Test
    fun reportsMissingExecutable() {
        val projects = DesktopProjectCapabilities(WorkspaceCli("qms") { _, _ -> throw IOException("no such file") })
        val failure = runBlocking { projects.brandTokens(Paths.get("/p").toString()) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("no such file"))
    }
}
