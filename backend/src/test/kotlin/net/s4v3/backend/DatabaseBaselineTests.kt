package net.s4v3.backend

import liquibase.integration.spring.SpringLiquibase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IntegrationTest
class DatabaseBaselineTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val liquibase: SpringLiquibase,
) {

    private fun count(sql: String) = jdbc.queryForObject(sql, Int::class.java)!!

    @Test
    fun `changelog applies to an empty database`() {
        val tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String::class.java,
        )
        assertTrue("event_publication" in tables, tables.toString())
        assertTrue("app_user" in tables, tables.toString())
        assertEquals(1, count("select count(*) from pg_extension where extname = 'vector'"))
        assertTrue("posting" in tables && "posting_source" in tables, tables.toString())
        assertTrue("source_fetch_state" in tables && "crawl_schedule" in tables && "posting_match" in tables, tables.toString())
        assertTrue("cv_document" in tables, tables.toString())
        assertEquals(10, count("select count(*) from databasechangelog"))
    }

    @Test
    fun `running the changelog again changes nothing`() {
        val before = jdbc.queryForList("select id, md5sum, dateexecuted::text from databasechangelog order by orderexecuted")

        liquibase.afterPropertiesSet()

        val after = jdbc.queryForList("select id, md5sum, dateexecuted::text from databasechangelog order by orderexecuted")
        assertEquals(before, after)
    }

    @Test
    @Transactional
    fun `a vector column can be created and queried`() {
        jdbc.execute("create table vector_probe (id int primary key, embedding vector(3))")
        jdbc.update("insert into vector_probe values (1, '[1,2,3]'), (2, '[1,2,4]')")

        val nearest = jdbc.queryForObject(
            "select id from vector_probe order by embedding <-> '[1,2,4]' limit 1",
            Int::class.java,
        )
        assertEquals(2, nearest)
    }
}
