package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ApiTest
class UserProvisioningTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val issuer get() = SharedMockIssuer.instance

    private fun call(token: String) =
        mvc.perform(get("/api/test/ping").header("Authorization", "Bearer $token"))

    private fun rows(sub: String) =
        jdbc.queryForList("select sub, email, display_name from app_user where sub = ?", sub)

    private fun newSub() = "user-" + UUID.randomUUID()

    @Test
    fun `the first request of a new user creates one record with the token's profile claims`() {
        val sub = newSub()

        call(issuer.token(sub, extraClaims = mapOf("email" to "anna@example.com", "name" to "Anna Muster")))
            .andExpect(status().isOk)

        val row = rows(sub).single()
        assertEquals("anna@example.com", row["email"])
        assertEquals("Anna Muster", row["display_name"])
    }

    @Test
    fun `a repeat request creates no additional record and keeps the existing one`() {
        val sub = newSub()
        call(issuer.token(sub, extraClaims = mapOf("email" to "first@example.com"))).andExpect(status().isOk)

        call(issuer.token(sub, extraClaims = mapOf("email" to "changed@example.com"))).andExpect(status().isOk)

        assertEquals("first@example.com", rows(sub).single()["email"])
    }

    @Test
    fun `claims that are absent from the token are absent from the record`() {
        val sub = newSub()

        call(issuer.token(sub)).andExpect(status().isOk)

        val row = rows(sub).single()
        assertNull(row["email"])
        assertNull(row["display_name"])
    }

    @Test
    fun `a token without a subject is rejected and creates no record`() {
        val before = jdbc.queryForObject("select count(*) from app_user", Int::class.java)

        call(issuer.token(sub = null)).andExpect(status().isUnauthorized)

        assertEquals(before, jdbc.queryForObject("select count(*) from app_user", Int::class.java))
    }

    @Test
    fun `a rejected token creates no record`() {
        val sub = newSub()

        call(issuer.token(sub, issuer = "http://evil.example/auth")).andExpect(status().isUnauthorized)

        assertEquals(0, rows(sub).size)
    }
}
