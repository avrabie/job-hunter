package net.s4v3.backend.jobimport.internal

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.charset.Charset
import java.time.Duration
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

internal sealed interface FetchResult {
    data class Page(val html: String) : FetchResult
    data class Failed(val error: FetchError) : FetchResult
}

/**
 * One user-initiated GET of a job page. Every hop is checked by [UrlRules], the host must resolve only to public
 * addresses ([isPublic]), and the TLS socket connects to the address that was validated, so a second DNS answer
 * cannot redirect the request. Nothing about the URL or the response is logged.
 *
 * [resolver], [isPublic], [sslContext] and [port] exist for tests only; production uses the defaults.
 */
internal class SafeFetcher(
    private val userAgent: String,
    private val resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val isPublic: (InetAddress) -> Boolean = AddressRules::isPublic,
    private val sslContext: SSLContext = SSLContext.getDefault(),
    private val port: Int = 443,
    private val totalTimeout: Duration = Duration.ofSeconds(10),
) {
    private class Fail(val error: FetchError) : Exception(null, null, false, false)

    fun fetch(url: String): FetchResult {
        val deadline = System.nanoTime() + totalTimeout.toNanos()
        return try {
            var current = checked(url)
            repeat(MAX_REDIRECTS + 1) { hop ->
                val response = request(current, deadline)
                if (response.status in REDIRECTS) {
                    if (hop == MAX_REDIRECTS) throw Fail(FetchError.UNREACHABLE)
                    val location = response.location ?: throw Fail(FetchError.UNREACHABLE)
                    current = checked(resolveLocation(current, location))
                } else {
                    return FetchResult.Page(response.body)
                }
            }
            FetchResult.Failed(FetchError.UNREACHABLE)
        } catch (e: Fail) {
            FetchResult.Failed(e.error)
        } catch (_: IOException) {
            FetchResult.Failed(FetchError.UNREACHABLE)
        } catch (_: RuntimeException) {
            FetchResult.Failed(FetchError.UNREACHABLE)
        }
    }

    private fun checked(url: String): CheckedUrl = when (val check = UrlRules.check(url)) {
        is UrlCheck.Ok -> check.url
        is UrlCheck.Rejected -> throw Fail(check.error)
    }

    private fun resolveLocation(from: CheckedUrl, location: String): String =
        URI("https://${from.host}${from.target}").resolve(location.trim()).toString()

    private class Response(val status: Int, val location: String?, val body: String)

    private fun request(url: CheckedUrl, deadline: Long): Response {
        val addresses = try {
            resolver(url.host)
        } catch (_: IOException) {
            throw Fail(FetchError.UNREACHABLE)
        }
        if (addresses.isEmpty()) throw Fail(FetchError.UNREACHABLE)
        if (!addresses.all(isPublic)) throw Fail(FetchError.BLOCKED_ADDRESS)

        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(addresses.first(), port), remainingMillis(deadline))
            val tls = sslContext.socketFactory.createSocket(plain, url.host, port, true) as SSLSocket
            tls.sslParameters = tls.sslParameters.apply {
                serverNames = listOf(SNIHostName(url.host))
                endpointIdentificationAlgorithm = "HTTPS"
            }
            tls.soTimeout = remainingMillis(deadline)
            tls.startHandshake()
            val request = "GET ${url.target} HTTP/1.1\r\nHost: ${url.host}\r\nUser-Agent: $userAgent\r\n" +
                "Accept: text/html, application/xhtml+xml\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n"
            tls.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            tls.outputStream.flush()
            return readResponse(DeadlineStream(tls, deadline))
        } finally {
            try {
                plain.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun readResponse(input: InputStream): Response {
        val status = readLine(input).split(' ').getOrNull(1)?.toIntOrNull() ?: throw Fail(FetchError.UNREACHABLE)
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            if (headers.size >= MAX_HEADERS) throw Fail(FetchError.UNREACHABLE)
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        if (status in REDIRECTS) return Response(status, headers["location"], "")
        if (status !in 200..299) throw Fail(FetchError.UNREACHABLE)

        val contentType = headers["content-type"].orEmpty()
        val mediaType = contentType.substringBefore(';').trim().lowercase()
        if (mediaType != "text/html" && mediaType != "application/xhtml+xml") throw Fail(FetchError.NOT_HTML)
        val declared = headers["content-length"]?.toLongOrNull()
        if (declared != null && declared > MAX_BYTES) throw Fail(FetchError.TOO_LARGE)

        val bytes = if (headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true) {
            readChunked(input)
        } else {
            readLimited(input, declared)
        }
        return Response(status, null, String(bytes, charsetOf(contentType)))
    }

    private fun readLimited(input: InputStream, declared: Long?): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (declared == null || out.size() < declared) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) throw Fail(FetchError.TOO_LARGE)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input).substringBefore(';').trim().toIntOrNull(16) ?: throw Fail(FetchError.UNREACHABLE)
            if (size < 0) throw Fail(FetchError.UNREACHABLE)
            if (size == 0) break
            if (out.size().toLong() + size > MAX_BYTES) throw Fail(FetchError.TOO_LARGE)
            var left = size
            val buffer = ByteArray(8192)
            while (left > 0) {
                val n = input.read(buffer, 0, minOf(left, buffer.size))
                if (n < 0) throw Fail(FetchError.UNREACHABLE)
                out.write(buffer, 0, n)
                left -= n
            }
            readLine(input) // the CRLF after the chunk
        }
        return out.toByteArray()
    }

    /** One header or chunk line, without CRLF; a line over 8 KB is refused. */
    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) throw Fail(FetchError.UNREACHABLE)
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > MAX_LINE) throw Fail(FetchError.UNREACHABLE)
        }
        return sb.toString()
    }

    private fun charsetOf(contentType: String): Charset {
        val name = Regex("charset=\"?([A-Za-z0-9_.:-]+)", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)
        return try {
            if (name == null) Charsets.UTF_8 else Charset.forName(name)
        } catch (_: IllegalArgumentException) {
            Charsets.UTF_8
        }
    }

    private fun remainingMillis(deadline: Long): Int {
        val left = (deadline - System.nanoTime()) / 1_000_000
        if (left <= 0) throw Fail(FetchError.UNREACHABLE)
        return minOf(left, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Applies what is left of the total time as the socket read timeout before every read. */
    private inner class DeadlineStream(private val socket: Socket, private val deadline: Long) : InputStream() {
        private val delegate = socket.getInputStream()

        override fun read(): Int {
            socket.soTimeout = remainingMillis(deadline)
            return delegate.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            socket.soTimeout = remainingMillis(deadline)
            return delegate.read(b, off, len)
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 3
        const val MAX_BYTES = 1_000_000
        const val MAX_LINE = 8192
        const val MAX_HEADERS = 100
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
