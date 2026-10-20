package net.s4v3.auth

import com.nimbusds.jose.JWSAlgorithm
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import net.s4v3.auth.user.UserRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@WebIntegrationTest
class AuthorizationServerTests(
    @LocalServerPort private val port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)

    @Test
    fun `discovery reports the configured issuer whatever the host headers say`() {
        val plain = flow.get("${flow.base}/.well-known/openid-configuration")
        val spoofed = flow.get(
            "${flow.base}/.well-known/openid-configuration",
            "Host" to "evil.example", "X-Forwarded-Host" to "evil.example", "X-Forwarded-Proto" to "https",
        )

        for (response in listOf(plain, spoofed)) {
            assertEquals(200, response.status)
            val doc = response.json()
            assertEquals("http://localhost/auth", doc["issuer"].asString())
            for (endpoint in listOf("authorization_endpoint", "token_endpoint", "jwks_uri", "userinfo_endpoint")) {
                assertTrue(doc[endpoint].asString().startsWith("http://localhost/auth/"), "$endpoint: ${doc[endpoint]}")
            }
        }
    }

    @Test
    fun `the published keys verify an issued token and the issuer claim matches`() {
        val user = users.createUser(encoder)
        val tokens = flow.tokens(user.email, "correct-horse")

        val decoder = NimbusJwtDecoder.withJwkSetUri("${flow.base}/oauth2/jwks")
            .jwsAlgorithm(SignatureAlgorithm.RS256).build()
        val jwt = decoder.decode(tokens["access_token"].asString())

        assertEquals("http://localhost/auth", jwt.issuer.toString())
        assertEquals(JWSAlgorithm.RS256.name, com.nimbusds.jwt.SignedJWT.parse(tokens["access_token"].asString()).header.algorithm.name)
        assertNotNull(jwt.expiresAt)
    }
}
