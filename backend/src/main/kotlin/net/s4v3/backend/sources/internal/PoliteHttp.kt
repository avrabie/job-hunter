package net.s4v3.backend.sources.internal

import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap

internal sealed interface Fetched {
    data class Body(val text: String, val etag: String?) : Fetched
    data object NotModified : Fetched

    /** [reason] is a fixed short text and never contains response content. [status] is the HTTP status, if one came. */
    data class Failed(val reason: String, val status: Int? = null) : Fetched
}

/**
 * GET requests for source adapters: identifying `User-Agent`, timeouts, a body size limit, and one request at a time
 * per host with a pause between consecutive requests.
 */
@Component
internal class PoliteHttp(private val settings: SourcesSettings) {

    private val client = HttpClient.newBuilder()
        .connectTimeout(settings.connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private class HostState { var lastFinished: Long = 0L }

    private val hosts = ConcurrentHashMap<String, HostState>()

    /**
     * [requireJson] rejects any other content type. [etag] is sent back as `If-None-Match`. [accept] replaces the
     * default `Accept` value (JSON, or plain text when [requireJson] is false), for example to ask for an XML feed.
     */
    fun get(uri: URI, maxBytes: Int, requireJson: Boolean = true, etag: String? = null, accept: String? = null): Fetched {
        val state = hosts.computeIfAbsent(uri.authority) { HostState() }
        synchronized(state) {
            waitForPause(state)
            try {
                return fetch(uri, maxBytes, requireJson, etag, accept)
            } finally {
                state.lastFinished = System.nanoTime()
            }
        }
    }

    private fun waitForPause(state: HostState) {
        if (state.lastFinished == 0L) return
        val remaining = settings.pause.toNanos() - (System.nanoTime() - state.lastFinished)
        if (remaining > 0) Thread.sleep(remaining / 1_000_000, (remaining % 1_000_000).toInt())
    }

    private fun fetch(uri: URI, maxBytes: Int, requireJson: Boolean, etag: String?, accept: String?): Fetched {
        val request = HttpRequest.newBuilder(uri)
            .timeout(settings.readTimeout)
            .header("User-Agent", settings.userAgent)
            .header("Accept", accept ?: if (requireJson) "application/json" else "text/plain, */*")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .GET()
            .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { stream ->
                when (val status = response.statusCode()) {
                    304 -> Fetched.NotModified
                    in 200..299 -> readBody(response, stream, maxBytes, requireJson)
                    else -> Fetched.Failed("HTTP status $status", status)
                }
            }
        } catch (e: java.net.http.HttpTimeoutException) {
            Fetched.Failed("timeout")
        } catch (e: IOException) {
            Fetched.Failed("connection failed")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Fetched.Failed("interrupted")
        }
    }

    private fun readBody(
        response: HttpResponse<*>, stream: java.io.InputStream, maxBytes: Int, requireJson: Boolean,
    ): Fetched {
        val type = response.headers().firstValue("Content-Type").orElse("").lowercase()
        if (requireJson && "json" !in type) return Fetched.Failed("not JSON", response.statusCode())
        val bytes = stream.readNBytes(maxBytes + 1)
        if (bytes.size > maxBytes) return Fetched.Failed("body too large", response.statusCode())
        return Fetched.Body(String(bytes, Charsets.UTF_8), response.headers().firstValue("ETag").orElse(null))
    }
}
