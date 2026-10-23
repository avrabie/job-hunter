package net.s4v3.backend.files.internal

import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Where a file's bytes are, how many there are and their SHA-256 (lowercase hex). */
internal data class Stored(val key: String, val sizeBytes: Long, val sha256: String)

/**
 * The only class that touches the files volume. A file lives at `<dir>/<first two hex of id>/<id>` and its key is that
 * path relative to `dir`. A key is made from a generated UUID, never from a user-provided name. [open] and [delete] accept
 * only keys of exactly that shape, so a key such as `../x` or `/etc/passwd` is refused before any path is built.
 */
@Component
internal class FileStorage(settings: FilesSettings) {

    private val root: Path = Path.of(settings.dir)

    /** Reads `temp` once for its size and SHA-256, then moves it into place (a rename on the same volume). */
    fun moveIn(temp: Path, id: UUID): Stored {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            Files.newInputStream(temp).use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    size += read
                }
            }
            val key = keyOf(id)
            val target = pathOf(key)
            Files.createDirectories(target.parent)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            return Stored(key, size, digest.digest().joinToString("") { "%02x".format(it) })
        } catch (e: IOException) {
            Files.deleteIfExists(temp)
            throw e
        }
    }

    /** The path of a stored file. Refuses any key that is not of the storage shape. */
    fun open(key: String): Path = pathOf(checked(key))

    /** Removes a stored file; a file that is already gone is not an error. */
    fun delete(key: String) {
        Files.deleteIfExists(pathOf(checked(key)))
    }

    /** Every stored key on the volume. The temporary directory and anything else that does not have the key shape is skipped. */
    fun keys(): Sequence<String> {
        if (!Files.isDirectory(root)) return emptySequence()
        return Files.walk(root, 2).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { root.relativize(it).joinToString("/") }
                .filter { isKey(it) }
                .toList()
        }.asSequence()
    }

    /** Usable bytes on the volume, measured at the nearest directory that exists. */
    fun usableSpace(): Long {
        val existing = generateSequence(root) { it.parent }.first { Files.exists(it) }
        return Files.getFileStore(existing).usableSpace
    }

    private fun keyOf(id: UUID): String {
        val text = id.toString()
        return text.substring(0, 2) + "/" + text
    }

    private fun isKey(key: String) = KEY.matches(key) && key.substring(0, 2) == key.substring(3, 5)

    private fun checked(key: String): String {
        require(isKey(key)) { "Not a storage key" }
        return key
    }

    private fun pathOf(key: String): Path {
        val path = root.resolve(key).normalize()
        require(path.startsWith(root.normalize())) { "Not a storage key" }
        return path
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        val KEY = Regex("[0-9a-f]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
