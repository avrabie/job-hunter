package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebIntegrationTest
class LogoutTests(
    @LocalServerPort port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)

    @Test
    fun `end-session with an id token hint redirects to the registered URI and ends the session`() {
        val user = users.createUser(encoder)
        val idToken = flow.tokens(user.email, "correct-horse")["id_token"].asString()
        // the session is signed in: a new authorization request goes straight back to the client
        val signedIn = flow.get(flow.authorizeUrl(flow.pkce()))
        assertTrue(signedIn.location!!.startsWith("${AuthFlow.REDIRECT}?code="), signedIn.toString())

        val logout = flow.get(
            "${flow.base}/connect/logout?id_token_hint=${AuthFlow.enc(idToken)}" +
                "&post_logout_redirect_uri=${AuthFlow.enc("http://localhost:5173/")}&state=bye",
        )

        assertEquals(302, logout.status, logout.toString())
        assertTrue(logout.location!!.startsWith("http://localhost:5173/"), logout.location)
        val after = flow.get(flow.authorizeUrl(flow.pkce()))
        assertTrue(after.location!!.contains("/login"), "session ended, the login page is required: $after")
    }

    @Test
    fun `an unregistered post-logout URI is not redirected to`() {
        val user = users.createUser(encoder)
        val idToken = flow.tokens(user.email, "correct-horse")["id_token"].asString()

        val logout = flow.get(
            "${flow.base}/connect/logout?id_token_hint=${AuthFlow.enc(idToken)}" +
                "&post_logout_redirect_uri=${AuthFlow.enc("http://evil.example/")}",
        )

        assertTrue(logout.location?.contains("evil.example") != true, logout.toString())
    }

    private fun refresh(refreshToken: String) = flow.postForm(
        "${flow.base}/oauth2/token",
        mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to "spa"),
    )

    private fun logout(query: String) = flow.get("${flow.base}/connect/logout?$query")

    private val post = "post_logout_redirect_uri=${AuthFlow.enc("http://localhost:5173/")}"

    @Test
    fun `an id token replaced by a refresh still ends the session and redirects`() {
        val user = users.createUser(encoder)
        val first = flow.tokens(user.email, "correct-horse")
        val staleIdToken = first["id_token"].asString()
        assertEquals(200, refresh(first["refresh_token"].asString()).status)

        val response = logout("id_token_hint=${AuthFlow.enc(staleIdToken)}&$post&state=bye")

        assertEquals(302, response.status, response.toString())
        assertEquals("http://localhost:5173/?state=bye", response.location)
        val after = flow.get(flow.authorizeUrl(flow.pkce()))
        assertTrue(after.location!!.contains("/login"), "session ended: $after")
    }

    @Test
    fun `no id token hint but a registered URI and the client id ends the session and redirects`() {
        val user = users.createUser(encoder)
        flow.tokens(user.email, "correct-horse")

        val response = logout("client_id=spa&$post")

        assertEquals(302, response.status, response.toString())
        assertEquals("http://localhost:5173/", response.location)
        assertTrue(flow.get(flow.authorizeUrl(flow.pkce())).location!!.contains("/login"))
    }

    @Test
    fun `a rejected request never redirects to an unregistered URI`() {
        val user = users.createUser(encoder)
        val first = flow.tokens(user.email, "correct-horse")
        refresh(first["refresh_token"].asString())

        val stale = logout("id_token_hint=${AuthFlow.enc(first["id_token"].asString())}&post_logout_redirect_uri=${AuthFlow.enc("http://evil.example/")}")
        val none = logout("client_id=spa&post_logout_redirect_uri=${AuthFlow.enc("http://evil.example/")}")
        val bare = logout("")

        for (r in listOf(stale, none, bare)) {
            assertEquals(400, r.status, r.toString())
            assertTrue(r.location?.contains("evil.example") != true)
        }
    }
}
