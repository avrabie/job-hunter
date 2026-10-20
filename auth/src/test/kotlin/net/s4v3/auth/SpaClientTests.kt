package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@WebIntegrationTest
class SpaClientTests(
    @LocalServerPort port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)
    private val email = "spa-${java.util.UUID.randomUUID()}@example.com"

    init {
        users.createUser(encoder, email = email, password = "correct-horse")
    }

    @Test
    fun `authorization code with PKCE returns access, id and refresh tokens`() {
        val tokens = flow.tokens(email, "correct-horse")

        assertNotNull(tokens["access_token"])
        assertNotNull(tokens["id_token"])
        assertNotNull(tokens["refresh_token"])
        assertTrue(tokens["expires_in"].asInt() in 895..900, "access token lives 15 minutes: ${tokens["expires_in"]}")
    }

    @Test
    fun `a sign-in is granted without a consent page`() {
        val pkce = flow.pkce()

        val redirect = flow.authorizeAndLogin(email, "correct-horse", pkce)

        assertEquals(302, redirect.status)
        assertTrue(redirect.location!!.startsWith("${AuthFlow.REDIRECT}?code="), redirect.location)
    }

    @Test
    fun `an authorization request without a code challenge is rejected`() {
        val loginRedirect = flow.get(flow.authorizeUrl(pkce = null))
        flow.login(email, "correct-horse")

        val response = flow.get(flow.authorizeUrl(pkce = null))

        val location = response.location.orEmpty()
        assertNull(AuthFlow.queryOf(location.ifEmpty { "x://x" })["code"], "no code may be issued: $response")
        assertTrue(response.status == 400 || "error=invalid_request" in location, "$loginRedirect / $response")
    }

    @Test
    fun `a wrong code verifier fails the token request`() {
        val pkce = flow.pkce()
        val code = flow.codeFrom(flow.authorizeAndLogin(email, "correct-horse", pkce))

        val response = flow.exchange(code, verifier = "x".repeat(43))

        assertEquals(400, response.status)
        assertEquals("invalid_grant", response.json()["error"].asString())
    }

    @Test
    fun `an unregistered redirect is rejected and never redirected to`() {
        val pkce = flow.pkce()
        flow.get(flow.authorizeUrl(pkce))
        flow.login(email, "correct-horse")

        val response = flow.get(flow.authorizeUrl(pkce, redirectUri = "http://evil.example/callback"))

        assertEquals(400, response.status)
        assertTrue(response.location?.contains("evil.example") != true, "$response")
    }

    @Test
    fun `client credentials and password grants are refused`() {
        for (form in listOf(
            mapOf("grant_type" to "client_credentials", "client_id" to "spa"),
            mapOf("grant_type" to "password", "client_id" to "spa", "username" to email, "password" to "correct-horse"),
        )) {
            val response = flow.postForm("${flow.base}/oauth2/token", form, "Accept" to "application/json")

            assertTrue(response.status in 400..401, "${form["grant_type"]}: $response")
            assertTrue("access_token" !in response.body, response.body)
        }
    }

    @Test
    fun `a refresh token yields a new access token`() {
        val tokens = flow.tokens(email, "correct-horse")

        val response = flow.postForm(
            "${flow.base}/oauth2/token",
            mapOf("grant_type" to "refresh_token", "refresh_token" to tokens["refresh_token"].asString(), "client_id" to "spa"),
        )

        assertEquals(200, response.status, response.body)
        assertNotNull(response.json()["access_token"])
    }

    private fun refresh(refreshToken: String) = flow.postForm(
        "${flow.base}/oauth2/token",
        mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to "spa"),
        "Accept" to "application/json",
    )

    @Test
    fun `a used refresh token is replaced and cannot be used again`() {
        val first = flow.tokens(email, "correct-horse")["refresh_token"].asString()

        val second = refresh(first)
        assertEquals(200, second.status, second.body)
        val rotated = second.json()["refresh_token"].asString()

        assertTrue(rotated != first, "public clients get a new refresh token on every use")
        assertEquals(400, refresh(first).status, "the old refresh token is dead")
        assertEquals(200, refresh(rotated).status)
    }

    @Test
    fun `an unknown refresh token is rejected`() {
        val response = refresh("not-a-real-token")

        assertEquals(400, response.status)
        assertEquals("invalid_grant", response.json()["error"].asString())
    }
}
