package net.s4v3.auth

import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@WebIntegrationTest
class PasswordResetTests(
    @LocalServerPort private val port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val encoder: PasswordEncoder,
    @Autowired private val mail: RecordingMailSender,
) {
    private val flow = AuthFlow(port)

    private fun requestReset(email: String) =
        flow.postJson("${flow.base}/api/password-reset/request", """{"email":"$email"}""")

    private fun confirm(token: String, password: String) =
        flow.postJson("${flow.base}/api/password-reset/confirm", """{"token":"$token","newPassword":"$password"}""")

    private fun canSignIn(email: String, password: String): Boolean {
        val f = AuthFlow(port)
        val authorize = f.get(f.authorizeUrl(f.pkce()))
        check(authorize.status == 302)
        return "error" !in f.login(email, password).location!!
    }

    @Test
    fun `a known email gets one reset mail with a link to the UI and only the hash is stored`() {
        val user = users.createUser(encoder)

        val response = requestReset(user.email)

        assertEquals(200, response.status)
        val mails = mail.to(user.email)
        assertEquals(1, mails.size)
        assertEquals("Reset your password", mails.single().subject)
        val token = mail.tokenOf(mails.single())
        assertTrue("http://ui.test/reset-password?token=$token" in mails.single().body)
        assertTrue(token.length >= 43, "at least 256 bits: $token")
        val hashes = jdbc.queryForList("select token_hash from password_reset_token where user_id = ?", String::class.java, user.id).filterNotNull()
        assertEquals(1, hashes.size)
        assertNotEquals(token, hashes.single())
        assertTrue(token !in hashes.single())
        assertEquals(64, hashes.single().length)
    }

    @Test
    fun `an unknown email gets the same response and no mail`() {
        val known = requestReset(users.createUser(encoder).email)
        val before = mail.sent.size

        val unknown = requestReset("nobody-${UUID.randomUUID()}@example.com")

        assertEquals(200, unknown.status)
        assertEquals(known.body, unknown.body)
        assertEquals(before, mail.sent.size)
    }

    @Test
    fun `a disabled account gets no mail`() {
        val user = users.createUser(encoder)
        users.save(user.copy(enabled = false))

        assertEquals(200, requestReset(user.email).status)
        assertEquals(0, mail.to(user.email).size)
    }

    @Test
    fun `a Google account can request a reset, which is how it adds a password`() {
        val user = users.createUser(encoder, password = null, provider = Provider.GOOGLE, providerId = "g-${UUID.randomUUID()}")

        assertEquals(200, requestReset(user.email).status)
        val token = mail.tokenOf(mail.to(user.email).single())
        assertEquals(204, confirm(token, "brand-new-password").status)

        assertTrue(canSignIn(user.email, "brand-new-password"))
    }

    @Test
    fun `a valid token sets the new password, kills the old one, and sends a notice`() {
        val user = users.createUser(encoder, password = "old-password-1")
        requestReset(user.email)
        val token = mail.tokenOf(mail.to(user.email).single())

        val response = confirm(token, "new-password-2")

        assertEquals(204, response.status, response.body)
        assertTrue(!canSignIn(user.email, "old-password-1"))
        assertTrue(canSignIn(user.email, "new-password-2"))
        assertEquals(listOf("Reset your password", "Your password was changed"), mail.to(user.email).map { it.subject })
    }

    @Test
    fun `a weak new password is a 400 and the token stays usable`() {
        val user = users.createUser(encoder)
        requestReset(user.email)
        val token = mail.tokenOf(mail.to(user.email).single())

        assertEquals(400, confirm(token, "short").status)

        assertEquals(204, confirm(token, "long-enough-now").status)
    }

    @Test
    fun `expired, reused and unknown tokens all fail the same way`() {
        val user = users.createUser(encoder)
        requestReset(user.email)
        val expired = mail.tokenOf(mail.to(user.email).single())
        jdbc.update("update password_reset_token set expires_at = ? where user_id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(60)), user.id)

        val expiredResponse = confirm(expired, "another-password")

        val other = users.createUser(encoder)
        requestReset(other.email)
        val used = mail.tokenOf(mail.to(other.email).single())
        assertEquals(204, confirm(used, "another-password").status)
        val reusedResponse = confirm(used, "yet-another-pass")

        val unknownResponse = confirm("never-issued-token", "another-password")

        for (r in listOf(expiredResponse, reusedResponse, unknownResponse)) {
            assertEquals(400, r.status)
            assertEquals("""{"error":"invalid_or_expired_token","fields":{}}""", r.body)
        }
    }

    @Test
    fun `a token works once even when submitted twice at the same moment`() {
        val user = users.createUser(encoder)
        requestReset(user.email)
        val token = mail.tokenOf(mail.to(user.email).single())
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        val results = (1..2).map { pool.submit<Int> { start.await(); confirm(token, "password-$it-aaaa").status } }
        start.countDown()
        val statuses = results.map { it.get() }.sorted()
        pool.shutdown()

        assertEquals(listOf(204, 400), statuses)
    }

    @Test
    fun `using one token invalidates the user's other open tokens`() {
        val user = users.createUser(encoder)
        requestReset(user.email)
        requestReset(user.email)
        val (first, second) = mail.to(user.email).map { mail.tokenOf(it) }

        assertEquals(204, confirm(first, "first-new-password").status)

        assertEquals(400, confirm(second, "second-new-password").status)
    }
}
