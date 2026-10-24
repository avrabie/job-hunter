package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class PublicCvSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    private fun newUser(): String = ("pcv-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun handle(): String = "pcv-" + UUID.randomUUID().toString().take(12)

    private fun insertPage(sub: String, handle: String) =
        jdbc.update(
            "insert into public_cv (owner_sub, handle, settings, published, settings_version) values (?, ?, '{}'::jsonb, true, 1)",
            sub, handle,
        )

    private fun insertHold(sub: String, handle: String) =
        jdbc.update(
            "insert into public_cv_handle_hold (handle, owner_sub, held_until) values (?, ?, now() + interval '90 days')",
            handle, sub,
        )

    private fun insertAudit(sub: String) =
        jdbc.update(
            "insert into public_cv_audit (owner_sub, event, occurred_at, flags) values (?, 'PUBLISHED', now(), '{}'::jsonb)",
            sub,
        )

    private fun count(table: String, sub: String) =
        jdbc.queryForObject<Int>("select count(*) from $table where owner_sub = ?", sub)

    @Test
    fun `the three tables exist`() {
        val tables = jdbc.queryForObject<Int>(
            """
            select count(*) from information_schema.tables
            where table_schema = current_schema()
              and table_name in ('public_cv', 'public_cv_handle_hold', 'public_cv_audit')
            """.trimIndent(),
        )
        assertEquals(3, tables)
    }

    @Test
    fun `the handle index is unique on lower-case handles`() {
        val definition = requireNotNull(
            jdbc.queryForObject<String>("select indexdef from pg_indexes where indexname = 'public_cv_handle_lower_idx'"),
        )
        assertContains(definition, "UNIQUE")
        assertContains(definition, "lower(handle)")
    }

    @Test
    fun `a handle can belong to one page only`() {
        val taken = handle()
        insertPage(newUser(), taken)

        assertFailsWith<DataIntegrityViolationException> { insertPage(newUser(), taken) }
    }

    @Test
    fun `a stored handle is lower-case`() {
        assertFailsWith<DataIntegrityViolationException> { insertPage(newUser(), "Pcv-Upper") }
        assertFailsWith<DataIntegrityViolationException> { insertHold(newUser(), "Pcv-Upper") }
    }

    @Test
    fun `deleting a user deletes its page, holds and audit, and only that user's`() {
        val gone = newUser()
        val kept = newUser()
        listOf(gone, kept).forEach { sub ->
            insertPage(sub, handle())
            insertHold(sub, handle())
            insertAudit(sub)
        }

        jdbc.update("delete from app_user where sub = ?", gone)

        listOf("public_cv", "public_cv_handle_hold", "public_cv_audit").forEach { table ->
            assertEquals(0, count(table, gone), table)
            assertEquals(1, count(table, kept), table)
        }
    }
}
