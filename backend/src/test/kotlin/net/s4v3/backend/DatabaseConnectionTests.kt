package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IntegrationTest
class DatabaseConnectionTests(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `connects to the pgvector container`() {
        assertEquals(1, jdbc.queryForObject("select 1", Int::class.java))
        assertTrue(jdbc.queryForObject("select version()", String::class.java)!!.startsWith("PostgreSQL"))
    }
}
