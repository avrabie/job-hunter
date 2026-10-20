package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals

/** The principal name is a UUID, which Spring Authorization Server must accept at every step. */
@WebIntegrationTest
class UuidSubjectTests(
    @LocalServerPort port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)

    @Test
    fun `code exchange, refresh and userinfo all keep the UUID as sub`() {
        val user = users.createUser(encoder)
        val first = flow.tokens(user.email, "correct-horse")

        val refreshed = flow.postForm(
            "${flow.base}/oauth2/token",
            mapOf("grant_type" to "refresh_token", "refresh_token" to first["refresh_token"].asString(), "client_id" to "spa"),
        ).json()
        val userinfo = flow.get("${flow.base}/userinfo", "Authorization" to "Bearer ${refreshed["access_token"].asString()}")

        for (token in listOf(first["access_token"], first["id_token"], refreshed["access_token"], refreshed["id_token"])) {
            assertEquals(user.id.toString(), AuthFlow.claims(token.asString()).subject)
        }
        assertEquals(listOf("USER"), AuthFlow.claims(refreshed["access_token"].asString()).getStringListClaim("roles"))
        assertEquals(200, userinfo.status, userinfo.body)
        assertEquals(user.id.toString(), userinfo.json()["sub"].asString())
    }
}
