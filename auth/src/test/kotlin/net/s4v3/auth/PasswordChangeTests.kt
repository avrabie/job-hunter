package net.s4v3.auth

import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebIntegrationTest
class PasswordChangeTests(
    @LocalServerPort private val port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
    @Autowired private val mail: RecordingMailSender,
    @Autowired private val jwkSource: JWKSource<SecurityContext>,
) {
    private val flow = AuthFlow(port)

    private fun accessToken(email: String, password: String) =
        AuthFlow(port).tokens(email, password)["access_token"].asString()

    private fun change(token: String, current: String, new: String, extra: String = "") =
        flow.putJson(
            "${flow.base}/api/me/password",
            """{"currentPassword":"$current","newPassword":"$new"$extra}""",
            "Authorization" to "Bearer $token",
        )

    private fun canSignIn(email: String, password: String): Boolean {
        val f = AuthFlow(port)
        f.get(f.authorizeUrl(f.pkce()))
        return "error" !in f.login(email, password).location!!
    }

    @Test
    fun `the right current password and a different valid new one change the password`() {
        val user = users.createUser(encoder, password = "old-password-1")
        val token = accessToken(user.email, "old-password-1")

        val response = change(token, "old-password-1", "new-password-2")

        assertEquals(204, response.status, response.body)
        assertTrue(!canSignIn(user.email, "old-password-1"))
        assertTrue(canSignIn(user.email, "new-password-2"))
        assertEquals("Your password was changed", mail.to(user.email).single().subject)
    }

    @Test
    fun `a wrong current password is rejected and nothing changes`() {
        val user = users.createUser(encoder, password = "old-password-1")
        val token = accessToken(user.email, "old-password-1")

        val response = change(token, "not-my-password", "new-password-2")

        assertEquals(400, response.status)
        assertTrue(canSignIn(user.email, "old-password-1"))
        assertEquals(0, mail.to(user.email).size)
    }

    @Test
    fun `the same password is rejected`() {
        val user = users.createUser(encoder, password = "old-password-1")
        val token = accessToken(user.email, "old-password-1")

        assertEquals(400, change(token, "old-password-1", "old-password-1").status)
        assertTrue(canSignIn(user.email, "old-password-1"))
    }

    @Test
    fun `an invalid new password is rejected`() {
        val user = users.createUser(encoder, password = "old-password-1")
        val token = accessToken(user.email, "old-password-1")

        assertEquals(400, change(token, "old-password-1", "short").status)
    }

    @Test
    fun `a Google-only account has no password to change`() {
        val user = users.createUser(encoder, password = null, provider = Provider.GOOGLE, providerId = "g-${UUID.randomUUID()}")
        // No password means no form login, so sign a token for the account with the server's own key,
        // as a completed Google sign-in would have produced.
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().issuer("http://localhost/auth").subject(user.id.toString())
            .issuedAt(now).expiresAt(now.plusSeconds(900)).build()
        val token = NimbusJwtEncoder(jwkSource)
            .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).tokenValue

        val response = change(token, "anything-at-all", "new-password-2")

        assertEquals(400, response.status)
        assertEquals("no_password", response.json()["error"].asString())
    }

    @Test
    fun `user A cannot change user B's password`() {
        val a = users.createUser(encoder, password = "a-password-1")
        val b = users.createUser(encoder, password = "b-password-1")
        val tokenA = accessToken(a.email, "a-password-1")

        // A names B in every way the request could: extra fields are ignored and B's password is not A's.
        val response = change(
            tokenA, "b-password-1", "hijacked-pass-1",
            extra = ""","userId":"${b.id}","email":"${b.email}","sub":"${b.id}"""",
        )

        assertEquals(400, response.status, "A's current password is a-password-1, so B's is wrong for A")
        assertTrue(canSignIn(b.email, "b-password-1"))
        assertTrue(!canSignIn(b.email, "hijacked-pass-1"))
        assertTrue(canSignIn(a.email, "a-password-1"))
    }

    @Test
    fun `a request without a token is a 401`() {
        assertEquals(401, flow.putJson("${flow.base}/api/me/password", """{"currentPassword":"x","newPassword":"y"}""").status)
    }
}
