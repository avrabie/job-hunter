package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class PostingSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    private fun insertPosting(): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            insert into posting (id, title, company, description, language, out_of_scope, needs_review, content_hash,
                                 raw_payload, first_seen, last_seen)
            values (?, 't', 'c', 'd', 'en', false, false, 'h', '{}', now(), now())
            """.trimIndent(),
            id,
        )
        return id
    }

    private fun insertSource(postingId: UUID, sourceId: String, sourcePostingId: String) =
        jdbc.update(
            """
            insert into posting_source (id, posting_id, source_id, source_posting_id, url, is_primary, last_seen)
            values (?, ?, ?, ?, 'https://example.org', true, now())
            """.trimIndent(),
            UUID.randomUUID(), postingId, sourceId, sourcePostingId,
        )

    @Test
    fun `a source posting id can be stored once per source`() {
        val posting = insertPosting()
        val sourceId = "src-" + UUID.randomUUID()
        insertSource(posting, sourceId, "1")

        assertFailsWith<DataIntegrityViolationException> { insertSource(posting, sourceId, "1") }
        insertSource(posting, sourceId, "2")
        insertSource(posting, "other-$sourceId", "1")
    }

    @Test
    fun `a source reference needs an existing posting`() {
        assertFailsWith<DataIntegrityViolationException> { insertSource(UUID.randomUUID(), "s", "1") }
    }

    @Test
    fun `deleting a posting deletes its source references`() {
        val posting = insertPosting()
        insertSource(posting, "src-" + UUID.randomUUID(), "1")

        jdbc.update("delete from posting where id = ?", posting)

        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from posting_source where posting_id = ?", posting))
    }
}
