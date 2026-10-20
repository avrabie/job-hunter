package net.s4v3.auth

import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Whole Google sign-ins against a fake Google, through the real authorization code flow with PKCE. */
@WebIntegrationTest
@Import(FakeGoogleConfig::class)
@TestPropertySource(
    properties = [
        "logging.level.org.springframework.security=TRACE",
        "logging.level.org.springframework.web.client=TRACE",
        "logging.level.org.springframework.http.client=TRACE",
    ],
)
class GoogleLoginFlowTests(
    @LocalServerPort private val port: Int,
    @Autowired private val google: FakeGoogle,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val mail: RecordingMailSender,
) {

    private fun signInWithGoogle(identity: FakeGoogle.Identity): Pair<AuthFlow, Resp> {
        google.identity = identity
        val flow = AuthFlow(port)
        val pkce = flow.pkce()
        val finalRedirect = flow.authorizeWithGoogle(pkce)
        lastPkce = pkce
        return flow to finalRedirect
    }

    private var lastPkce: Pkce? = null

    private fun subOf(flow: AuthFlow, redirect: Resp): String {
        val tokens = flow.exchange(flow.codeFrom(redirect), lastPkce!!.verifier)
        assertEquals(200, tokens.status, tokens.body)
        return AuthFlow.claims(tokens.json()["access_token"].asString()).subject
    }

    @Test
    fun `a first Google sign-in creates the account and the SPA gets tokens for its UUID`() {
        val identity = FakeGoogle.Identity()

        val (flow, redirect) = signInWithGoogle(identity)

        assertTrue(redirect.location!!.startsWith("${AuthFlow.REDIRECT}?code="), redirect.toString())
        val user = assertNotNull(users.findByEmail(identity.email))
        assertEquals(Provider.GOOGLE, user.provider)
        assertEquals(identity.sub, user.providerId)
        assertEquals("Gina Google", user.displayName)
        assertNull(user.password)
        assertEquals(user.id.toString(), subOf(flow, redirect))
    }

    @Test
    fun `the access token carries the roles, email and name of the Google user`() {
        val identity = FakeGoogle.Identity()
        val (flow, redirect) = signInWithGoogle(identity)

        val tokens = flow.exchange(flow.codeFrom(redirect), lastPkce!!.verifier).json()

        val claims = AuthFlow.claims(tokens["access_token"].asString())
        assertEquals(listOf("USER"), claims.getStringListClaim("roles"))
        assertEquals(identity.email, claims.getStringClaim("email"))
        assertEquals("Gina Google", claims.getStringClaim("name"))
    }

    @Test
    fun `a returning Google user keeps the same sub and account`() {
        val identity = FakeGoogle.Identity()
        val (f1, r1) = signInWithGoogle(identity)
        val first = subOf(f1, r1)

        val (f2, r2) = signInWithGoogle(identity)

        assertEquals(first, subOf(f2, r2))
        assertEquals(1, jdbc.queryForObject("select count(*) from users where provider_id = ?", Int::class.java, identity.sub))
    }

    @Test
    fun `an unverified Google email creates and links nothing`() {
        val identity = FakeGoogle.Identity(emailVerified = false)

        val (_, redirect) = signInWithGoogle(identity)

        assertTrue("/login?error" in redirect.location!!, redirect.toString())
        assertNull(users.findByEmail(identity.email))
    }

    @Test
    fun `a Google account cannot be entered through the password form`() {
        val identity = FakeGoogle.Identity()
        val (f, r) = signInWithGoogle(identity)
        subOf(f, r)

        val form = AuthFlow(port)

        assertTrue("error" in form.login(identity.email, "any-password-at-all").location!!)
    }

    @Test
    fun `squatter loses access when the owner signs in with Google, and a reset restores form login`() {
        val flow = AuthFlow(port)
        val victim = "victim-${UUID.randomUUID()}@example.com"
        assertEquals(201, flow.postJson("${flow.base}/api/register", """{"email":"$victim","password":"squatter-pass-1"}""").status)
        val originalId = users.findByEmail(victim)!!.id

        val (gf, gr) = signInWithGoogle(FakeGoogle.Identity(email = victim))

        assertEquals(originalId.toString(), subOf(gf, gr), "Google lands on the same account")
        assertTrue("error" in AuthFlow(port).login(victim, "squatter-pass-1").location!!, "squatter's password is dead")

        assertEquals(200, flow.postJson("${flow.base}/api/password-reset/request", """{"email":"$victim"}""").status)
        val token = mail.tokenOf(mail.to(victim).single())
        assertEquals(204, flow.postJson("${flow.base}/api/password-reset/confirm", """{"token":"$token","newPassword":"owner-pass-2"}""").status)

        val formTokens = AuthFlow(port).tokens(victim, "owner-pass-2")
        assertEquals(originalId.toString(), AuthFlow.claims(formTokens["access_token"].asString()).subject)
        // and Google still works for the linked account, with the same sub
        val (gf2, gr2) = signInWithGoogle(FakeGoogle.Identity(sub = users.findByEmail(victim)!!.providerId!!, email = victim))
        assertEquals(originalId.toString(), subOf(gf2, gr2))
    }

    @Test
    fun `only the Google subject, email and names are stored and no Google token is logged`() {
        val appender = LogCapture.attach()
        val identity = FakeGoogle.Identity()
        try {
            val (f, r) = signInWithGoogle(identity)
            subOf(f, r)
        } finally {
            LogCapture.detach(appender)
        }

        val columns = jdbc.queryForMap("select * from users where provider_id = ?", identity.sub)
        assertEquals(
            setOf("id", "email", "password", "first_name", "last_name", "enabled", "provider", "provider_id", "version", "created_at", "updated_at"),
            columns.keys,
        )
        assertTrue(google.issued.isNotEmpty())
        val log = LogCapture.lines(appender).joinToString("\n")
        for (token in google.issued) {
            assertTrue(token !in log, "a Google token was logged")
        }
        assertTrue(columns.values.none { v -> google.issued.any { it in v.toString() } }, "no Google token in the row")
    }
}
