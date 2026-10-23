package net.s4v3.backend

import net.s4v3.backend.files.internal.FileStorage
import net.s4v3.backend.files.internal.FilesSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileStorageTests {

    @TempDir
    lateinit var dir: Path

    private fun storage() = FileStorage(FilesSettings(dir = dir.toString()))

    private fun tempFile(bytes: ByteArray): Path {
        val temp = Files.createDirectories(dir.resolve("tmp")).resolve("upload-" + UUID.randomUUID())
        return Files.write(temp, bytes)
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `move keeps the bytes and reports size and hash`() {
        val storage = storage()
        val bytes = "hello, cabinet".toByteArray()
        val temp = tempFile(bytes)

        val stored = storage.moveIn(temp, UUID.randomUUID())

        assertEquals(bytes.size.toLong(), stored.sizeBytes)
        assertEquals(sha256(bytes), stored.sha256)
        assertContentEquals(bytes, Files.readAllBytes(storage.open(stored.key)))
        assertFalse(Files.exists(temp))
    }

    @Test
    fun `the key is the first two hex characters and the id`() {
        val id = UUID.fromString("0a000000-0000-4000-8000-000000000001")

        val stored = storage().moveIn(tempFile("x".toByteArray()), id)

        assertEquals("0a/0a000000-0000-4000-8000-000000000001", stored.key)
        assertTrue(Files.exists(dir.resolve("0a").resolve(id.toString())))
    }

    @Test
    fun `a key that is not of the storage shape is rejected`() {
        val storage = storage()
        val stored = storage.moveIn(tempFile("x".toByteArray()), UUID.randomUUID())

        assertFailsWith<IllegalArgumentException> { storage.open("../x") }
        assertFailsWith<IllegalArgumentException> { storage.open("../../etc/passwd") }
        assertFailsWith<IllegalArgumentException> { storage.open("/etc/passwd") }
        assertFailsWith<IllegalArgumentException> { storage.open(stored.key + "/..") }
        assertFailsWith<IllegalArgumentException> { storage.delete("../" + stored.key) }
        assertFailsWith<IllegalArgumentException> { storage.delete("") }
        assertTrue(Files.exists(storage.open(stored.key)))
    }

    @Test
    fun `delete twice is fine`() {
        val storage = storage()
        val stored = storage.moveIn(tempFile("x".toByteArray()), UUID.randomUUID())

        storage.delete(stored.key)
        storage.delete(stored.key)

        assertFalse(Files.exists(storage.open(stored.key)))
    }

    @Test
    fun `files in two sub-directories are listed`() {
        val storage = storage()
        val first = storage.moveIn(tempFile("a".toByteArray()), UUID.fromString("0a000000-0000-4000-8000-000000000001"))
        val second = storage.moveIn(tempFile("b".toByteArray()), UUID.fromString("f3000000-0000-4000-8000-000000000002"))
        tempFile("left over".toByteArray())

        assertEquals(setOf(first.key, second.key), storage.keys().toSet())
    }
}
