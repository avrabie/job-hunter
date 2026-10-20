package net.s4v3.gateway

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A short response timeout makes the 504 case quick; everything else uses the shipped settings. */
@TestPropertySource(properties = ["spring.cloud.gateway.server.webflux.httpclient.response-timeout=1s"])
class DownstreamFailureTests : GatewayTest() {

    @Autowired
    lateinit var client: WebTestClient

    @BeforeEach
    fun reset() = Stubs.reset()

    @AfterEach
    fun restore() {
        runCatching { Stubs.backend.restart() }
        runCatching { Stubs.auth.restart() }
    }

    @Test
    fun `a stopped service is a 502 that does not reveal its address, and the gateway recovers`() {
        val address = Stubs.backend.uri
        Stubs.backend.stop()

        val down = client.get().uri("/api/x").exchange().expectStatus().isEqualTo(502).returnResult(String::class.java)
        assertTrue(address !in down.responseBodyContent!!.decodeToString() && "localhost" !in down.responseBodyContent!!.decodeToString())

        Stubs.backend.restart()
        val statuses = (1..3).map { client.get().uri("/api/x").exchange().returnResult(String::class.java).status.value() }
        assertEquals(200, statuses.last(), "the gateway serves again without a restart: $statuses")
        assertTrue(statuses.count { it != 200 } <= 1, "at most one failed request after the restart: $statuses")
    }

    @Test
    fun `a service that does not answer in time is a 504`() {
        Stubs.backend.handler = { Reply(body = "late", delayMillis = 4000) }

        val response = client.get().uri("/api/slow").exchange().expectStatus().isEqualTo(504).returnResult(String::class.java)

        assertTrue("localhost" !in response.responseBodyContent!!.decodeToString())
    }

    @Test
    fun `the other service keeps working while one is down`() {
        Stubs.backend.stop()

        client.get().uri("/auth/login").exchange().expectStatus().isOk
    }
}
