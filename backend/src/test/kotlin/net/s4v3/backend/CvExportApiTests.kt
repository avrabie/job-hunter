package net.s4v3.backend

import net.s4v3.backend.cv.export.internal.RenderGuard
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import com.jayway.jsonpath.JsonPath
import org.apache.pdfbox.pdmodel.PDResources
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The export endpoint end to end with synthetic profiles (an invented person). */
@ApiTest
@ContextConfiguration(initializers = [MockIssuerInitializer::class, FilesVolumeInitializer::class])
internal class CvExportApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val guard: RenderGuard,
) {

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "export-" + UUID.randomUUID()

    private fun profileJson(
        name: String? = "Ada Example",
        extra: String = "",
    ): String {
        val contact = if (name == null) "{}" else """{"fullName":"$name","headline":"Senior Kotlin Engineer","email":"ada@example.org"}"""
        return """
            {"contact":$contact,"workAuthorisation":"PERMIT_B","summary":"Builds backends.",
             "experience":[{"employer":"Employer AG","title":"Engineer","start":"2021-03","achievements":["Built the thing in français"]}],
             "skills":[{"name":"Kotlin","level":"EXPERT"}],
             "preferences":{"targetRoles":["Secret Role"],"cantons":["ZH"],"minSalaryChf":150000}$extra}
        """.trimIndent()
    }

    private fun save(sub: String, profile: String = profileJson(), version: Int = 0) {
        mvc.perform(
            put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":$version,"profile":$profile}"""),
        ).andExpect(status().isOk)
    }

    private fun export(sub: String, body: String): ResultActions =
        mvc.perform(
            post("/api/cv/export").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(body),
        )

    private fun request(
        format: String = "MARKDOWN",
        template: String = "CLASSIC",
        language: String = "EN",
        more: String = "",
    ) = """{"format":"$format","template":"$template","language":"$language"$more}"""

    private fun text(result: MvcResult) = result.response.contentAsString

    private fun pdfText(result: MvcResult): String =
        Loader.loadPDF(result.response.contentAsByteArray).use { PDFTextStripper().getText(it) }

    @Test
    fun `exports a PDF with the name and the right headers`() {
        val sub = newSub()
        save(sub)

        val result = export(sub, request("PDF", "MODERN", "DE")).andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"ada-example-cv-de.pdf\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()

        assertEquals("%PDF", String(result.response.contentAsByteArray.copyOfRange(0, 4), Charsets.US_ASCII))
        assertTrue("Ada Example" in pdfText(result))
    }

    @Test
    fun `exports Markdown and HTML with the same sections in the same order`() {
        val sub = newSub()
        save(sub)

        val md = export(sub, request("MARKDOWN")).andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/markdown;charset=utf-8"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"ada-example-cv-en.md\""))
            .andReturn()
        val html = export(sub, request("HTML")).andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/html;charset=utf-8"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"ada-example-cv-en.html\""))
            .andReturn()

        listOf(text(md), text(html)).forEach { body ->
            assertTrue("Ada Example" in body)
            val at = listOf("Profile", "Experience", "Skills").map { body.indexOf(it) }
            assertTrue(at.all { it >= 0 } && at == at.sorted(), "sections out of order: $at")
        }
    }

    @Test
    fun `an export does not change the stored profile or its version`() {
        val sub = newSub()
        save(sub)
        val before = mvc.perform(get("/api/profile").header("Authorization", bearer(sub))).andReturn().response.contentAsString

        export(sub, request("HTML")).andExpect(status().isOk)

        val after = mvc.perform(get("/api/profile").header("Authorization", bearer(sub))).andReturn().response.contentAsString
        assertEquals(before, after)
    }

    @Test
    fun `another user's profile is unreachable`() {
        val a = newSub()
        val b = newSub()
        save(a, profileJson("Alice Alpha"))
        save(b, profileJson("Bob Beta"))

        val body = text(export(a, request()).andExpect(status().isOk).andReturn())

        assertTrue("Alice Alpha" in body)
        assertFalse("Bob Beta" in body)
    }

    @Test
    fun `anonymous calls get 401`() {
        mvc.perform(post("/api/cv/export").contentType(MediaType.APPLICATION_JSON).content(request()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a bad format, an unknown field or a body that is not JSON is 400 invalid_request`() {
        val sub = newSub()
        save(sub)

        listOf(
            request(format = "DOCX"),
            request(more = ""","extra":1"""),
            request(more = ""","sections":["NOPE"]"""),
            request(more = ""","sections":[null]"""),
            request(more = ""","includeAnalysis":null"""),
            """{"template":"CLASSIC","language":"EN"}""",
            "not json",
            "",
        ).forEach { body ->
            export(sub, body).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_request"))
        }
    }

    @Test
    fun `a profile in the request is refused with 400 invalid_request`() {
        val sub = newSub()
        save(sub)

        export(sub, request(more = ""","profile":{"contact":{"fullName":"Mallory Mallet"}}"""))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_request"))
    }

    @Test
    fun `a saved profile without a name answers 409 name_required`() {
        val sub = newSub()
        save(sub, profileJson(name = null))

        export(sub, request("PDF")).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("name_required"))
    }

    @Test
    fun `a user who never saved a profile answers 409 name_required`() {
        export(newSub(), request()).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("name_required"))
    }

    @Test
    fun `sections select and order, and private data is never printed`() {
        val sub = newSub()
        save(sub)

        val body = text(export(sub, request(more = ""","sections":["SKILLS","EXPERIENCE"]""")).andExpect(status().isOk).andReturn())
        assertTrue(body.indexOf("Skills") in 0 until body.indexOf("Experience"))
        assertFalse("Profile\n" in body.substringAfter("Ada Example"), "summary section is off")

        listOf("MARKDOWN", "HTML").forEach { format ->
            val all = text(export(sub, request(format)).andExpect(status().isOk).andReturn())
            listOf("150000", "Secret Role", "ZH").forEach { assertFalse(it in all, "$it printed in $format") }
        }
        val pdf = pdfText(export(sub, request("PDF")).andExpect(status().isOk).andReturn())
        listOf("150000", "Secret Role").forEach { assertFalse(it in pdf, "$it printed in PDF") }
    }

    @Test
    fun `open source analysis and work authorisation follow the options`() {
        val sub = newSub()
        val project = """
            ,"openSourceProjects":[{"provider":"GITHUB","fullName":"example/lib","name":"lib","url":"https://github.com/example/lib",
              "description":"A small library","analysis":{"summary":"ANALYSISSUMMARY","architecture":"One module.",
              "techStack":[{"name":"Spring Boot","category":"FRAMEWORK","evidencePath":"build.gradle.kts","evidence":"plugin"}],
              "files":["build.gradle.kts"],"truncated":false,"analyzedAt":"2026-10-24T12:00:00Z"}}]
        """.trimIndent()
        save(sub, profileJson(extra = project))

        val with = text(export(sub, request(more = ""","includeAnalysis":true""")).andExpect(status().isOk).andReturn())
        val without = text(export(sub, request(more = ""","includeWorkAuthorisation":false""")).andExpect(status().isOk).andReturn())
        val off = text(export(sub, request(more = ""","includeOpenSource":false""")).andExpect(status().isOk).andReturn())

        assertTrue("ANALYSISSUMMARY" in with && "Spring Boot" in with)
        assertFalse("ANALYSISSUMMARY" in without)
        assertTrue("A small library" in without)
        assertTrue("permit B" in with)
        assertFalse("permit B" in without)
        assertFalse("example/lib" in off || "Open source" in off)
    }

    @Test
    fun `the hourly limit answers 429 too_many_exports and is per user`() {
        val sub = newSub()
        save(sub)

        repeat(6) { export(sub, request()).andExpect(status().isOk) }
        export(sub, request("PDF")).andExpect(status().isTooManyRequests).andExpect(jsonPath("$.error").value("too_many_exports"))

        val other = newSub()
        save(other)
        export(other, request()).andExpect(status().isOk)
    }

    @Test
    fun `a third PDF render that cannot start in time answers 503 busy`() {
        val sub = newSub()
        save(sub)

        guard.permits.acquire(2)
        try {
            export(sub, request("PDF")).andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.error").value("busy"))
            // Only PDF is guarded.
            export(sub, request("HTML")).andExpect(status().isOk)
        } finally {
            guard.permits.release(2)
        }
        export(sub, request("PDF")).andExpect(status().isOk)
    }

    @Test
    fun `profile text never reaches the logs`() {
        val sub = newSub()
        val events = captureAppLogs {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            save(sub, profileJson(name = "Zed Logmarker4417", extra = ""))
            export(sub, request("PDF")).andExpect(status().isOk)
            export(sub, request(format = "DOCX", more = ""","note":"BODYMARKER4417"""")).andExpect(status().isBadRequest)
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val logged = events.filter { e -> frameworkBodyPrinters.none { e.loggerName.startsWith(it) } }
            .joinToString("\n") { e -> e.fullText() }
        assertTrue("capture alive" in logged, "the capture does not work, so it proves nothing")
        assertTrue("CV exported" in logged, "our own outcome line is expected")
        listOf("Logmarker4417", "BODYMARKER4417", "Built the thing", "Builds backends", "ada@example.org").forEach {
            assertFalse(it in logged, "$it appears in the logs")
        }
    }

    // ---- Professional template with a photo ----

    /** A synthetic image drawn here (never a real face), encoded as [format]. */
    private fun image(format: String): ByteArray {
        val img = BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(40, 90, 160)
        g.fillRect(0, 0, 120, 90)
        g.color = Color(230, 170, 60)
        g.fillOval(30, 15, 60, 60)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()
    }

    private fun upload(sub: String, name: String, bytes: ByteArray, type: String = "image/jpeg"): String {
        val result = mvc.perform(
            multipart("/api/files").file(MockMultipartFile("file", name, type, bytes)).header("Authorization", bearer(sub)),
        ).andExpect(status().isOk).andReturn()
        return JsonPath.read(result.response.contentAsString, "$.id")
    }

    private fun photoRequest(id: String, format: String = "PDF", template: String = "PROFESSIONAL") =
        request(format, template, "EN", ""","photoFileId":"$id"""")

    private fun imageCount(result: MvcResult): Int = Loader.loadPDF(result.response.contentAsByteArray).use { d ->
        d.pages.sumOf { page ->
            val res: PDResources = page.resources
            res.xObjectNames.count { res.isImageXObject(it) }
        }
    }

    @Test
    fun `a professional PDF with the caller's own JPEG holds one image and the footer`() {
        val sub = newSub()
        save(sub)
        val id = upload(sub, "me.jpg", image("jpg"))

        val result = export(sub, photoRequest(id)).andExpect(status().isOk).andReturn()

        assertEquals(1, imageCount(result))
        assertTrue("Ada Example · Senior Kotlin Engineer" in pdfText(result))
        val html = text(export(sub, photoRequest(id, "HTML")).andExpect(status().isOk).andReturn())
        assertEquals(1, Regex("data:image/jpeg;base64,").findAll(html).count())
        assertTrue("img-src data:" in html)
    }

    @Test
    fun `a professional PDF without a photo has no image`() {
        val sub = newSub()
        save(sub)
        assertEquals(0, imageCount(export(sub, request("PDF", "PROFESSIONAL")).andExpect(status().isOk).andReturn()))
    }

    @Test
    fun `someone else's file and an unknown id answer the same 400 photo_invalid`() {
        val owner = newSub()
        val sub = newSub()
        save(sub)
        val foreign = upload(owner, "owner.jpg", image("jpg"))

        val a = export(sub, photoRequest(foreign)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("photo_invalid")).andReturn().response.contentAsString
        val b = export(sub, photoRequest(UUID.randomUUID().toString())).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertEquals(a, b)
    }

    @Test
    fun `a PNG named jpg is 400 photo_invalid`() {
        val sub = newSub()
        save(sub)
        val id = upload(sub, "fake.jpg", image("png"))

        export(sub, photoRequest(id)).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("photo_invalid"))
        export(sub, photoRequest(id, "HTML")).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("photo_invalid"))
    }

    @Test
    fun `the photo is ignored with CLASSIC and with Markdown, even when the id is bad`() {
        val sub = newSub()
        save(sub)
        val bogus = UUID.randomUUID().toString()
        val png = upload(sub, "fake.jpg", image("png"))

        assertEquals(0, imageCount(export(sub, photoRequest(png, "PDF", "CLASSIC")).andExpect(status().isOk).andReturn()))
        export(sub, photoRequest(bogus, "MARKDOWN", "PROFESSIONAL")).andExpect(status().isOk)
        val jpg = upload(sub, "me.jpg", image("jpg"))
        val html = text(export(sub, photoRequest(jpg, "HTML", "CLASSIC")).andExpect(status().isOk).andReturn())
        assertFalse("data:image" in html)
    }

    @Test
    fun `a refused photo still counts against the hourly limit`() {
        val sub = newSub()
        save(sub)
        val bogus = UUID.randomUUID().toString()

        repeat(6) { export(sub, photoRequest(bogus)).andExpect(status().isBadRequest) }
        export(sub, photoRequest(bogus)).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `the photo, its name and its id never reach the logs`() {
        val sub = newSub()
        save(sub)
        val id = upload(sub, "PHOTOMARK8841.jpg", image("jpg"))
        val png = upload(sub, "PNGMARK8841.jpg", image("png"))

        val events = captureAppLogs {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            export(sub, photoRequest(id)).andExpect(status().isOk)
            export(sub, photoRequest(png)).andExpect(status().isBadRequest)
            export(sub, photoRequest(UUID.randomUUID().toString())).andExpect(status().isBadRequest)
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val logged = events.filter { e -> frameworkBodyPrinters.none { it.let { p -> e.loggerName.startsWith(p) } } }
            .joinToString("\n") { e -> e.fullText() }
        assertTrue("capture alive" in logged)
        assertTrue("CV exported" in logged)
        listOf("MARK8841", id, png, "base64").forEach { assertFalse(it in logged, "$it appears in the logs") }
    }
}
