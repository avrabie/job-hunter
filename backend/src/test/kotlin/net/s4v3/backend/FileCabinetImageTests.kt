package net.s4v3.backend

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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNull

@IntegrationTest
internal class FileCabinetImageTests(
    @Autowired private val files: JdbcFileRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @TempDir
    lateinit var dir: Path

    private fun newUser(): String = ("image-user-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    /** A small synthetic JPEG drawn by the test itself. */
    private fun jpeg(): ByteArray {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 16) for (y in 0 until 16) image.setRGB(x, y, (x * 16) shl 16 or (y * 16) shl 8 or 0x80)
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    /** Stores [bytes] for [sub] through the real storage and records the row; answers the file id. */
    private fun store(sub: String, name: String, contentType: String, bytes: ByteArray, storage: FileStorage): UUID {
        val id = UUID.randomUUID()
        val temp = Files.createDirectories(dir.resolve("tmp")).resolve("upload-" + UUID.randomUUID())
        Files.write(temp, bytes)
        val stored = storage.moveIn(temp, id)
        files.insert(
            FileRecord(
                id = id,
                ownerSub = sub,
                name = name,
                kind = FileKind.OTHER,
                contentType = contentType,
                sizeBytes = stored.sizeBytes,
                sha256 = stored.sha256,
                storageKey = stored.key,
                master = false,
                uploadedAt = Instant.parse("2026-10-23T10:00:00Z"),
            ),
        )
        return id
    }

    @Test
    fun `an own JPEG is found with a readable path`() {
        val sub = newUser()
        val storage = FileStorage(FilesSettings(dir = dir.toString()))
        val bytes = jpeg()
        val id = store(sub, "photo.jpg", "image/jpeg", bytes, storage)

        val image = FileCabinetService(files, storage).image(sub, id)!!

        assertEquals(id, image.id)
        assertEquals("photo.jpg", image.name)
        assertEquals("image/jpeg", image.contentType)
        assertEquals(bytes.size.toLong(), image.sizeBytes)
        assertEquals(bytes.toList(), Files.readAllBytes(image.path).toList())
    }

    @Test
    fun `another user's JPEG gives null`() {
        val owner = newUser()
        val other = newUser()
        val storage = FileStorage(FilesSettings(dir = dir.toString()))
        val id = store(owner, "photo.jpg", "image/jpeg", jpeg(), storage)

        assertNull(FileCabinetService(files, storage).image(other, id))
    }

    @Test
    fun `a PNG named photo jpg gives null`() {
        val sub = newUser()
        val storage = FileStorage(FilesSettings(dir = dir.toString()))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val id = store(sub, "x.jpg", "image/jpeg", png, storage)

        assertNull(FileCabinetService(files, storage).image(sub, id))
    }

    @Test
    fun `a PDF gives null`() {
        val sub = newUser()
        val storage = FileStorage(FilesSettings(dir = dir.toString()))
        val id = store(sub, "cv.pdf", "application/pdf", "%PDF-1.4\n% synthetic\n".toByteArray(), storage)

        assertNull(FileCabinetService(files, storage).image(sub, id))
    }

    @Test
    fun `a JPEG over 6 MB gives null and one of exactly 6 MB is found`() {
        val sub = newUser()
        val storage = FileStorage(FilesSettings(dir = dir.toString()))
        val limit = 6 * 1024 * 1024
        val marker = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val atLimit = marker + ByteArray(limit - marker.size)
        val overLimit = marker + ByteArray(limit + 1 - marker.size)
        val okId = store(sub, "exact.jpg", "image/jpeg", atLimit, storage)
        val bigId = store(sub, "big.jpg", "image/jpeg", overLimit, storage)

        assertEquals(limit.toLong(), FileCabinetService(files, storage).image(sub, okId)!!.sizeBytes)
        assertNull(FileCabinetService(files, storage).image(sub, bigId))
    }

    @Test
    fun `an unknown id gives null`() {
        val sub = newUser()
        val storage = FileStorage(FilesSettings(dir = dir.toString()))

        assertNull(FileCabinetService(files, storage).image(sub, UUID.randomUUID()))
    }
}
