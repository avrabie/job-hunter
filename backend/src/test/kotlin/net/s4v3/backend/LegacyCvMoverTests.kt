package net.s4v3.backend

import net.s4v3.backend.files.internal.FileKind
import net.s4v3.backend.files.internal.FileRecord
import net.s4v3.backend.files.internal.FileStorage
import net.s4v3.backend.files.internal.FilesSettings
import net.s4v3.backend.files.internal.JdbcFileRepository
import net.s4v3.backend.files.internal.LegacyCvMover
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@IntegrationTest
internal class LegacyCvMoverTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val files: JdbcFileRepository,
    @Autowired private val transactions: PlatformTransactionManager,
) {

    @TempDir
    lateinit var dir: Path

    private fun newUser(): String = ("legacy-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun storage(root: Path = dir) = FileStorage(FilesSettings(dir = root.toString()))

    private fun mover(root: Path = dir) = LegacyCvMover(jdbc, storage(root), transactions)

    private fun insertOldCv(sub: String, name: String, bytes: ByteArray, uploadedAt: Instant) {
        jdbc.update(
            "insert into cv_document (owner_sub, file_name, content_type, size_bytes, sha256, content, uploaded_at) values (?, ?, 'text/plain', ?, 'h', ?, ?)",
            sub, name, bytes.size, bytes, Timestamp.from(uploadedAt),
        )
    }

    private fun oldCount(sub: String) = jdbc.queryForObject("select count(*) from cv_document where owner_sub = ?", Int::class.java, sub)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun newMasterFile(sub: String): FileRecord {
        val id = UUID.randomUUID()
        val record = FileRecord(
            id = id,
            ownerSub = sub,
            name = "new.txt",
            kind = FileKind.CV,
            contentType = "text/plain",
            sizeBytes = 3,
            sha256 = "h",
            storageKey = id.toString().take(2) + "/" + id,
            master = true,
            uploadedAt = Instant.parse("2026-10-20T10:00:00Z"),
        )
        files.insert(record)
        return record
    }

    @Test
    fun `an old CV becomes the master file with the same bytes and the old row goes`() {
        val sub = newUser()
        val bytes = "Jane Doe, Kotlin engineer".toByteArray()
        val uploadedAt = Instant.parse("2026-10-01T10:00:00Z")
        insertOldCv(sub, "cv.txt", bytes, uploadedAt)

        mover().moveAll()

        assertEquals(0, oldCount(sub))
        val file = files.list(sub).single()
        assertTrue(file.master)
        assertEquals(FileKind.CV, file.kind)
        assertEquals("cv.txt", file.name)
        assertEquals("text/plain", file.contentType)
        assertEquals(bytes.size.toLong(), file.sizeBytes)
        assertEquals(sha256(bytes), file.sha256)
        assertEquals(uploadedAt, file.uploadedAt)
        assertContentEquals(bytes, Files.readAllBytes(storage().open(file.storageKey)))
    }

    @Test
    fun `a master chosen since then is kept and the legacy CV is only a file`() {
        val sub = newUser()
        val newer = newMasterFile(sub)
        insertOldCv(sub, "old.txt", "old".toByteArray(), Instant.parse("2026-10-01T10:00:00Z"))

        mover().moveAll()

        val masters = files.list(sub).filter { it.master }
        assertEquals(1, masters.size)
        assertEquals(newer.id, masters.single().id)
        assertTrue(files.list(sub).any { it.name == "old.txt" && !it.master })
    }

    @Test
    fun `a second run changes nothing`() {
        val sub = newUser()
        insertOldCv(sub, "cv.txt", "Jane".toByteArray(), Instant.parse("2026-10-01T10:00:00Z"))
        mover().moveAll()
        val before = files.list(sub)
        val keysBefore = storage().keys().toList()

        val moved = mover().moveAll()

        assertEquals(0, moved)
        assertEquals(before, files.list(sub))
        assertEquals(keysBefore, storage().keys().toList())
    }

    @Test
    fun `a user whose move fails keeps the old row and the run goes on`() {
        val failing = newUser()
        val alsoFailing = newUser()
        insertOldCv(failing, "stuck.txt", "stuck".toByteArray(), Instant.parse("2026-10-01T10:00:00Z"))
        insertOldCv(alsoFailing, "stuck2.txt", "stuck".toByteArray(), Instant.parse("2026-10-01T10:00:00Z"))
        // A regular file where the volume should be: no temporary directory can be made, so every move fails.
        val notADirectory = Files.createFile(dir.resolve("not-a-directory"))

        val moved = mover(notADirectory).moveAll()

        assertEquals(0, moved)
        assertEquals(1, oldCount(failing))
        assertEquals(1, oldCount(alsoFailing))
        assertTrue(files.list(failing).isEmpty())
    }
}
