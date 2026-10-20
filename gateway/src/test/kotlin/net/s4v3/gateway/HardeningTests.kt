package net.s4v3.gateway

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardeningTests : GatewayTest() {

    @Autowired
    lateinit var client: WebTestClient

    @BeforeEach
    fun reset() = Stubs.reset()

    @Test
    fun `proxied responses carry the security headers and no content security policy`() {
        client.get().uri("/auth/login").exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
            .expectHeader().exists("X-Frame-Options")
            .expectHeader().exists("Referrer-Policy")
            .expectHeader().exists("Strict-Transport-Security")
            .expectHeader().doesNotExist("Content-Security-Policy")
            .expectHeader().doesNotExist("Content-Security-Policy-Report-Only")
    }

    @Test
    fun `a header the service already sets reaches the client once`() {
        Stubs.backend.handler = { Reply(headers = mapOf("X-Frame-Options" to "SAMEORIGIN", "X-Content-Type-Options" to "nosniff")) }

        val response = client.get().uri("/api/x").exchange().expectStatus().isOk.returnResult(String::class.java)

        for (name in listOf("X-Frame-Options", "X-Content-Type-Options")) {
            assertEquals(1, response.responseHeaders[name]!!.size, "$name: ${response.responseHeaders[name]}")
        }
    }

    @Test
    fun `the gateway sets no cookie of its own`() {
        val plain = client.get().uri("/api/x").exchange().expectStatus().isOk.returnResult(String::class.java)
        assertTrue(plain.responseHeaders[HttpHeaders.SET_COOKIE].isNullOrEmpty())

        Stubs.auth.handler = { Reply(headers = mapOf("Set-Cookie" to "JSESSIONID=s; Path=/auth")) }
        val withCookie = client.get().uri("/auth/login").exchange().returnResult(String::class.java)
        assertEquals(listOf("JSESSIONID=s; Path=/auth"), withCookie.responseHeaders[HttpHeaders.SET_COOKIE])
    }

    @Test
    fun `a request with a token, a cookie, a body and a query leaves none of them in the log`() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start(); root.addAppender(it) }
        try {
            client.post().uri("/api/postings?apikey=query-secret-789")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-secret-123")
                .header(HttpHeaders.COOKIE, "sid=cookie-secret-456")
                .bodyValue("""{"cv":"body-secret-000"}""")
                .exchange().expectStatus().isOk
            Stubs.backend.handler = { Reply(404, body = "not found") }
            client.get().uri("/api/missing?apikey=query-secret-789")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-secret-123")
                .exchange().expectStatus().isNotFound
        } finally {
            root.detachAppender(appender)
            appender.stop()
        }

        val log = appender.list.joinToString("\n") { "${it.loggerName}: ${it.formattedMessage}" }
        for (secret in listOf("token-secret-123", "cookie-secret-456", "query-secret-789", "body-secret-000")) {
            assertTrue(secret !in log, "$secret was logged")
        }
    }
}
