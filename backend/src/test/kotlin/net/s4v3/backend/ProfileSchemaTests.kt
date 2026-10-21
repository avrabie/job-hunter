package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import java.util.UUID
import kotlin.test.assertEquals

@IntegrationTest
class ProfileSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `deleting a user deletes the profile, and only that user's`() {
        val a = "schema-a-" + UUID.randomUUID()
        val b = "schema-b-" + UUID.randomUUID()
        listOf(a, b).forEach { sub ->
            jdbc.update("insert into app_user (sub) values (?)", sub)
            jdbc.update("insert into profile (owner_sub, document, schema_version, version) values (?, '{}'::jsonb, 1, 1)", sub)
        }

        jdbc.update("delete from app_user where sub = ?", a)

        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from profile where owner_sub = ?", a))
        assertEquals(1, jdbc.queryForObject<Int>("select count(*) from profile where owner_sub = ?", b))
    }
}
