package net.s4v3.auth

import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@WebIntegrationTest
class FormLoginTests(
    @LocalServerPort private val port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {

    private fun withoutCsrf(html: String) = html.replace(Regex("""name="_csrf" type="hidden" value="[^"]*""""), "")

    @Test
    fun `login by email works and the token sub is the UUID, not the email`() {
        val user = users.createUser(encoder)

        val tokens = AuthFlow(port).tokens(user.email.uppercase(), "correct-horse")

        for (name in listOf("access_token", "id_token")) {
            val subject = AuthFlow.claims(tokens[name].asString()).subject
            assertEquals(user.id.toString(), subject, name)
            assertNotEquals(user.email, subject)
        }
    }

    @Test
    fun `a sign-in during an authorization request resumes it and returns a code`() {
        val user = users.createUser(encoder)
        val flow = AuthFlow(port)

        val redirect = flow.authorizeAndLogin(user.email, "correct-horse", flow.pkce())

        assertTrue(redirect.location!!.startsWith("${AuthFlow.REDIRECT}?code="), redirect.toString())
        assertEquals("state-1", AuthFlow.queryOf(redirect.location!!)["state"])
    }

    @Test
    fun `a wrong password and an unknown email give the same generic error`() {
        val user = users.createUser(encoder)

        val wrongPassword = AuthFlow(port).let { it.login(user.email, "nope-nope-nope").let { r -> r to it.get(r.location!!) } }
        val unknownEmail = AuthFlow(port).let { it.login("nobody@example.com", "nope-nope-nope").let { r -> r to it.get(r.location!!) } }

        assertEquals(wrongPassword.first.location, unknownEmail.first.location)
        assertTrue("error" in wrongPassword.first.location!!)
        assertEquals(withoutCsrf(wrongPassword.second.body), withoutCsrf(unknownEmail.second.body))
        assertTrue("Invalid credentials" in wrongPassword.second.body, wrongPassword.second.body)
    }

    @Test
    fun `an account without a password cannot be entered with any password`() {
        val google = users.createUser(
            encoder, password = null, provider = Provider.GOOGLE, providerId = "g-${java.util.UUID.randomUUID()}",
        )

        for (attempt in listOf("", "password", "{noop}password", "correct-horse")) {
            val flow = AuthFlow(port)
            val response = flow.login(google.email, attempt.ifEmpty { "x" })

            assertTrue("error" in response.location!!, "attempt '$attempt': $response")
            assertTrue(flow.get("${flow.base}/api/me/x").status == 401, "still signed out")
        }
    }

    @Test
    fun `a disabled account cannot sign in`() {
        val user = users.createUser(encoder)
        users.save(user.copy(enabled = false))
        val flow = AuthFlow(port)

        assertTrue("error" in flow.login(user.email, "correct-horse").location!!)
    }
}
