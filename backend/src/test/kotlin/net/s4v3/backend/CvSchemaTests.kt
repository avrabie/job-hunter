package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class CvSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    private fun newUser(): String = ("cv-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun insert(sub: String, type: String = "application/pdf", size: Int = 10) =
        jdbc.update(
            "insert into cv_document (owner_sub, file_name, content_type, size_bytes, sha256, content) values (?, 'cv', ?, ?, 'h', ?)",
            sub, type, size, ByteArray(maxOf(size, 1)),
        )

    private fun count(sub: String) = jdbc.queryForObject("select count(*) from cv_document where owner_sub = ?", Int::class.java, sub)

    @Test
    fun `a user has at most one CV`() {
        val sub = newUser()
        insert(sub)

        assertFailsWith<DataIntegrityViolationException> { insert(sub) }
        insert(newUser())
    }

    @Test
    fun `an unknown user, another type or an oversized file is rejected`() {
        val sub = newUser()

        assertFailsWith<DataIntegrityViolationException> { insert("nobody-" + UUID.randomUUID()) }
        assertFailsWith<DataIntegrityViolationException> { insert(sub, type = "image/png") }
        assertFailsWith<DataIntegrityViolationException> { insert(sub, size = 5 * 1024 * 1024 + 1) }
        assertFailsWith<DataIntegrityViolationException> { insert(sub, size = 0) }
    }

    @Test
    fun `deleting the user deletes the CV`() {
        val sub = newUser()
        val other = newUser()
        insert(sub)
        insert(other)

        jdbc.update("delete from app_user where sub = ?", sub)

        assertEquals(0, count(sub))
        assertEquals(1, count(other))
    }
}
