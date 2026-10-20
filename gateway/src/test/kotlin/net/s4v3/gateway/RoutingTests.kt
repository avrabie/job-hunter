package net.s4v3.gateway

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingTests : GatewayTest() {

    @Autowired
    lateinit var client: WebTestClient

    @BeforeEach
    fun reset() = Stubs.reset()

    @Test
    fun `api requests reach backend with the path unchanged`() {
        client.get().uri("/api/ping").exchange().expectStatus().isOk

        val call = Stubs.backend.received.single()
        assertEquals("GET", call.method)
        assertEquals("/api/ping", call.uri)
        assertEquals(0, Stubs.auth.received.size)
    }

    @Test
    fun `auth requests reach auth with the path unchanged`() {
        client.get().uri("/auth/.well-known/openid-configuration").exchange().expectStatus().isOk

        assertEquals("/auth/.well-known/openid-configuration", Stubs.auth.received.single().uri)
        assertEquals(0, Stubs.backend.received.size)
    }

    @Test
    fun `method, query string and form body are preserved`() {
        client.post().uri("/auth/oauth2/token?x=1&y={v}", "a b")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", "authorization_code").with("code", "c-1"))
            .exchange().expectStatus().isOk

        val call = Stubs.auth.received.single()
        assertEquals("POST", call.method)
        assertEquals("/auth/oauth2/token", call.path)
        assertEquals("x=1&y=a%20b", call.query)
        assertEquals("grant_type=authorization_code&code=c-1", call.body)
        assertTrue(call.header("content-type")!!.startsWith("application/x-www-form-urlencoded"))
    }

    @Test
    fun `paths outside api and auth are 404 and reach nobody`() {
        for (path in listOf("/internal/secrets", "/apix/foo", "/authority", "/", "/actuator/env")) {
            client.get().uri(path).exchange().expectStatus().isNotFound
        }

        assertEquals(0, Stubs.backend.received.size)
        assertEquals(0, Stubs.auth.received.size)
    }

    @Test
    fun `an api path that looks like an actuator path is an ordinary api request for backend`() {
        client.get().uri("/api/actuator/health").exchange().expectStatus().isOk

        assertEquals("/api/actuator/health", Stubs.backend.received.single().uri)
    }
}
