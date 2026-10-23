package net.s4v3.backend

import net.s4v3.backend.files.internal.CvContent
import net.s4v3.backend.files.internal.CvRejected
import net.s4v3.backend.files.internal.CvRejection
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CvContentTests {

    private val pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".toByteArray()

    private fun rejection(name: String?, bytes: ByteArray): CvRejection =
        assertFailsWith<CvRejected> { CvContent.accept(name, bytes) }.reason

    @Test
    fun `each accepted kind gets its content type from the content and name`() {
        assertEquals("application/pdf", CvContent.accept("cv.pdf", pdf).contentType)
        assertEquals("text/markdown", CvContent.accept("cv.md", "# Jane\n\n- Kotlin".toByteArray()).contentType)
        assertEquals("text/plain", CvContent.accept("cv.txt", "Jane, engineer — Zürich".toByteArray()).contentType)
    }

    @Test
    fun `the accepted bytes are the given bytes`() {
        val bytes = "# second".toByteArray()

        assertContentEquals(bytes, CvContent.accept("second.md", bytes).bytes)
    }

    @Test
    fun `content that does not match the name is refused`() {
        assertEquals(CvRejection.WRONG_TYPE, rejection("cv.pdf", "this is not a pdf".toByteArray()))
        assertEquals(CvRejection.WRONG_TYPE, rejection("cv.txt", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01)))
        assertEquals(CvRejection.WRONG_TYPE, rejection("cv.txt", byteArrayOf(0xC3.toByte(), 0x28)))
        assertEquals(CvRejection.WRONG_TYPE, rejection("cv.docx", "text".toByteArray()))
        assertEquals(CvRejection.WRONG_TYPE, rejection("cv.txt", ByteArray(0)))
        assertEquals(CvRejection.WRONG_TYPE, rejection("cv.exe", pdf))
        assertEquals(CvRejection.WRONG_TYPE, rejection(null, pdf))
    }

    @Test
    fun `a file over 5 MB is too large`() {
        assertEquals(CvRejection.TOO_LARGE, rejection("big.txt", ByteArray(5 * 1024 * 1024 + 1) { 'a'.code.toByte() }))
    }

    @Test
    fun `a file of exactly 5 MB is accepted`() {
        CvContent.accept("big.txt", ByteArray(5 * 1024 * 1024) { 'a'.code.toByte() })
    }

    @Test
    fun `the stored name is for display only`() {
        assertEquals("passwd.txt", CvContent.accept("../../etc/pass\u0000wd\n.txt", "x".toByteArray()).displayName)
        assertEquals("my_cv_.md", CvContent.displayName("C:\\docs\\my<cv>.md"))
        assertEquals("cv", CvContent.displayName("   "))
        assertEquals("cv", CvContent.displayName(null))
    }

    @Test
    fun `the name of any file is cut to a safe display name`() {
        assertEquals("passwd", CvContent.displayNameOnly("../../etc/passwd"))
        assertEquals("my file_.zip", CvContent.displayNameOnly("my file?.zip"))
        assertEquals("a".repeat(100), CvContent.displayNameOnly("a".repeat(150)))
    }
}
