package com.entreco.eqms.desktop

import java.io.IOException
import java.net.URI
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import java.util.prefs.AbstractPreferences
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DesktopChromeAdaptersTest {
    private fun release(tag: String = "v1.2.3", url: String = "https://github.com/Entreco/eQMS/releases/tag/$tag") =
        """{"tag_name":"$tag","html_url":"$url","draft":false,"prerelease":false}"""

    private fun check(version: String = "1.0.0", status: Int = 200, body: String = release()) = runBlocking {
        DesktopUpdates(version, ReleaseTransport { ReleaseResponse(status, body) }).check()
    }

    @Test
    fun stableVersionsCompareNumericallyAndDevelopmentIsNotUpToDate() {
        assertContains(check().getOrThrow().message, "available")
        assertContains(check("1.2.3").getOrThrow().message, "up to date")
        assertContains(check("1.10.0").getOrThrow().message, "newer than")
        assertContains(check("1.2.3+build.2").getOrThrow().message, "up to date")
        assertContains(check("1.0.0-dev").getOrThrow().message, "cannot be compared")
        assertContains(check(body = release("nightly")).getOrThrow().message, "cannot be compared")
        assertContains(check(body = release("v1.2.3-rc1")).getOrThrow().message, "cannot be compared")
        assertContains(check(body = release("v999999999999999999999.0.0")).getOrThrow().message, "available")
        assertContains(check(status = 404, body = "").getOrThrow().message, "No published stable releases")
    }

    @Test
    fun invalidAndUnsafeReleaseMetadataIsNotASuccess() {
        for (body in listOf(
            "not JSON", "[]", "{}", release().replace("false", "true"),
            release().replace("\"draft\":false", "\"draft\":\"false\""),
            release(url = "https://evil.invalid/release"),
            release(url = "https://github.com/Other/repo/releases/tag/v1.2.3"),
            release(url = "https://github.com/Entreco/eQMS/releases/tag/v9.0.0"),
            release(url = "https://github.com/Entreco/eQMS/releases/tag/v1.2.3?redirect=evil"),
            release(url = "https://github.com/Entreco/eQMS/releases/tag/%2e%2e"),
            release(url = "https://github.com/Entreco/eQMS/releases/tag/v1.2.3#fragment"),
            release(url = "invalid url"),
            " ".repeat(MAX_RELEASE_BYTES + 1),
        )) assertTrue(check(body = body).isFailure, body.take(100))
        for (status in listOf(301, 403, 429, 500)) {
            assertContains(check(status = status).exceptionOrNull()!!.message!!, "HTTP $status")
        }
    }

    @Test
    fun networkAndBrowserFailuresAreExplicit() = runBlocking {
        for (failure in listOf(IOException("offline"), HttpTimeoutException("timeout"))) {
            val updates = DesktopUpdates("1.0.0", ReleaseTransport { throw failure })
            assertEquals(failure, updates.check().exceptionOrNull())
        }
        val opened = mutableListOf<URI>()
        val updates = DesktopUpdates("1.0.0", ReleaseTransport { error("No request expected") }, opened::add)
        assertTrue(updates.open("https://evil.invalid").isFailure)
        assertTrue(opened.isEmpty())
        updates.open("https://github.com/Entreco/eQMS/releases/tag/v1.2.3").getOrThrow()
        assertEquals(1, opened.size)
        val failing = DesktopUpdates("1.0.0", ReleaseTransport { error("No request expected") }) { throw IOException("browser unavailable") }
        assertContains(failing.open(opened.single().toString()).exceptionOrNull()!!.message!!, "browser unavailable")
    }

    @Test
    fun releaseBodyEnforcesByteLimitAndCancelsSubscription() {
        var cancelled = false
        val body = LimitedReleaseBody()
        body.onSubscribe(object : Flow.Subscription {
            override fun request(n: Long) {}
            override fun cancel() { cancelled = true }
        })
        body.onNext(listOf(ByteBuffer.wrap(ByteArray(MAX_RELEASE_BYTES))))
        assertFalse(body.body.toCompletableFuture().isDone)
        body.onNext(listOf(ByteBuffer.wrap(byteArrayOf(1))))
        assertTrue(cancelled)
        assertTrue(body.body.toCompletableFuture().isCompletedExceptionally)
        val small = LimitedReleaseBody()
        small.onSubscribe(object : Flow.Subscription {
            override fun request(n: Long) {}
            override fun cancel() {}
        })
        small.onNext(listOf(ByteBuffer.wrap("{}".toByteArray())))
        small.onComplete()
        assertEquals("{}", small.body.toCompletableFuture().get().toString(Charsets.UTF_8))
    }

    private class MemoryPreferences : AbstractPreferences(null, "") {
        val values = mutableMapOf<String, String>()
        var failFlush = false
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun keysSpi() = values.keys.toTypedArray()
        override fun childrenNamesSpi() = emptyArray<String>()
        override fun childSpi(name: String): AbstractPreferences = error("No child node expected")
        override fun removeNodeSpi() {}
        override fun syncSpi() {}
        override fun flushSpi() { if (failFlush) throw BackingStoreException("disk failure") }
    }

    @Test
    fun appearancePersistsAcrossAdapterInstancesAndReportsFailures() = runBlocking {
        val node = MemoryPreferences()
        val preferences = DesktopPreferences { node }
        assertTrue(preferences.readDark().getOrThrow())
        preferences.saveDark(false).getOrThrow()
        assertFalse(DesktopPreferences { node }.readDark().getOrThrow())
        preferences.saveDark(true).getOrThrow()
        assertTrue(DesktopPreferences { node }.readDark().getOrThrow())
        node.put("appearance", "invalid")
        assertTrue(preferences.readDark().isFailure)
        node.failFlush = true
        assertTrue(preferences.saveDark(false).isFailure)
        val denied = DesktopPreferences { throw SecurityException("access denied") }
        assertTrue(denied.readDark().isFailure)
        assertTrue(denied.saveDark(true).isFailure)
    }

    @Test
    fun realPreferenceStorageSurvivesReloadInIsolatedNode() = runBlocking {
        val node = Preferences.userRoot().node("com/entreco/dutchdox-test-${java.util.UUID.randomUUID()}")
        try {
            DesktopPreferences { node }.saveDark(false).getOrThrow()
            val classpath = listOf(AppearancePreferenceProbe::class.java, Unit::class.java)
                .joinToString(java.io.File.pathSeparator) { java.io.File(it.protectionDomain.codeSource.location.toURI()).path }
            val process = ProcessBuilder(
                java.io.File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath,
                AppearancePreferenceProbe::class.java.name, node.absolutePath(),
            ).redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), "Preference probe timed out")
                val output = process.inputStream.bufferedReader().use { it.readText() }
                assertEquals(0, process.exitValue(), output)
                assertEquals("light", output)
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            node.removeNode()
            Preferences.userRoot().flush()
        }
    }

    internal object AppearancePreferenceProbe {
        @JvmStatic
        fun main(arguments: Array<String>) {
            print(Preferences.userRoot().node(arguments.single()).get("appearance", "unset"))
        }
    }

    @Test
    fun aboutVersionComesFromGeneratedMetadata() {
        assertTrue(Regex("[1-9][0-9]*\\.[0-9]+\\.[0-9]+.*").matches(desktopVersion()))
        assertFalse(desktopVersion().contains('$'))
    }
}
