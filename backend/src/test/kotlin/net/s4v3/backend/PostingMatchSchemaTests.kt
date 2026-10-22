package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class PostingMatchSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    private fun newUser(): String = ("match-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun newPosting(): UUID {
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

    private fun insert(sub: String, posting: UUID, score: Int = 50) =
        jdbc.update(
            "insert into posting_match (user_sub, posting_id, passed, score, reasons, profile_version, evaluated_at) " +
                "values (?, ?, true, ?, array['ROLE_MATCH'], 1, now())",
            sub, posting, score,
        )

    private fun count(sub: String) =
        jdbc.queryForObject("select count(*) from posting_match where user_sub = ?", Int::class.java, sub)

    @Test
    fun `a second row for the same user and posting is rejected`() {
        val sub = newUser()
        val posting = newPosting()
        insert(sub, posting)

        assertFailsWith<DataIntegrityViolationException> { insert(sub, posting) }
        insert(newUser(), posting)
    }

    @Test
    fun `an unknown user or posting is rejected, and the score stays within 0 to 100`() {
        val sub = newUser()
        val posting = newPosting()

        assertFailsWith<DataIntegrityViolationException> { insert("nobody-" + UUID.randomUUID(), posting) }
        assertFailsWith<DataIntegrityViolationException> { insert(sub, UUID.randomUUID()) }
        assertFailsWith<DataIntegrityViolationException> { insert(sub, posting, score = 101) }
    }

    @Test
    fun `deleting the user or the posting deletes the results`() {
        val sub = newUser()
        val other = newUser()
        val posting = newPosting()
        val second = newPosting()
        insert(sub, posting)
        insert(sub, second)
        insert(other, posting)

        jdbc.update("delete from posting where id = ?", second)
        assertEquals(1, count(sub))
        jdbc.update("delete from app_user where sub = ?", sub)

        assertEquals(0, count(sub))
        assertEquals(1, count(other))
    }
}
