package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@WebIntegrationTest
class TokenClaimsTests(
    @LocalServerPort port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)

    @Test
    fun `a user with a name gets sub, roles, email and name in the access token`() {
        val user = users.createUser(encoder, firstName = "Ann", lastName = "Lee")

        val claims = AuthFlow.claims(flow.tokens(user.email, "correct-horse")["access_token"].asString())

        assertEquals(user.id.toString(), claims.subject)
        assertEquals("http://localhost/auth", claims.issuer)
        assertEquals(listOf("USER"), claims.getStringListClaim("roles"))
        assertEquals(user.email, claims.getStringClaim("email"))
        assertEquals("Ann Lee", claims.getStringClaim("name"))
        assertTrue(claims.expirationTime.time - claims.issueTime.time == 15 * 60 * 1000L)
    }

    @Test
    fun `a user without a name gets no name claim`() {
        val user = users.createUser(encoder, firstName = null, lastName = null)

        val claims = AuthFlow.claims(flow.tokens(user.email, "correct-horse")["access_token"].asString())

        assertEquals(user.email, claims.getStringClaim("email"))
        assertNull(claims.getClaim("name"))
    }

    @Test
    fun `the id token carries the profile claims`() {
        val user = users.createUser(encoder)

        val claims = AuthFlow.claims(flow.tokens(user.email, "correct-horse")["id_token"].asString())

        assertEquals(user.id.toString(), claims.subject)
        assertEquals(user.email, claims.getStringClaim("email"))
        assertEquals("Ann Lee", claims.getStringClaim("name"))
    }

    @Test
    fun `email and name are left out when their scopes are not granted`() {
        val user = users.createUser(encoder)

        val claims = AuthFlow.claims(flow.tokens(user.email, "correct-horse", scope = "openid")["access_token"].asString())

        assertNull(claims.getClaim("email"))
        assertNull(claims.getClaim("name"))
        assertEquals(listOf("USER"), claims.getStringListClaim("roles"))
    }

    @Test
    fun `no token contains password data`() {
        val user = users.createUser(encoder)
        val tokens = flow.tokens(user.email, "correct-horse")

        for (name in listOf("access_token", "id_token")) {
            val claims = AuthFlow.claims(tokens[name].asString())
            val text = claims.toString()
            assertFalse("correct-horse" in text || "bcrypt" in text || "password" in text.lowercase(), "$name: $text")
        }
    }

    @Test
    fun `userinfo works for a user whose sub is a UUID`() {
        val user = users.createUser(encoder)
        val access = flow.tokens(user.email, "correct-horse")["access_token"].asString()

        val response = flow.get("${flow.base}/userinfo", "Authorization" to "Bearer $access")

        assertEquals(200, response.status, response.body)
        assertEquals(user.id.toString(), response.json()["sub"].asString())
        assertEquals(user.email, response.json()["email"].asString())
    }
}
