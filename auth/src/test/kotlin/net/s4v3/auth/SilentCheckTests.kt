package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@WebIntegrationTest
class SilentCheckTests(
    @LocalServerPort port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)
    private val email = "silent-${java.util.UUID.randomUUID()}@example.com"

    init {
        users.createUser(encoder, email = email, password = "correct-horse")
    }

    private fun silent(
        redirectUri: String = AuthFlow.REDIRECT,
        state: String = "state-1",
        prompt: String? = "none",
        client: AuthFlow = flow,
    ): Resp {
        val url = client.authorizeUrl(client.pkce(), redirectUri = redirectUri, state = state)
        return client.get(if (prompt == null) url else "$url&prompt=$prompt")
    }

    private fun assertNotRedirectedTo(response: Resp, host: String) {
        assertTrue(response.location?.contains(host) != true, "$response")
    }

    @Test
    fun `a valid session gets a code and the same state`() {
        flow.authorizeAndLogin(email, "correct-horse", flow.pkce())

        val response = silent(state = "abc")

        assertEquals(302, response.status)
        val query = AuthFlow.queryOf(response.location!!)
        assertTrue(response.location!!.startsWith("${AuthFlow.REDIRECT}?"), response.location)
        assertTrue(query["code"] != null, response.location)
        assertEquals("abc", query["state"])
    }

    @Test
    fun `no session gets login_required and the same state, not the login form`() {
        val response = silent(state = "abc")

        assertEquals(302, response.status)
        assertTrue(response.location!!.startsWith("${AuthFlow.REDIRECT}?"), response.location)
        assertEquals(mapOf("error" to "login_required", "state" to "abc"), AuthFlow.queryOf(response.location!!))
        assertTrue("/login" !in response.location!!)
    }

    @Test
    fun `state with special characters is echoed back unchanged`() {
        val state = "a b&c=d+e/é%?#"

        val response = silent(state = state)

        assertEquals(state, AuthFlow.queryOf(response.location!!)["state"])
        assertEquals(setOf("error", "state"), AuthFlow.queryOf(response.location!!).keys)
    }

    @Test
    fun `no prompt and prompt=login still go to the login form`() {
        for (prompt in listOf(null, "login")) {
            val response = silent(prompt = prompt)

            assertEquals(302, response.status)
            assertTrue(response.location!!.contains("/login"), "$prompt: $response")
            assertNull(AuthFlow.queryOf(response.location!!)["error"])
        }
    }

    @Test
    fun `an unregistered redirect uri is not redirected to`() {
        val response = silent(redirectUri = "http://evil.example/callback")

        assertNotRedirectedTo(response, "evil.example")
    }

    @Test
    fun `variants of a registered redirect uri are not redirected to`() {
        for (variant in listOf(
            AuthFlow.REDIRECT.uppercase(),
            AuthFlow.REDIRECT + "/",
            AuthFlow.REDIRECT + "?x=1",
            AuthFlow.REDIRECT.replace("callback", "%63allback"),
            "http://localhost:5173@evil.example/callback",
        )) {
            val response = silent(redirectUri = variant)

            assertTrue(response.location?.startsWith(variant) != true || response.status != 302, "$variant: $response")
            assertNotRedirectedTo(response, "evil.example")
            assertNull(response.location?.let { AuthFlow.queryOf(it)["error"] }?.takeIf { it == "login_required" }, "$variant: $response")
        }
    }

    @Test
    fun `an unknown client is not redirected to`() {
        val unknown = AuthFlow(flow.base.substringAfter("localhost:").substringBefore("/").toInt(), clientId = "nope")

        val response = silent(client = unknown)

        assertNotRedirectedTo(response, "localhost:5173")
    }

    @Test
    fun `a missing redirect uri is not redirected to`() {
        val url = flow.authorizeUrl(flow.pkce()).replace(Regex("redirect_uri=[^&]*&"), "") + "&prompt=none"

        val response = flow.get(url)

        assertNotRedirectedTo(response, "localhost:5173")
    }
}
