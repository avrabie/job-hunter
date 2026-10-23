package net.s4v3.backend

import net.s4v3.backend.files.internal.FileKind
import net.s4v3.backend.files.internal.FileRecord
import net.s4v3.backend.files.internal.JdbcFileRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class FileRepositoryTests(
    @Autowired private val files: JdbcFileRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun newUser(): String = ("repo-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun file(
        sub: String,
        kind: FileKind = FileKind.OTHER,
        name: String = "file.bin",
        uploadedAt: Instant = Instant.parse("2026-10-23T10:00:00Z"),
    ): FileRecord {
        val id = UUID.randomUUID()
        return FileRecord(
            id = id,
            ownerSub = sub,
            name = name,
            kind = kind,
            contentType = "application/octet-stream",
            sizeBytes = 3,
            sha256 = "h",
            storageKey = id.toString().take(2) + "/" + id,
            master = false,
            uploadedAt = uploadedAt,
        ).also { files.insert(it) }
    }

    @Test
    fun `a user sees only their own files`() {
        val a = newUser()
        val b = newUser()
        val mine = file(a, kind = FileKind.CV)

        assertEquals(mine, files.find(a, mine.id))
        assertNull(files.find(b, mine.id))
        assertEquals(emptyList(), files.list(b))
        assertNull(files.delete(b, mine.id))
        assertFalse(files.setMaster(b, mine.id))
        assertNull(files.master(b))
        assertEquals(mine, files.find(a, mine.id))
    }

    @Test
    fun `the list is newest first`() {
        val sub = newUser()
        val old = file(sub, uploadedAt = Instant.parse("2026-10-01T10:00:00Z"))
        val new = file(sub, uploadedAt = Instant.parse("2026-10-20T10:00:00Z"))
        val middle = file(sub, uploadedAt = Instant.parse("2026-10-10T10:00:00Z"))

        assertEquals(listOf(new.id, middle.id, old.id), files.list(sub).map { it.id })
    }

    @Test
    fun `the list can be filtered by kind`() {
        val sub = newUser()
        val cv = file(sub, kind = FileKind.CV)
        file(sub, kind = FileKind.OTHER)

        assertEquals(listOf(cv.id), files.list(sub, FileKind.CV).map { it.id })
        assertEquals(2, files.list(sub).size)
    }

    @Test
    fun `the master moves to the last file set and never two at once`() {
        val sub = newUser()
        val first = file(sub, kind = FileKind.CV)
        val second = file(sub, kind = FileKind.CV)

        assertTrue(files.setMaster(sub, first.id))
        assertTrue(files.setMaster(sub, second.id))

        assertEquals(second.id, files.master(sub)?.id)
        assertTrue(files.master(sub)!!.master)
        assertFalse(files.find(sub, first.id)!!.master)
    }

    @Test
    fun `an other file cannot be the master and the master stays`() {
        val sub = newUser()
        val cv = file(sub, kind = FileKind.CV)
        val other = file(sub, kind = FileKind.OTHER)
        files.setMaster(sub, cv.id)

        assertFalse(files.setMaster(sub, other.id))

        assertEquals(cv.id, files.master(sub)?.id)
    }

    @Test
    fun `clearing the master leaves the files`() {
        val sub = newUser()
        val cv = file(sub, kind = FileKind.CV)
        files.setMaster(sub, cv.id)

        files.clearMaster(sub)

        assertNull(files.master(sub))
        assertEquals(cv.id, files.find(sub, cv.id)?.id)
    }

    @Test
    fun `delete answers the storage key once`() {
        val sub = newUser()
        val cv = file(sub, kind = FileKind.CV)
        files.setMaster(sub, cv.id)

        assertEquals(cv.storageKey, files.delete(sub, cv.id))
        assertNull(files.delete(sub, cv.id))
        assertNull(files.find(sub, cv.id))
        assertNull(files.master(sub))
    }
}
