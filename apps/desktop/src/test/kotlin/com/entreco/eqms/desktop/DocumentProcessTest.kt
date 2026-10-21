package com.entreco.eqms.desktop

import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.*
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

    @Test
    fun processDrainsOutputWhileWritingAndPropagatesSuccessfulProcessWriteFailure() {
        val result = executeProcess(
            "python3", listOf("-c", "import sys; sys.stdout.write('x' * 1000000); sys.stdout.flush(); sys.stdin.read()"),
            "input\n".repeat(200000),
        )
        assertEquals(0, result.exitCode)
        assertEquals(1000000, result.output.length)
        assertFailsWith<IOException> {
            executeProcess("python3", listOf("-c", "import os; os.close(0)"), "input\n".repeat(200000))
        }
    }

    @Test
    fun cancellingSearchPreparationTerminatesItsProcessAndStreamThreads() = runBlocking {
        val marker = Path.of("build", "search-cancellation-${UUID.randomUUID()}.pid").toAbsolutePath()
        Files.createDirectories(marker.parent)
        var handle: ProcessHandle? = null
        val capabilities = DesktopDocumentCapabilities("python3", DocumentExecutor { executable, _, _ ->
            executeProcess(
                executable,
                listOf("-c", "import os, pathlib, sys, time; pathlib.Path(sys.argv[1]).write_text(str(os.getpid())); time.sleep(120)", marker.toString()),
            )
        })
        val operation = async { capabilities.prepareSearch("project") }
        try {
            withTimeout(10_000) { while (!Files.exists(marker)) delay(5) }
            val pid = Files.readString(marker).toLong()
            handle = ProcessHandle.of(pid).orElseThrow()
            assertTrue(handle.isAlive)
            withTimeout(5_000) { operation.cancelAndJoin() }
            assertTrue(operation.isCancelled)
            assertFalse(handle.isAlive, "Cancelled preparation must terminate its native process.")
            assertFalse(Thread.getAllStackTraces().keys.any {
                it.isAlive && it.name in setOf("qms-stdout-$pid", "qms-stdin-$pid")
            })
        } finally {
            handle?.takeIf { it.isAlive }?.destroyForcibly()
            operation.cancelAndJoin()
            Files.deleteIfExists(marker)
        }
    }
}
