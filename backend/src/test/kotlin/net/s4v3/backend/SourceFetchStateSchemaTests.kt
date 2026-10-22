package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertFailsWith

@IntegrationTest
class SourceFetchStateSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    private fun insert(sourceId: String) =
        jdbc.update("insert into source_fetch_state (source_id, etag, last_run_at) values (?, 'W/\"x\"', now())", sourceId)

    @Test
    fun `a board has one state row`() {
        val id = "greenhouse-" + UUID.randomUUID()
        insert(id)

        assertFailsWith<DataIntegrityViolationException> { insert(id) }
    }

    @Test
    fun `a run start is required but the validator is not`() {
        jdbc.update("insert into source_fetch_state (source_id, last_run_at) values (?, now())", "g-" + UUID.randomUUID())

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update("insert into source_fetch_state (source_id) values (?)", "g-" + UUID.randomUUID())
        }
    }
}
