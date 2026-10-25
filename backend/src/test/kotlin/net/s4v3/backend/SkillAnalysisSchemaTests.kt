package net.s4v3.backend

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The `letter_skill_analysis` changeset: shape, owner index, and that its rollback statements work. */
@IntegrationTest
internal class SkillAnalysisSchemaTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val transactions: TransactionTemplate,
) {
    private fun exists(sql: String) = jdbc.queryForObject(sql, Int::class.java) == 1

    @Test
    fun `the table has the owner index and a cascading foreign key`() {
        val columns = jdbc.queryForList("select column_name from information_schema.columns where table_name = 'letter_skill_analysis'", String::class.java)
        assertEquals(setOf("application_id", "owner_sub", "analysis", "notes", "created_at"), columns.toSet())
        assertTrue(exists("select count(*) from pg_indexes where indexname = 'letter_skill_analysis_owner'"))
        assertTrue(exists("select count(*) from information_schema.referential_constraints where constraint_name in (select constraint_name from information_schema.table_constraints where table_name = 'letter_skill_analysis') and delete_rule = 'CASCADE'"))
    }

    @Test
    fun `the rollback statements of the changeset remove the table and the index`() {
        val lines = Files.readAllLines(Path.of("src/main/resources/db/changelog/013-letter-skill-gaps.sql")).filter { it.startsWith("--rollback ") }
        assertTrue(lines.size >= 2)
        val gone = transactions.execute { status ->
            lines.forEach { jdbc.execute(it.removePrefix("--rollback ")) }
            val result = !exists("select count(*) from information_schema.tables where table_name = 'letter_skill_analysis'")
            status.setRollbackOnly()
            result
        }
        assertTrue(gone == true)
        assertTrue(exists("select count(*) from information_schema.tables where table_name = 'letter_skill_analysis'"), "the rollback test must not change the database")
    }
}
