package net.s4v3.backend

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val photoIpCounter = AtomicInteger(1)

/** The public photo (Professional template) end to end. Photos are drawn here, never a real face; the person is invented. */
@ApiTest
@ContextConfiguration(initializers = [MockIssuerInitializer::class, FilesVolumeInitializer::class])
internal class PublicCvPhotoApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newHandle() = "ada-" + UUID.randomUUID().toString().replace("-", "").take(10)
    private fun newIp() = photoIpCounter.getAndIncrement().let { "203.0.${it / 250}.${it % 250 + 1}" }

    private fun user(): String {
        val sub = "pcv-photo-" + UUID.randomUUID()
        mvc.perform(
            put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(
                """{"version":0,"profile":{"contact":{"fullName":"Ada Example","headline":"Engineer"},"summary":"Builds backends."}}""",
            ),
        ).andExpect(status().isOk)
        return sub
    }

    private fun jpeg(color: Color): ByteArray {
        val img = BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, 120, 90)
        g.color = Color(230, 170, 60)
        g.fillOval(30, 15, 60, 60)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    private fun upload(sub: String, name: String, bytes: ByteArray): String {
        val result = mvc.perform(
            multipart("/api/files").file(MockMultipartFile("file", name, "image/jpeg", bytes)).header("Authorization", bearer(sub)),
        ).andExpect(status().isOk).andReturn()
        return JsonPath.read(result.response.contentAsString, "$.id")
    }

    private fun settings(photo: String?, template: String = "PROFESSIONAL") =
        """{"template":"$template"${if (photo == null) "" else ""","photoFileId":"$photo""""}}"""

    private fun putCv(sub: String, handle: String, settings: String, published: Boolean = true): ResultActions =
        mvc.perform(
            put("/api/cv/public").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"handle":"$handle","published":$published,"settings":$settings}"""),
        )

    private fun page(handle: String, ip: String = newIp()): MvcResult =
        mvc.perform(get("/api/public/cv/$handle").header("X-Forwarded-For", ip)).andReturn()

    private fun photo(handle: String, ip: String = newIp(), method: HttpMethod = HttpMethod.GET): MvcResult =
        mvc.perform(request(method, "/api/public/cv/$handle/photo").header("X-Forwarded-For", ip)).andReturn()

    private fun hasPhoto(html: String) = "data:image/jpeg;base64," in html

    /** A published PROFESSIONAL page with a fresh photo; returns owner, handle and file id. */
    private fun published(): Triple<String, String, String> {
        val sub = user()
        val id = upload(sub, "me.jpg", jpeg(Color(40, 90, 160)))
        val handle = newHandle()
        putCv(sub, handle, settings(id)).andExpect(status().isOk)
        return Triple(sub, handle, id)
    }

    @Test
    fun `no photo by default and the route answers 404`() {
        val sub = user()
        val handle = newHandle()
        putCv(sub, handle, settings(null)).andExpect(status().isOk)

        assertFalse(hasPhoto(page(handle).response.contentAsString))
        assertEquals(404, photo(handle).response.status)
    }

    @Test
    fun `the page embeds the photo and the route serves it with the safe headers`() {
        val (_, handle, _) = published()

        val html = page(handle).response.contentAsString
        assertTrue(hasPhoto(html))
        assertTrue("img-src data:" in html)

        val r = photo(handle).response
        assertEquals(200, r.status)
        assertEquals("image/jpeg", r.contentType)
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"))
        assertEquals("default-src 'none'; sandbox", r.getHeader("Content-Security-Policy"))
        assertEquals("inline; filename=\"photo.jpg\"", r.getHeader("Content-Disposition"))
        assertEquals("no-cache", r.getHeader("Cache-Control"))
        val etag = r.getHeader("ETag")!!
        assertTrue(etag.startsWith("\"") && etag.endsWith("\""))
        val bytes = r.contentAsByteArray
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertEquals(320, ImageIO.read(bytes.inputStream()).width)

        val again = mvc.perform(
            get("/api/public/cv/$handle/photo").header("X-Forwarded-For", newIp()).header("If-None-Match", etag),
        ).andReturn().response
        assertEquals(304, again.status)
    }

    @Test
    fun `HEAD answers like GET without a body`() {
        val (_, handle, _) = published()

        val head = photo(handle, method = HttpMethod.HEAD).response

        assertEquals(200, head.status)
        assertEquals("image/jpeg", head.getHeader("Content-Type"))
        assertEquals(0, head.contentAsByteArray.size)
        val missing = photo(newHandle(), method = HttpMethod.HEAD).response
        assertEquals(404, missing.status)
    }

    @Test
    fun `someone else's file is 400 photo_invalid and nothing is saved`() {
        val owner = user()
        val foreign = upload(owner, "owner.jpg", jpeg(Color.GRAY))
        val sub = user()
        val handle = newHandle()

        putCv(sub, handle, settings(foreign)).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("photo_invalid"))
        putCv(sub, handle, settings(UUID.randomUUID().toString())).andExpect(status().isBadRequest)

        mvc.perform(get("/api/cv/public").header("Authorization", bearer(sub))).andExpect(jsonPath("$.configured").value(false))
        assertEquals(0, jdbc.queryForObject("select count(*) from public_cv where owner_sub = ?", Int::class.java, sub))
    }

    @Test
    fun `a deleted file gives a page without a photo and a 404 route`() {
        val (sub, handle, id) = published()
        assertEquals(200, photo(handle).response.status)

        mvc.perform(delete("/api/files/$id").header("Authorization", bearer(sub))).andExpect(status().isNoContent)

        val html = page(handle)
        assertEquals(200, html.response.status)
        assertFalse(hasPhoto(html.response.contentAsString))
        assertEquals(404, photo(handle).response.status)
    }

    @Test
    fun `another template shows no photo, switching back does`() {
        val (sub, handle, id) = published()
        putCv(sub, handle, settings(id, "CLASSIC")).andExpect(status().isOk)

        assertFalse(hasPhoto(page(handle).response.contentAsString))
        assertEquals(404, photo(handle).response.status)

        putCv(sub, handle, settings(id)).andExpect(status().isOk)
        assertTrue(hasPhoto(page(handle).response.contentAsString))
    }

    @Test
    fun `every case that is not served answers the identical 404`() {
        val (pausedSub, paused, _) = published()
        putCv(pausedSub, paused, settings(upload(pausedSub, "b.jpg", jpeg(Color.RED))), published = false).andExpect(status().isOk)
        val (_, locked, _) = published()
        mvc.perform(
            delete("/api/admin/public-cv/$locked")
                .header("Authorization", "Bearer " + issuer.token("admin-" + UUID.randomUUID(), roles = listOf("USER", "ADMIN"))),
        ).andExpect(status().isNoContent)
        val sub = user()
        val noPhoto = newHandle().also { putCv(sub, it, settings(null)).andExpect(status().isOk) }
        val classicSub = user()
        val classicId = upload(classicSub, "c.jpg", jpeg(Color.BLUE))
        val classic = newHandle().also { putCv(classicSub, it, settings(classicId, "CLASSIC")).andExpect(status().isOk) }
        val (goneSub, gone, goneId) = published()
        mvc.perform(delete("/api/files/$goneId").header("Authorization", bearer(goneSub))).andExpect(status().isNoContent)

        val handles = listOf(newHandle(), paused, locked, noPhoto, classic, gone, "x", "a_b", "a".repeat(80))
        val answers = handles.map { photo(it).also { r -> assertEquals(404, r.response.status, it) } }
        val head = photo(newHandle(), method = HttpMethod.HEAD).response

        val first = answers.first().response
        answers.forEach { a ->
            assertEquals("""{"error":"not_found"}""", a.response.contentAsString)
            assertEquals(first.headerNames.toSet(), a.response.headerNames.toSet())
            first.headerNames.filter { it != "Content-Length" }.forEach { h -> assertEquals(first.getHeader(h), a.response.getHeader(h), h) }
        }
        // The same as the page route's 404, for the same kind of handle.
        val pageMissing = page(newHandle()).response
        assertEquals(pageMissing.headerNames.toSet(), first.headerNames.toSet())
        assertEquals(pageMissing.contentAsString, first.contentAsString)
        assertEquals(0, head.contentAsByteArray.size)
        // Extra segments after the photo segment stay the catch-all 404.
        val deeper = mvc.perform(get("/api/public/cv/$noPhoto/photo/x").header("X-Forwarded-For", newIp())).andReturn().response
        assertEquals(404, deeper.status)
        assertEquals(first.contentAsString, deeper.contentAsString)
    }

    @Test
    fun `the photo route shares the per-address limits with the page`() {
        val (_, handle, _) = published()
        val ip = newIp()
        repeat(10) { assertEquals(404, photo(newHandle(), ip).response.status) }
        assertEquals(429, photo(newHandle(), ip).response.status)

        val other = newIp()
        repeat(30) { assertEquals(200, photo(handle, other).response.status) }
        repeat(30) { assertEquals(200, page(handle, other).response.status) }
        assertEquals(429, photo(handle, other).response.status)
        assertEquals(429, page(handle, other).response.status)
    }

    @Test
    fun `replacing the file changes the page and the photo`() {
        val (sub, handle, _) = published()
        val before = photo(handle).response
        val etagBefore = before.getHeader("ETag")
        val pageBefore = page(handle).response.contentAsString

        val replacement = upload(sub, "new.jpg", jpeg(Color(200, 30, 30)))
        putCv(sub, handle, settings(replacement)).andExpect(status().isOk)

        assertNotEquals(etagBefore, photo(handle).response.getHeader("ETag"))
        assertNotEquals(pageBefore, page(handle).response.contentAsString)
    }

    @Test
    fun `same id with different bytes is a different cache key`() {
        // The key reads size and SHA-256 from the cabinet on every request: a changed hash row is seen at once.
        val (_, handle, id) = published()
        val etagBefore = photo(handle).response.getHeader("ETag")
        jdbc.update("update user_file set sha256 = ?, size_bytes = size_bytes where id = ?::uuid", "f".repeat(64), id)

        // The key changed, so the page is rendered again from the same stored bytes: same picture, same ETag.
        assertEquals(etagBefore, photo(handle).response.getHeader("ETag"))
        assertEquals(200, page(handle).response.status)
    }

    @Test
    fun `the photo and its id are not in the logs or the audit`() {
        val sub = user()
        val id = upload(sub, "PHOTOMARK7731.jpg", jpeg(Color(10, 120, 90)))
        val handle = newHandle()

        val events = captureAppLogs {
            putCv(sub, handle, settings(id)).andExpect(status().isOk)
            page(handle)
            photo(handle)
            photo(handle, method = HttpMethod.HEAD)
            photo(newHandle())
        }

        val logged = events.joinToString("\n") { it.fullText() }
        listOf("PHOTOMARK7731", id, "base64", handle).forEach { assertFalse(it in logged, "$it is in the logs") }
        val flags = jdbc.queryForList("select flags::text from public_cv_audit where owner_sub = ?", String::class.java, sub).joinToString()
        assertTrue("\"photo\": true" in flags, flags)
        assertFalse(id in flags)
        assertFalse("PHOTOMARK" in flags)
    }
}
