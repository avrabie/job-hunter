package net.s4v3.backend.files.internal

import net.s4v3.backend.files.CabinetFile
import net.s4v3.backend.files.FileCabinet
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
internal class FileCabinetService(
    private val files: JdbcFileRepository,
    private val storage: FileStorage,
) : FileCabinet {

    override fun masterCv(sub: String): CabinetFile? {
        val master = files.master(sub) ?: return null
        return CabinetFile(master.id, master.name, master.contentType, master.sizeBytes, master.sha256, storage.open(master.storageKey))
    }

    override fun image(sub: String, id: UUID): CabinetFile? {
        val file = files.find(sub, id) ?: return null
        if (file.sizeBytes > MAX_IMAGE_BYTES) return null
        val path = storage.open(file.storageKey)
        if (!startsWithJpegMarker(path)) return null
        return CabinetFile(file.id, file.name, file.contentType, file.sizeBytes, file.sha256, path)
    }

    /** Reads the first three bytes only. A missing or unreadable file is not a JPEG. */
    private fun startsWithJpegMarker(path: Path): Boolean =
        try {
            Files.newInputStream(path).use { it.readNBytes(JPEG_MARKER.size).contentEquals(JPEG_MARKER) }
        } catch (_: IOException) {
            false
        }

    private companion object {
        const val MAX_IMAGE_BYTES = 6L * 1024 * 1024
        val JPEG_MARKER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }
}
