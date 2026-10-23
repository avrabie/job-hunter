package net.s4v3.backend

import net.s4v3.backend.files.internal.FileKind
import net.s4v3.backend.files.internal.FileRecord
import net.s4v3.backend.files.internal.FileStorage
import net.s4v3.backend.files.internal.FilesSettings
import net.s4v3.backend.files.internal.JdbcFileRepository
import net.s4v3.backend.files.internal.OrphanSweep
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IntegrationTest
internal class OrphanSweepTests(
    @Autowired private val files: JdbcFileRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @TempDir
    lateinit var dir: Path

    private val now: Instant = Instant.parse("2026-10-23T10:00:00Z")

    private fun newUser(): String = ("sweep-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun storage() = FileStorage(FilesSettings(dir = dir.toString()))

    /** Writes a stored file for [sub] (or none, when [sub] is null) and sets its last-written time to [ageHours] before now. */
    private fun storedFile(ageHours: Long, sub: String?): String {
        val storage = storage()
        val id = UUID.randomUUID()
        val temp = Files.createDirectories(dir.resolve("tmp")).resolve("upload-" + UUID.randomUUID())
        Files.writeString(temp, "bytes of $id")
        val stored = storage.moveIn(temp, id)
        Files.setLastModifiedTime(storage.open(stored.key), FileTime.from(now.minus(Duration.ofHours(ageHours))))
        if (sub != null) {
            files.insert(
                FileRecord(
                    id = id,
                    ownerSub = sub,
                    name = "file.bin",
                    kind = FileKind.OTHER,
                    contentType = "application/octet-stream",
                    sizeBytes = stored.sizeBytes,
                    sha256 = stored.sha256,
                    storageKey = stored.key,
                    master = false,
                    uploadedAt = now,
                ),
            )
        }
        return stored.key
    }

    @Test
    fun `an orphan older than a day is removed, a young orphan and a referenced file are kept`() {
        val sub = newUser()
        val old = storedFile(ageHours = 25, sub = null)
        val young = storedFile(ageHours = 23, sub = null)
        val referenced = storedFile(ageHours = 100, sub = sub)

        val removed = OrphanSweep(jdbc, storage()).sweep(now)

        assertEquals(1, removed)
        assertTrue(Files.notExists(storage().open(old)))
        assertTrue(Files.exists(storage().open(young)))
        assertTrue(Files.exists(storage().open(referenced)))
    }

    @Test
    fun `a second sweep finds nothing more`() {
        storedFile(ageHours = 48, sub = null)
        val sweep = OrphanSweep(jdbc, storage())

        assertEquals(1, sweep.sweep(now))
        assertEquals(0, sweep.sweep(now))
    }

    @Test
    fun `the temporary directory is never touched`() {
        val stale = Files.createDirectories(dir.resolve("tmp")).resolve("upload-" + UUID.randomUUID())
        Files.writeString(stale, "left behind")
        Files.setLastModifiedTime(stale, FileTime.from(now.minus(Duration.ofHours(72))))

        OrphanSweep(jdbc, storage()).sweep(now)

        assertTrue(Files.exists(stale))
    }
}
