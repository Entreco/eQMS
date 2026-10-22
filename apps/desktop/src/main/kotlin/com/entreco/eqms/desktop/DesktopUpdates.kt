package com.entreco.eqms.desktop

import com.entreco.eqms.feature.scaffold.ReleaseCheck
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Flow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

internal const val MAX_RELEASE_BYTES = 65536
internal data class ReleaseResponse(val status: Int, val body: String)

internal fun interface ReleaseTransport {
    suspend fun latest(): ReleaseResponse
}

internal class GitHubReleaseTransport : ReleaseTransport {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    override suspend fun latest(): ReleaseResponse {
        val request = HttpRequest.newBuilder(URI("https://api.github.com/repos/Entreco/eQMS/releases/latest"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DutchDoxx")
            .GET().build()
        val response = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<HttpResponse<ByteArray>> { continuation ->
                val future = client.sendAsync(request) { LimitedReleaseBody() }
                continuation.invokeOnCancellation { future.cancel(true) }
                future.whenComplete { value, error ->
                    if (error == null) continuation.resume(value)
                    else continuation.resumeWithException(if (error is CompletionException) error.cause ?: error else error)
                }
            }
        } ?: throw HttpTimeoutException("GitHub update check timed out.")
        return ReleaseResponse(response.statusCode(), response.body().toString(Charsets.UTF_8))
    }
}

internal class LimitedReleaseBody : HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val bytes = ByteArrayOutputStream()
    private lateinit var subscription: Flow.Subscription
    override fun getBody() = result
    override fun onSubscribe(value: Flow.Subscription) {
        subscription = value
        value.request(1)
    }
    override fun onNext(items: List<ByteBuffer>) {
        for (buffer in items) {
            if (buffer.remaining() > MAX_RELEASE_BYTES - bytes.size()) {
                subscription.cancel()
                result.completeExceptionally(IOException("Release metadata exceeds $MAX_RELEASE_BYTES bytes."))
                return
            }
            val chunk = ByteArray(buffer.remaining())
            buffer.get(chunk)
            bytes.write(chunk)
        }
        subscription.request(1)
    }
    override fun onError(error: Throwable) { result.completeExceptionally(error) }
    override fun onComplete() { result.complete(bytes.toByteArray()) }
}

internal fun safeReleaseUri(value: String): URI {
    val uri = URI.create(value)
    require(
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            Regex("/Entreco/eQMS/releases/tag/[A-Za-z0-9._+-]+").matches(uri.rawPath ?: ""),
    ) { "Invalid GitHub release page URL." }
    return uri
}

private fun stableVersion(value: String): List<java.math.BigInteger>? {
    val match = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?")
        .matchEntire(value) ?: return null
    return match.groupValues.drop(1).map { it.toBigInteger() }
}

internal class DesktopUpdates(
    private val version: String,
    private val transport: ReleaseTransport = GitHubReleaseTransport(),
    private val browse: (URI) -> Unit = { uri ->
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "Opening a browser is not supported on this desktop."
        }
        Desktop.getDesktop().browse(uri)
    },
) {
    suspend fun check(): Result<ReleaseCheck> = withContext(Dispatchers.IO) {
        try {
            val response = transport.latest()
            require(response.body.toByteArray(Charsets.UTF_8).size <= MAX_RELEASE_BYTES) { "Release metadata is too large." }
            val check = when (response.status) {
                404 -> ReleaseCheck("No published stable releases are available.")
                403, 429 -> throw IOException("GitHub refused the update check (HTTP ${response.status}); try again later.")
                200 -> parse(response.body)
                else -> throw IOException("GitHub update check failed (HTTP ${response.status}).")
            }
            Result.success(check)
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        } catch (error: SerializationException) {
            Result.failure(error)
        } catch (error: SecurityException) {
            Result.failure(error)
        }
    }

    private fun parse(body: String): ReleaseCheck {
        val metadata = Json.parseToJsonElement(body) as? JsonObject
            ?: throw IllegalArgumentException("Invalid release metadata.")
        fun isFalse(key: String) = (metadata[key] as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull == false } == true
        require(isFalse("draft") && isFalse("prerelease")) { "Expected a published stable release." }
        fun text(key: String): String = (metadata[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("Release metadata is missing $key.")
        val tag = text("tag_name")
        val url = safeReleaseUri(text("html_url")).toString()
        require(url.substringAfterLast('/') == tag) { "Release page does not match its version tag." }
        val latest = stableVersion(tag)
        val current = stableVersion(version)
        val message = if (latest == null || current == null) {
            "Latest release: $tag. Installed: $version. These versions cannot be compared automatically."
        } else {
            val comparison = latest.zip(current).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a.compareTo(b) } ?: 0
            when {
                comparison > 0 -> "DutchDoxx $tag is available. Installed: $version."
                comparison == 0 -> "DutchDoxx $version is up to date."
                else -> "Installed: $version, newer than the latest published release ($tag)."
            }
        }
        return ReleaseCheck(message, url)
    }

    suspend fun open(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            browse(safeReleaseUri(url))
            Result.success(Unit)
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        } catch (error: IllegalStateException) {
            Result.failure(error)
        } catch (error: UnsupportedOperationException) {
            Result.failure(error)
        } catch (error: SecurityException) {
            Result.failure(error)
        }
    }
}
