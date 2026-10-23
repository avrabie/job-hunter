package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class FilesSchemaTests(@Autowired private val jdbc: JdbcTemplate) {

    private fun newUser(): String = ("files-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun insert(
        sub: String,
        kind: String = "OTHER",
        size: Long = 10,
        master: Boolean = false,
        contentType: String = "application/octet-stream",
    ) =
        jdbc.update(
            """
            insert into user_file (id, owner_sub, name, kind, content_type, size_bytes, sha256, storage_key, master, uploaded_at)
            values (?, ?, 'file', ?, ?, ?, 'h', ?, ?, now())
            """.trimIndent(),
            UUID.randomUUID(), sub, kind, contentType, size, "k/" + UUID.randomUUID(), master,
        )

    private fun count(sub: String) = jdbc.queryForObject("select count(*) from user_file where owner_sub = ?", Int::class.java, sub)

    @Test
    fun `a user has at most one master file`() {
        val sub = newUser()
        insert(sub, kind = "CV", master = true)

        assertFailsWith<DataIntegrityViolationException> { insert(sub, kind = "CV", master = true) }
        insert(sub, kind = "CV", master = false)
        insert(newUser(), kind = "CV", master = true)
    }

    @Test
    fun `a master must be a CV`() {
        val sub = newUser()

        assertFailsWith<DataIntegrityViolationException> { insert(sub, kind = "OTHER", master = true) }
        insert(sub, kind = "OTHER", master = false)
    }

    @Test
    fun `an unknown kind, an unknown user or an oversized file is rejected`() {
        val sub = newUser()

        assertFailsWith<DataIntegrityViolationException> { insert(sub, kind = "PHOTO") }
        assertFailsWith<DataIntegrityViolationException> { insert("nobody-" + UUID.randomUUID()) }
        assertFailsWith<DataIntegrityViolationException> { insert(sub, size = 10_737_418_240L + 1) }
        assertFailsWith<DataIntegrityViolationException> { insert(sub, size = -1) }
        insert(sub, size = 10_737_418_240L)
    }

    @Test
    fun `deleting the user deletes the files`() {
        val sub = newUser()
        val other = newUser()
        insert(sub)
        insert(sub)
        insert(other)

        jdbc.update("delete from app_user where sub = ?", sub)

        assertEquals(0, count(sub))
        assertEquals(1, count(other))
    }
}
