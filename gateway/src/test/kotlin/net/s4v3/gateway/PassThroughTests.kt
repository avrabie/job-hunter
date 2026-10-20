package net.s4v3.gateway

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassThroughTests : GatewayTest() {

    @Autowired
    lateinit var client: WebTestClient

    @BeforeEach
    fun reset() = Stubs.reset()

    @Test
    fun `authorization, cookies and bodies arrive unchanged`() {
        val body = """{"title":"Kotlin Engineer","note":"ünïcode ✓"}"""

        client.post().uri("/api/postings")
            .header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi")
            .header(HttpHeaders.COOKIE, "session=s1; theme=dark")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange().expectStatus().isOk

        val call = Stubs.backend.received.single()
        assertEquals("Bearer abc.def.ghi", call.header("authorization"))
        assertEquals("session=s1; theme=dark", call.header("cookie"))
        assertEquals(body, call.body)
    }

    @Test
    fun `a request without a token is not rejected by the gateway and the 401 of the service reaches the client`() {
        Stubs.backend.handler = { Reply(401, mapOf("WWW-Authenticate" to "Bearer error=\"invalid_token\""), """{"error":"unauthorized"}""") }

        client.get().uri("/api/postings").exchange()
            .expectStatus().isUnauthorized
            .expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\"")
            .expectBody().json("""{"error":"unauthorized"}""")

        assertNull(Stubs.backend.received.single().header("authorization"))
    }

    @Test
    fun `the gateway adds no header that identifies a user or a role`() {
        client.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc").exchange().expectStatus().isOk

        val names = Stubs.backend.received.single().headers.keys
        val identity = Regex("user|role|identity|principal|subject|^x-sub|email|name$")
        assertTrue(names.none { identity.containsMatchIn(it) && it != "user-agent" }, names.toString())
    }

    @Test
    fun `status, body, set-cookie and location of a service are relayed unchanged`() {
        Stubs.auth.handler = {
            Reply(
                302,
                mapOf("Location" to "https://s4v3.net/auth/login", "Set-Cookie" to "JSESSIONID=abc; Path=/auth; HttpOnly"),
                "redirecting",
            )
        }

        client.get().uri("/auth/oauth2/authorize?client_id=spa").exchange()
            .expectStatus().isFound
            .expectHeader().valueEquals("Location", "https://s4v3.net/auth/login")
            .expectHeader().valueEquals("Set-Cookie", "JSESSIONID=abc; Path=/auth; HttpOnly")
            .expectBody(String::class.java).isEqualTo("redirecting")
    }

    @Test
    fun `forwarded host and scheme from the edge arrive as the first values`() {
        client.get().uri("/auth/login")
            .header("X-Forwarded-Host", "s4v3.net")
            .header("X-Forwarded-Proto", "https")
            .exchange().expectStatus().isOk

        val call = Stubs.auth.received.single()
        assertEquals("s4v3.net", call.header("x-forwarded-host")!!.substringBefore(","), call.headers.toString())
        assertEquals("https", call.header("x-forwarded-proto")!!.substringBefore(","), call.headers.toString())
    }

    @Test
    fun `a direct request gets the gateway's own view of host and scheme`() {
        client.get().uri("/auth/login").exchange().expectStatus().isOk

        val call = Stubs.auth.received.single()
        assertEquals("http", call.header("x-forwarded-proto"), call.headers.toString())
        assertTrue(call.header("x-forwarded-host")!!.startsWith("localhost:"), call.headers.toString())
    }
}
