package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

/** Which sign-ins get ADMIN from `app.admin-emails`. The list is configured with mixed case and spaces on purpose. */
@WebIntegrationTest
@Import(FakeGoogleConfig::class)
@TestPropertySource(properties = ["app.admin-emails=Owner@Example.com, other-admin@example.com, local-owner@example.com"])
class AdminEmailsTests(
    @LocalServerPort private val port: Int,
    @Autowired private val google: FakeGoogle,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun googleRoles(identity: FakeGoogle.Identity): List<String> {
        google.identity = identity
        val flow = AuthFlow(port)
        val pkce = flow.pkce()
        val redirect = flow.authorizeWithGoogle(pkce)
        val tokens = flow.exchange(flow.codeFrom(redirect), pkce.verifier).json()
        return AuthFlow.claims(tokens["access_token"].asString()).getStringListClaim("roles")
    }

    @Test
    fun `a listed Google user gets USER and ADMIN, matched ignoring case`() {
        assertEquals(listOf("ADMIN", "USER"), googleRoles(FakeGoogle.Identity(sub = "g-owner", email = "owner@example.com")))
        assertEquals(listOf("ADMIN", "USER"), googleRoles(FakeGoogle.Identity(sub = "g-other-admin", email = "other-admin@example.com")))
    }

    @Test
    fun `an unlisted Google user gets USER only`() {
        assertEquals(listOf("USER"), googleRoles(FakeGoogle.Identity()))
    }

    @Test
    fun `a local-password account with a listed email does not get ADMIN`() {
        val user = users.createUser(encoder, email = "local-owner@example.com")

        val tokens = AuthFlow(port).tokens(user.email, "correct-horse")

        assertEquals(listOf("USER"), AuthFlow.claims(tokens["access_token"].asString()).getStringListClaim("roles"))
    }

    @Test
    fun `the grant is not stored, so dropping an email from the list takes effect on the next token`() {
        val identity = FakeGoogle.Identity(sub = "g-owner", email = "owner@example.com")
        assertEquals(listOf("ADMIN", "USER"), googleRoles(identity))

        val stored = jdbc.queryForList(
            "select r.role from user_role r join users u on u.id = r.user_id where lower(u.email) = ?", String::class.java, identity.email,
        )
        assertEquals(listOf("USER"), stored)
    }
}
