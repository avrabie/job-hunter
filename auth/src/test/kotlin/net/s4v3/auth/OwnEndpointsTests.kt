package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals

@WebIntegrationTest
class OwnEndpointsTests(
    @LocalServerPort port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)

    @Test
    fun `the self-service api rejects a request without a token`() {
        assertEquals(401, flow.get("${flow.base}/api/me/anything").status)
    }

    @Test
    fun `the self-service api rejects a token it did not issue`() {
        val forged = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."

        assertEquals(401, flow.get("${flow.base}/api/me/anything", "Authorization" to "Bearer $forged").status)
    }

    @Test
    fun `the self-service api accepts a token issued by this server`() {
        val user = users.createUser(encoder)
        val access = flow.tokens(user.email, "correct-horse")["access_token"].asString()

        // no such resource, but the request got past authentication (404, not 401)
        assertEquals(404, flow.get("${flow.base}/api/me/anything", "Authorization" to "Bearer $access").status)
    }
}
