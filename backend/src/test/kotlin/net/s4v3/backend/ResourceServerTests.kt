package net.s4v3.backend

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import kotlin.test.assertNotEquals

@ApiTest
class ResourceServerTests(@Autowired private val mvc: MockMvc) {

    private fun api(token: String? = null, path: String = "/api/test/ping") =
        mvc.perform(get(path).apply { if (token != null) header("Authorization", "Bearer $token") })

    @Test
    fun `no token is rejected with 401`() {
        api().andExpect(status().isUnauthorized)
    }

    @Test
    fun `a token from another issuer is rejected with 401`() {
        api(issuer.token("user-1", issuer = "http://evil.example/auth")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `an expired token is rejected with 401`() {
        api(issuer.token("user-1", expiresAt = Instant.now().minusSeconds(600))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a token signed with a key the issuer does not publish is rejected with 401`() {
        val forgedKey = RSAKeyGenerator(2048).keyID("test-key").generate()
        api(issuer.token("user-1", signingKey = forgedKey)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a malformed token is rejected with 401`() {
        api("not.a.jwt").andExpect(status().isUnauthorized)
    }

    @Test
    fun `a valid token is accepted and its subject is the principal`() {
        api(issuer.token("user-1")).andExpect(status().isOk).andExpect(content().string("user-1"))
    }

    @Test
    fun `a role-restricted endpoint accepts the role`() {
        api(issuer.token("admin-1", roles = listOf("USER", "ADMIN")), "/api/test/admin").andExpect(status().isOk)
    }

    @Test
    fun `a role-restricted endpoint rejects a token without the role with 403`() {
        api(issuer.token("user-1", roles = listOf("USER")), "/api/test/admin").andExpect(status().isForbidden)
    }

    @Test
    fun `a token without a roles claim has no roles and gets 403 on a restricted endpoint`() {
        api(issuer.token("user-1", roles = emptyList()), "/api/test/admin").andExpect(status().isForbidden)
    }

    @Test
    fun `health endpoints need no token`() {
        mvc.perform(get("/actuator/health/liveness")).andExpect { assertNotEquals(401, it.response.status) }
    }

    private val issuer get() = SharedMockIssuer.instance
}
