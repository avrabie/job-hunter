package net.s4v3.backend.jobimport.internal

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A local TLS server with a self-signed certificate for example.com, jobs.example.com and other.example.com. The
 * fetcher trusts only that certificate. The test resolver maps names to 127.0.0.1 (the fake) or 10.0.0.1, and the
 * test address policy accepts loopback only, so the rules under test are the ones in [SafeFetcher].
 */
class SafeFetcherTests {

    private val loopback = InetAddress.getByName("127.0.0.1")
    private val internal = InetAddress.getByName("10.0.0.1")

    private lateinit var context: SSLContext
    private lateinit var server: SSLServerSocket
    private val requests = CopyOnWriteArrayList<String>()
    private var handler: (String, OutputStream) -> Unit = { _, out -> respond(out, 200, "text/html", "<p>ok</p>") }

    @BeforeEach
    fun start() {
        val store = KeyStore.getInstance("PKCS12")
        javaClass.getResourceAsStream("/jobimport/fake-tls.p12")!!.use { store.load(it, "changeit".toCharArray()) }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(store, "changeit".toCharArray()) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, trust.trustManagers, null) }
        server = context.serverSocketFactory.createServerSocket(0, 10, loopback) as SSLServerSocket
        Thread { acceptLoop(server) }.apply { isDaemon = true }.start()
    }

    @AfterEach
    fun stop() = server.close()

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val socket = try {
                s.accept() as SSLSocket
            } catch (_: Exception) {
                return
            }
            Thread {
                try {
                    socket.use {
                        val head = StringBuilder()
                        val input = it.inputStream
                        while (!head.endsWith("\r\n\r\n")) {
                            val c = input.read()
                            if (c < 0) return@use
                            head.append(c.toChar())
                        }
                        requests += head.toString()
                        handler(head.toString(), it.outputStream)
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun respond(out: OutputStream, status: Int, type: String?, body: String, extra: String = "") {
        val head = "HTTP/1.1 $status X\r\n" + (type?.let { "Content-Type: $it\r\n" } ?: "") +
            "Content-Length: ${body.toByteArray().size}\r\n$extra" + "Connection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body.toByteArray())
        out.flush()
    }

    private fun fetcher(
        resolver: (String) -> List<InetAddress> = { host -> listOf(if (host.startsWith("internal.")) internal else loopback) },
        timeout: Duration = Duration.ofSeconds(5),
    ) = SafeFetcher(
        userAgent = "JobHunter-Test/1.0 (+https://example.com/about)",
        resolver = resolver,
        isPublic = { it.isLoopbackAddress },
        sslContext = context,
        port = server.localPort,
        totalTimeout = timeout,
    )

    private fun failed(result: FetchResult): FetchError = (result as FetchResult.Failed).error

    @Test
    fun `fetches a page with SNI-named host, identifying user agent and identity encoding`() {
        val page = (fetcher().fetch("https://jobs.example.com/ada-engineer?id=7#x") as FetchResult.Page).html
        assertEquals("<p>ok</p>", page)
        val request = requests.single()
        assertTrue(request.startsWith("GET /ada-engineer?id=7 HTTP/1.1\r\n"))
        assertTrue("Host: jobs.example.com\r\n" in request)
        assertTrue("User-Agent: JobHunter-Test/1.0" in request)
        assertTrue("Accept-Encoding: identity\r\n" in request)
    }

    @Test
    fun `reads a chunked body`() {
        handler = { _, out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nTransfer-Encoding: chunked\r\n\r\n5\r\n<p>ab\r\n7\r\ncd</p>\n\r\n0\r\n\r\n".toByteArray())
            out.flush()
        }
        assertEquals("<p>abcd</p>\n", (fetcher().fetch("https://example.com/a") as FetchResult.Page).html)
    }

    @Test
    fun `host resolving to an internal address is blocked without a connection`() {
        assertEquals(FetchError.BLOCKED_ADDRESS, failed(fetcher().fetch("https://internal.example.com/a")))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `one internal address among public ones blocks the host`() {
        val result = fetcher(resolver = { listOf(loopback, internal) }).fetch("https://example.com/a")
        assertEquals(FetchError.BLOCKED_ADDRESS, failed(result))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `rebinding cannot redirect the connection to the second answer`() {
        val lookups = AtomicInteger()
        // First answer is public (loopback stands in for it), every later one is private.
        val resolver = { _: String -> listOf(if (lookups.getAndIncrement() == 0) loopback else internal) }
        val result = fetcher(resolver = resolver).fetch("https://example.com/a")
        assertEquals("<p>ok</p>", (result as FetchResult.Page).html)
        assertEquals(1, lookups.get(), "the host is resolved once and the validated address is used")
        assertEquals(1, requests.size)
    }

    @Test
    fun `redirect to an internal host is blocked`() {
        handler = { _, out -> respond(out, 302, null, "", "Location: https://internal.example.com/secret\r\n") }
        assertEquals(FetchError.BLOCKED_ADDRESS, failed(fetcher().fetch("https://example.com/a")))
        assertEquals(1, requests.size)
    }

    @Test
    fun `redirects are followed, including relative ones, up to three`() {
        handler = { head, out ->
            when {
                head.startsWith("GET /a ") -> respond(out, 301, null, "", "Location: /b\r\n")
                head.startsWith("GET /b ") -> respond(out, 302, null, "", "Location: https://other.example.com/c\r\n")
                else -> respond(out, 200, "text/html", "<p>final</p>")
            }
        }
        assertEquals("<p>final</p>", (fetcher().fetch("https://example.com/a") as FetchResult.Page).html)
    }

    @Test
    fun `more than three redirects fail`() {
        handler = { _, out -> respond(out, 302, null, "", "Location: /again\r\n") }
        assertEquals(FetchError.UNREACHABLE, failed(fetcher().fetch("https://example.com/a")))
        assertEquals(4, requests.size)
    }

    @Test
    fun `redirect to http or a denied site is refused`() {
        handler = { _, out -> respond(out, 302, null, "", "Location: http://example.com/a\r\n") }
        assertEquals(FetchError.INVALID_URL, failed(fetcher().fetch("https://example.com/a")))
        handler = { _, out -> respond(out, 302, null, "", "Location: https://www.linkedin.com/jobs/1\r\n") }
        assertEquals(FetchError.UNSUPPORTED_SITE, failed(fetcher().fetch("https://example.com/a")))
    }

    @Test
    fun `bodies over 1 MB are too large, declared or not`() {
        handler = { _, out -> respond(out, 200, "text/html", "x".repeat(1_000_001)) }
        assertEquals(FetchError.TOO_LARGE, failed(fetcher().fetch("https://example.com/a")))
        handler = { _, out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n".toByteArray())
            repeat(1100) { out.write(ByteArray(1000) { 'x'.code.toByte() }) }
            out.flush()
        }
        assertEquals(FetchError.TOO_LARGE, failed(fetcher().fetch("https://example.com/a")))
    }

    @Test
    fun `a body just under the limit is accepted`() {
        handler = { _, out -> respond(out, 200, "text/html", "x".repeat(999_999)) }
        assertEquals(999_999, (fetcher().fetch("https://example.com/a") as FetchResult.Page).html.length)
    }

    @Test
    fun `other content types are not html`() {
        handler = { _, out -> respond(out, 200, "application/pdf", "%PDF") }
        assertEquals(FetchError.NOT_HTML, failed(fetcher().fetch("https://example.com/a")))
        handler = { _, out -> respond(out, 200, null, "<p>x</p>") }
        assertEquals(FetchError.NOT_HTML, failed(fetcher().fetch("https://example.com/a")))
        handler = { _, out -> respond(out, 200, "application/xhtml+xml; charset=UTF-8", "<p>x</p>") }
        assertTrue(fetcher().fetch("https://example.com/a") is FetchResult.Page)
    }

    @Test
    fun `a server that stalls hits the total timeout`() {
        handler = { _, out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n".toByteArray())
            out.flush()
            Thread.sleep(3000)
        }
        val started = System.nanoTime()
        val result = fetcher(timeout = Duration.ofMillis(500)).fetch("https://example.com/a")
        assertEquals(FetchError.UNREACHABLE, failed(result))
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(2))
    }

    @Test
    fun `a slow trickle cannot outlast the total timeout`() {
        handler = { _, out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n".toByteArray())
            repeat(50) {
                out.write('x'.code)
                out.flush()
                Thread.sleep(100)
            }
        }
        assertEquals(FetchError.UNREACHABLE, failed(fetcher(timeout = Duration.ofMillis(600)).fetch("https://example.com/a")))
    }

    @Test
    fun `server errors, unknown certificates and unresolvable hosts are unreachable`() {
        handler = { _, out -> respond(out, 500, "text/html", "<p>no</p>") }
        assertEquals(FetchError.UNREACHABLE, failed(fetcher().fetch("https://example.com/a")))
        handler = { _, out -> respond(out, 404, "text/html", "<p>no</p>") }
        assertEquals(FetchError.UNREACHABLE, failed(fetcher().fetch("https://example.com/a")))
        val untrusting = SafeFetcher("t", { listOf(loopback) }, { true }, SSLContext.getDefault(), server.localPort)
        assertEquals(FetchError.UNREACHABLE, failed(untrusting.fetch("https://example.com/a")))
        val unknown = fetcher(resolver = { throw java.net.UnknownHostException() })
        assertEquals(FetchError.UNREACHABLE, failed(unknown.fetch("https://example.com/a")))
    }

    @Test
    fun `a certificate for another name is rejected`() {
        assertEquals(FetchError.UNREACHABLE, failed(fetcher().fetch("https://wrong.example.org/a")))
    }

    @Test
    fun `rule violations never reach the network`() {
        assertEquals(FetchError.INVALID_URL, failed(fetcher().fetch("https://203.0.113.5/a")))
        assertEquals(FetchError.UNSUPPORTED_SITE, failed(fetcher().fetch("https://linkedin.com/jobs/1")))
        assertTrue(requests.isEmpty())
    }
}
