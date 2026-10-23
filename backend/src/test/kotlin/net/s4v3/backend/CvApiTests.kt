package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@ApiTest
internal class CvApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "cv-api-" + UUID.randomUUID()

    private val pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".toByteArray()

    private fun upload(sub: String, name: String, bytes: ByteArray, type: String = "application/octet-stream"): ResultActions =
        mvc.perform(multipart("/api/cv").file(MockMultipartFile("file", name, type, bytes)).header("Authorization", bearer(sub)))

    private fun info(sub: String) = mvc.perform(get("/api/cv").header("Authorization", bearer(sub)))
    private fun download(sub: String) = mvc.perform(get("/api/cv/file").header("Authorization", bearer(sub)))
    private fun remove(sub: String) = mvc.perform(delete("/api/cv").header("Authorization", bearer(sub)))

    @Test
    fun `every endpoint needs a token`() {
        mvc.perform(get("/api/cv")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/cv/file")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/cv")).andExpect(status().isUnauthorized)
        mvc.perform(multipart("/api/cv").file(MockMultipartFile("file", "cv.txt", "text/plain", "x".toByteArray())))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `upload then details hold name, type, size and time but no content`() {
        val sub = newSub()
        info(sub).andExpect(status().isNotFound)

        upload(sub, "Jane CV.pdf", pdf).andExpect(status().isOk)

        info(sub).andExpect(status().isOk)
            .andExpect(jsonPath("$.fileName").value("Jane CV.pdf"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.sizeBytes").value(pdf.size))
            .andExpect(jsonPath("$.uploadedAt").exists())
            .andExpect(jsonPath("$.content").doesNotExist())
    }

    @Test
    fun `each accepted kind gets its content type from the content and name`() {
        val sub = newSub()
        upload(sub, "cv.pdf", pdf).andExpect(jsonPath("$.contentType").value("application/pdf"))
        upload(sub, "cv.md", "# Jane\n\n- Kotlin".toByteArray()).andExpect(jsonPath("$.contentType").value("text/markdown"))
        upload(sub, "cv.txt", "Jane, engineer — Zürich".toByteArray()).andExpect(jsonPath("$.contentType").value("text/plain"))
    }

    @Test
    fun `a new upload replaces the old file`() {
        val sub = newSub()
        upload(sub, "first.txt", "first".toByteArray()).andExpect(status().isOk)
        upload(sub, "second.md", "# second".toByteArray()).andExpect(status().isOk)

        info(sub).andExpect(jsonPath("$.fileName").value("second.md"))
        assertEquals(1, jdbc.queryForObject("select count(*) from cv_document where owner_sub = ?", Int::class.java, sub))
        assertContentEquals("# second".toByteArray(), download(sub).andReturn().response.contentAsByteArray)
    }

    @Test
    fun `content that does not match is refused and an earlier CV stays`() {
        val sub = newSub()
        upload(sub, "keep.txt", "keep me".toByteArray()).andExpect(status().isOk)

        upload(sub, "cv.pdf", "this is not a pdf".toByteArray()).andExpect(status().isBadRequest)
        upload(sub, "cv.txt", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01)).andExpect(status().isBadRequest)
        upload(sub, "cv.txt", byteArrayOf(0xC3.toByte(), 0x28)).andExpect(status().isBadRequest)
        upload(sub, "cv.docx", "text".toByteArray()).andExpect(status().isBadRequest)
        upload(sub, "cv.txt", ByteArray(0)).andExpect(status().isBadRequest)
        upload(sub, "cv.exe", pdf, "application/pdf").andExpect(status().isBadRequest)

        info(sub).andExpect(jsonPath("$.fileName").value("keep.txt"))
    }

    @Test
    fun `the error body is a fixed text and never repeats the name or the content`() {
        val sub = newSub()

        val body = upload(sub, "SECRETNAME4417.pdf", "SECRETCONTENT4417".toByteArray())
            .andExpect(status().isBadRequest).andReturn().response.contentAsString

        assertFalse("SECRETNAME4417" in body || "SECRETCONTENT4417" in body)
    }

    @Test
    fun `a file over 5 MB is refused with 413 and an earlier CV stays`() {
        val sub = newSub()
        upload(sub, "keep.txt", "keep me".toByteArray()).andExpect(status().isOk)

        upload(sub, "big.txt", ByteArray(5 * 1024 * 1024 + 1) { 'a'.code.toByte() }).andExpect(status().isPayloadTooLarge)

        info(sub).andExpect(jsonPath("$.fileName").value("keep.txt"))
    }

    @Test
    fun `the stored name is for display only`() {
        val sub = newSub()

        upload(sub, "../../etc/pass\u0000wd\n.txt", "x".toByteArray()).andExpect(status().isOk)
            .andExpect(jsonPath("$.fileName").value("passwd.txt"))
        upload(sub, "C:\\docs\\my<cv>.md", "x".toByteArray()).andExpect(jsonPath("$.fileName").value("my_cv_.md"))
    }

    @Test
    fun `download returns the same bytes as an attachment`() {
        val sub = newSub()
        val bytes = "Lebenslauf — Müller".toByteArray()
        upload(sub, "Lebenslauf.txt", bytes).andExpect(status().isOk)

        val result = download(sub).andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(content().contentTypeCompatibleWith("text/plain")).andReturn()

        assertContentEquals(bytes, result.response.contentAsByteArray)
    }

    @Test
    fun `delete removes the CV and is fine when there is none`() {
        val sub = newSub()
        remove(sub).andExpect(status().isNoContent)
        upload(sub, "cv.txt", "x".toByteArray()).andExpect(status().isOk)

        remove(sub).andExpect(status().isNoContent)

        info(sub).andExpect(status().isNotFound)
        download(sub).andExpect(status().isNotFound)
    }

    @Test
    fun `user A cannot see, download, replace or delete user B's CV`() {
        val a = newSub()
        val b = newSub()
        upload(a, "a.txt", "AAA".toByteArray()).andExpect(status().isOk)
        upload(b, "b.txt", "BBB".toByteArray()).andExpect(status().isOk)

        info(a).andExpect(jsonPath("$.fileName").value("a.txt"))
        assertContentEquals("AAA".toByteArray(), download(a).andReturn().response.contentAsByteArray)
        upload(a, "a2.txt", "AAA2".toByteArray()).andExpect(status().isOk)
        remove(a).andExpect(status().isNoContent)

        info(b).andExpect(jsonPath("$.fileName").value("b.txt"))
        assertContentEquals("BBB".toByteArray(), download(b).andReturn().response.contentAsByteArray)
    }
}
