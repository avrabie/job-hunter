package net.s4v3.backend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertFailsWith

@IntegrationTest
class CrawlScheduleSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    @AfterEach
    fun clean() {
        jdbc.update("delete from crawl_schedule")
    }

    private fun insert(id: Int, cron: String? = "0 0 * * * *") =
        jdbc.update(
            "insert into crawl_schedule (id, enabled, cron, zone, updated_at, updated_by) values (?, false, ?, 'Europe/Zurich', now(), 'u')",
            id, cron,
        )

    @Test
    fun `only one schedule row can exist`() {
        insert(1)

        assertFailsWith<DataIntegrityViolationException> { insert(1) }
        assertFailsWith<DataIntegrityViolationException> { insert(2) }
    }

    @Test
    fun `the cron is required`() {
        assertFailsWith<DataIntegrityViolationException> { insert(1, cron = null) }
    }
}
