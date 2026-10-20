package net.s4v3.auth

import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@IntegrationTest
class AuthApplicationTests(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `context loads and connects to the container database`() {
        assertEquals(1, jdbc.queryForObject("select 1", Int::class.java))
    }
}
