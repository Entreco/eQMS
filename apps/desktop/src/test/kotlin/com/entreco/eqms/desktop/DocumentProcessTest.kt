package com.entreco.eqms.desktop

import java.security.MessageDigest
import kotlin.test.*

class DocumentProcessTest {
    @Test
    fun processStreamsLargeUtf8BodyWithoutShellInterpretation() {
        val body = "# Policy: caf\u00e9\n\$literal `text`\n".repeat(20000)
        val result = executeProcess("git", listOf("hash-object", "--stdin"), body)
        assertEquals(0, result.exitCode)
        val oid = result.output.trim()
        assertTrue(Regex("([0-9a-f]{40}|[0-9a-f]{64})").matches(oid))
        val bytes = body.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance(if (oid.length == 40) "SHA-1" else "SHA-256")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        val expected = digest.digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, oid)
    }

    @Test
    fun earlyProcessFailureReturnsNonzeroRatherThanAFalseSuccessOrBrokenPipe() {
        val result = executeProcess("git", listOf("hash-object", "--invalid-option"), "body\n".repeat(200000))
        assertNotEquals(0, result.exitCode)
        assertTrue(result.output.isNotBlank())
    }
}
