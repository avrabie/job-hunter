package net.s4v3.backend

import net.s4v3.backend.sources.internal.Fetched
import net.s4v3.backend.sources.internal.PoliteHttp
import net.s4v3.backend.sources.internal.SourcesSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PoliteHttpTests {

    private val server = FakeHttpServer()

    @AfterEach
    fun stop() = server.close()

    private fun serve(path: String, status: Int = 200, type: String = "application/json", body: String = "{}", etag: String? = null, delayMs: Long = 0) {
        server.on(path) {
            if (delayMs > 0) Thread.sleep(delayMs)
            FakeHttpServer.Reply(status, body, type, headers = etag?.let { mapOf("ETag" to it) } ?: emptyMap())
        }
    }

    private fun http(pause: Duration = Duration.ZERO, read: Duration = Duration.ofSeconds(2)) =
        PoliteHttp(SourcesSettings(pause = pause, readTimeout = read))

    private fun uri(path: String) = URI("${server.baseUrl}$path")

    @Test
    fun `a 200 returns the body and its etag`() {
        serve("/ok", body = """{"a":1}""", etag = "W/\"v1\"")

        val r = assertIs<Fetched.Body>(http().get(uri("/ok"), 1000))
        assertEquals("""{"a":1}""", r.text)
        assertEquals("W/\"v1\"", r.etag)
    }

    @Test
    fun `the request carries the user agent and the stored validator`() {
        serve("/h")

        http().get(uri("/h"), 1000, etag = "W/\"v1\"")

        assertEquals(SourcesSettings().userAgent, server.requests.single().header("User-Agent"))
        assertEquals("W/\"v1\"", server.requests.single().header("If-None-Match"))
        assertTrue("s4v3.net" in SourcesSettings().userAgent)
    }

    @Test
    fun `a 304 is not modified`() {
        serve("/nm", status = 304)

        assertIs<Fetched.NotModified>(http().get(uri("/nm"), 1000))
    }

    @Test
    fun `error statuses fail and carry the status but no body`() {
        serve("/missing", status = 404, body = "SECRET-BODY")
        serve("/boom", status = 500, body = "SECRET-BODY")

        val missing = assertIs<Fetched.Failed>(http().get(uri("/missing"), 1000))
        val boom = assertIs<Fetched.Failed>(http().get(uri("/boom"), 1000))
        assertEquals(404, missing.status)
        assertEquals(500, boom.status)
        assertTrue("SECRET" !in missing.reason + boom.reason)
    }

    @Test
    fun `a slow server times out`() {
        serve("/slow", delayMs = 1500)

        val r = assertIs<Fetched.Failed>(http(read = Duration.ofMillis(300)).get(uri("/slow"), 1000))
        assertEquals("timeout", r.reason)
    }

    @Test
    fun `an oversized body fails`() {
        serve("/big", body = "x".repeat(2000))

        assertEquals("body too large", assertIs<Fetched.Failed>(http().get(uri("/big"), 1000)).reason)
    }

    @Test
    fun `an html body fails when json is required`() {
        serve("/html", type = "text/html", body = "<html>SECRET</html>")

        val r = assertIs<Fetched.Failed>(http().get(uri("/html"), 1000))
        assertEquals("not JSON", r.reason)
        assertIs<Fetched.Body>(http().get(uri("/html"), 1000, requireJson = false))
    }

    @Test
    fun `consecutive requests to one host are separated by the pause`() {
        serve("/p")
        val client = http(pause = Duration.ofMillis(400))

        client.get(uri("/p"), 1000)
        client.get(uri("/p"), 1000)

        val gapMs = (server.requests[1].startedAt - server.requests[0].finishedAt) / 1_000_000
        assertTrue(gapMs >= 350, "gap was $gapMs ms")
    }
}
