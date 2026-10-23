package net.s4v3.backend

import net.s4v3.backend.files.FileCabinet
import net.s4v3.backend.files.internal.FileCabinetService
import net.s4v3.backend.files.internal.FileKind
import net.s4v3.backend.files.internal.FileRecord
import net.s4v3.backend.files.internal.FileStorage
import net.s4v3.backend.files.internal.FilesSettings
import net.s4v3.backend.files.internal.JdbcFileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class FileCabinetTests(
    @Autowired private val cabinet: FileCabinet,
    @Autowired private val files: JdbcFileRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @TempDir
    lateinit var dir: Path

    private fun newUser(): String = ("cabinet-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    @Test
    fun `a user without a master gets null`() {
        val sub = newUser()

        assertNull(cabinet.masterCv(sub))
    }

    @Test
    fun `the master CV resolves to a readable path`() {
        val sub = newUser()
        val storage = FileStorage(FilesSettings(dir = dir.toString()))
        val id = UUID.randomUUID()
        val bytes = "Jane, engineer".toByteArray()
        val temp = Files.createDirectories(dir.resolve("tmp")).resolve("upload-" + UUID.randomUUID())
        Files.write(temp, bytes)
        val stored = storage.moveIn(temp, id)
        files.insert(
            FileRecord(
                id = id,
                ownerSub = sub,
                name = "cv.txt",
                kind = FileKind.CV,
                contentType = "text/plain",
                sizeBytes = stored.sizeBytes,
                sha256 = stored.sha256,
                storageKey = stored.key,
                master = false,
                uploadedAt = Instant.parse("2026-10-23T10:00:00Z"),
            ),
        )
        files.setMaster(sub, id)

        val master = FileCabinetService(files, storage).masterCv(sub)!!

        assertEquals(id, master.id)
        assertEquals("cv.txt", master.name)
        assertEquals("text/plain", master.contentType)
        assertEquals(bytes.size.toLong(), master.sizeBytes)
        assertTrue(Files.isReadable(master.path))
        assertContentEquals(bytes, Files.readAllBytes(master.path))
    }
}
