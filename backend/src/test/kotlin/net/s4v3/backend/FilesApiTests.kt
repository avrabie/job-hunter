package net.s4v3.backend

import com.jayway.jsonpath.JsonPath
import net.s4v3.backend.files.internal.FileStorage
import net.s4v3.backend.files.internal.FilesSettings
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.boot.test.context.TestConfiguration
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/** A clock the test moves by hand, so a ticket expires without waiting. */
class TestClock : Clock() {
    private val now = AtomicReference(Instant.now())
    fun advance(by: Duration) { now.updateAndGet { it.plus(by) } }
    override fun instant(): Instant = now.get()
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}

/** A fresh files volume for each test context, so a test can count what is on disk. */
class FilesVolumeInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        val dir = Files.createTempDirectory("files-api-test-")
        TestPropertyValues.of("jobhunter.files.dir=$dir").applyTo(context)
    }
}

/** The files API over MockMvc with a real token, a temporary volume and the database. The other-file limit is 1 MB here. */
@ApiTest
@ContextConfiguration(initializers = [MockIssuerInitializer::class, FilesVolumeInitializer::class])
@TestPropertySource(properties = ["jobhunter.files.max-other-bytes=1048576"])
@Import(FilesApiTests.TestClockConfig::class)
internal class FilesApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val storage: FileStorage,
    @Autowired private val settings: FilesSettings,
    @Autowired private val clock: TestClock,
) {

    @TestConfiguration
    class TestClockConfig {
        @Bean
        fun testClock() = TestClock()
    }

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "files-api-" + UUID.randomUUID()

    private val pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".toByteArray()
    private val dir: Path get() = Path.of(settings.dir)

    private fun upload(
        sub: String,
        name: String,
        bytes: ByteArray,
        kind: String? = null,
        type: String = "application/octet-stream",
    ): ResultActions {
        val request = multipart("/api/files").file(MockMultipartFile("file", name, type, bytes)).header("Authorization", bearer(sub))
        if (kind != null) request.param("kind", kind)
        return mvc.perform(request)
    }

    private fun idOf(result: ResultActions): String = JsonPath.read(result.andReturn().response.contentAsString, "$.id")

    private fun rows(sub: String): Int =
        jdbc.queryForObject("select count(*) from user_file where owner_sub = ?", Int::class.java, sub)!!

    private fun storedBytes(id: String): ByteArray {
        val key = jdbc.queryForObject("select storage_key from user_file where id = ?::uuid", String::class.java, id)!!
        return Files.readAllBytes(storage.open(key))
    }

    private fun storedFiles(): Int = storage.keys().count()

    private fun tempFiles(): Int = Files.list(dir.resolve("tmp")).use { it.count().toInt() }

    @Test
    fun `every upload needs a token`() {
        mvc.perform(multipart("/api/files").file(MockMultipartFile("file", "cv.txt", "text/plain", "x".toByteArray())))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `upload answers the details without the content`() {
        val sub = newSub()

        upload(sub, "Jane CV.pdf", pdf, kind = "CV", type = "application/pdf")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Jane CV.pdf"))
            .andExpect(jsonPath("$.kind").value("CV"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.sizeBytes").value(pdf.size))
            .andExpect(jsonPath("$.uploadedAt").exists())
            .andExpect(jsonPath("$.master").value(false))
            .andExpect(jsonPath("$.storageKey").doesNotExist())
            .andExpect(jsonPath("$.content").doesNotExist())
    }

    @Test
    fun `an upload without kind is kept as OTHER and its bytes are stored unchanged`() {
        val sub = newSub()
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x7F)

        val result = upload(sub, "references.zip", bytes, type = "application/zip")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("OTHER"))
            .andExpect(jsonPath("$.contentType").value("application/zip"))
            .andExpect(jsonPath("$.sizeBytes").value(bytes.size))

        assertContentEquals(bytes, storedBytes(idOf(result)))
    }

    @Test
    fun `a file without a declared type gets application octet-stream`() {
        val sub = newSub()

        upload(sub, "data.bin", byteArrayOf(1, 2, 3)).andExpect(jsonPath("$.contentType").value("application/octet-stream"))
    }

    @Test
    fun `several files with the same name are kept with different ids`() {
        val sub = newSub()

        val first = idOf(upload(sub, "notes.txt", "one".toByteArray(), kind = "OTHER"))
        val second = idOf(upload(sub, "notes.txt", "two".toByteArray(), kind = "OTHER"))

        assertNotEquals(first, second)
        assertEquals(2, rows(sub))
    }

    @Test
    fun `an unknown kind is refused with 400`() {
        val sub = newSub()

        upload(sub, "notes.txt", "x".toByteArray(), kind = "PHOTO")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_file"))
            .andExpect(jsonPath("$.message").value("The kind must be CV or OTHER."))
        assertEquals(0, rows(sub))
    }

    @Test
    fun `each accepted CV kind gets its content type from the content and name`() {
        val sub = newSub()
        upload(sub, "cv.pdf", pdf, kind = "CV").andExpect(jsonPath("$.contentType").value("application/pdf"))
        upload(sub, "cv.md", "# Jane\n\n- Kotlin".toByteArray(), kind = "CV").andExpect(jsonPath("$.contentType").value("text/markdown"))
        upload(sub, "cv.txt", "Jane, engineer — Zürich".toByteArray(), kind = "CV").andExpect(jsonPath("$.contentType").value("text/plain"))
    }

    @Test
    fun `a CV with wrong content is refused with 400 and nothing is stored`() {
        val sub = newSub()
        val before = storedFiles()

        upload(sub, "cv.pdf", "this is not a pdf".toByteArray(), kind = "CV")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("The file must be a PDF, a Markdown file or a plain text file."))
        upload(sub, "cv.txt", byteArrayOf(0x50, 0x4B, 0x00, 0x04), kind = "CV").andExpect(status().isBadRequest)

        assertEquals(0, rows(sub))
        assertEquals(before, storedFiles())
        assertEquals(0, tempFiles())
    }

    @Test
    fun `a CV over 5 MB is refused with 413 and nothing is stored`() {
        val sub = newSub()

        upload(sub, "big.txt", ByteArray(5 * 1024 * 1024 + 1) { 'a'.code.toByte() }, kind = "CV")
            .andExpect(status().isContentTooLarge)
            .andExpect(jsonPath("$.message").value("The file is larger than 5 MB."))

        assertEquals(0, rows(sub))
        assertEquals(0, tempFiles())
    }

    @Test
    fun `an other file over the limit is refused with 413 and nothing is stored`() {
        val sub = newSub()

        upload(sub, "SECRETNAME4417.bin", ByteArray(1024 * 1024 + 1))
            .andExpect(status().isContentTooLarge)
            .andExpect(jsonPath("$.message").value("The file is larger than 10 GB."))

        assertEquals(0, rows(sub))
        assertEquals(0, tempFiles())
    }

    @Test
    fun `the error body never repeats the name or the content`() {
        val sub = newSub()

        val body = upload(sub, "SECRETNAME4417.pdf", "SECRETCONTENT4417".toByteArray(), kind = "CV")
            .andExpect(status().isBadRequest).andReturn().response.contentAsString

        assertFalse("SECRETNAME4417" in body || "SECRETCONTENT4417" in body)
    }

    @Test
    fun `the stored name is for display only`() {
        val sub = newSub()

        upload(sub, "../../etc/passwd", "x".toByteArray()).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("passwd"))
    }

    @Test
    fun `a failed insert leaves neither a row nor a stored file`() {
        val sub = newSub()
        val marker = "FAIL-INSERT-" + UUID.randomUUID()
        val before = storedFiles()
        jdbc.execute(
            """
            create or replace function files_api_fail_insert() returns trigger language plpgsql as $$
            begin
              if new.name = '$marker' then raise exception 'forced failure'; end if;
              return new;
            end $$
            """.trimIndent(),
        )
        jdbc.execute("create trigger files_api_fail_insert before insert on user_file for each row execute function files_api_fail_insert()")
        try {
            assertFailsWith<Exception> { upload(sub, marker, "bytes".toByteArray()) }
        } finally {
            jdbc.execute("drop trigger if exists files_api_fail_insert on user_file")
            jdbc.execute("drop function if exists files_api_fail_insert()")
        }

        assertEquals(0, rows(sub))
        assertEquals(before, storedFiles())
        assertEquals(0, tempFiles())
    }

    @Test
    fun `files belong to their owner`() {
        val a = newSub()
        val b = newSub()

        upload(a, "a.md", "# A".toByteArray(), kind = "CV").andExpect(status().isOk)
        upload(b, "b.txt", "BBB".toByteArray(), kind = "CV").andExpect(status().isOk)

        assertEquals(1, rows(a))
        assertEquals(1, rows(b))
        val owners = jdbc.queryForList("select owner_sub from user_file where owner_sub in (?, ?)", String::class.java, a, b)
        assertEquals(setOf(a, b), owners.toSet())
    }

    @Test
    fun `files belong to their owner through every read and write`() {
        val a = newSub()
        val b = newSub()
        val aFile = idOf(upload(a, "a.md", "# A".toByteArray(), kind = "CV"))
        val bFile = idOf(upload(b, "b.md", "# B".toByteArray(), kind = "CV"))

        get(a, "/api/files").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(aFile))
        get(a, "/api/files/$bFile").andExpect(status().isNotFound)
        put(a, "/api/files/master", """{"fileId":"$bFile"}""").andExpect(status().isNotFound)

        assertEquals(1, rows(b))
        get(b, "/api/files/$bFile").andExpect(status().isOk)
    }

    @Test
    fun `list shows the caller's files newest first and filters by kind`() {
        val sub = newSub()
        upload(sub, "first.txt", "1".toByteArray(), kind = "OTHER")
        Thread.sleep(5)
        upload(sub, "second.md", "# 2".toByteArray(), kind = "CV")
        Thread.sleep(5)
        upload(sub, "third.pdf", pdf, kind = "CV", type = "application/pdf")

        get(sub, "/api/files")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].name").value("third.pdf"))
            .andExpect(jsonPath("$[1].name").value("second.md"))
            .andExpect(jsonPath("$[2].name").value("first.txt"))
        get(sub, "/api/files?kind=CV")
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].kind").value(everyItem(equalTo("CV"))))
        get(sub, "/api/files?kind=PHOTO").andExpect(status().isBadRequest)
    }

    @Test
    fun `details answer one file and an unknown id is 404`() {
        val sub = newSub()
        val id = idOf(upload(sub, "notes.txt", "x".toByteArray(), kind = "OTHER"))

        get(sub, "/api/files/$id")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.kind").value("OTHER"))
            .andExpect(jsonPath("$.master").value(false))
        get(sub, "/api/files/${UUID.randomUUID()}").andExpect(status().isNotFound)
    }

    @Test
    fun `the master CV can be chosen, switched, read and cleared`() {
        val sub = newSub()
        val first = idOf(upload(sub, "first.md", "# first".toByteArray(), kind = "CV"))
        val second = idOf(upload(sub, "second.txt", "second".toByteArray(), kind = "CV"))
        val other = idOf(upload(sub, "photo.jpg", byteArrayOf(1, 2, 3), kind = "OTHER"))

        get(sub, "/api/files/master").andExpect(status().isNotFound)

        put(sub, "/api/files/master", """{"fileId":"$first"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(first))
            .andExpect(jsonPath("$.master").value(true))
        put(sub, "/api/files/master", """{"fileId":"$second"}""").andExpect(status().isOk)

        get(sub, "/api/files/master").andExpect(status().isOk).andExpect(jsonPath("$.id").value(second))
        get(sub, "/api/files/$first").andExpect(jsonPath("$.master").value(false))
        get(sub, "/api/files/$second").andExpect(jsonPath("$.master").value(true))
        assertEquals(1, jdbc.queryForObject("select count(*) from user_file where owner_sub = ? and master", Int::class.java, sub))

        put(sub, "/api/files/master", """{"fileId":"$other"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Only a file of kind CV can be the master CV."))
        get(sub, "/api/files/master").andExpect(jsonPath("$.id").value(second))

        upload(sub, "third.md", "# third".toByteArray(), kind = "CV").andExpect(status().isOk)
        get(sub, "/api/files/master").andExpect(jsonPath("$.id").value(second))

        delete(sub, "/api/files/master").andExpect(status().isNoContent)
        get(sub, "/api/files/master").andExpect(status().isNotFound)
        delete(sub, "/api/files/master").andExpect(status().isNoContent)
        assertEquals(4, rows(sub))
    }

    @Test
    fun `the master CV is per user and an id of another user is 404`() {
        val a = newSub()
        val b = newSub()
        val aCv = idOf(upload(a, "a.md", "# A".toByteArray(), kind = "CV"))
        put(b, "/api/files/master", """{"fileId":"$aCv"}""").andExpect(status().isNotFound)
        put(a, "/api/files/master", """{"fileId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)

        get(b, "/api/files/master").andExpect(status().isNotFound)
        put(a, "/api/files/master", """{"fileId":"$aCv"}""").andExpect(status().isOk)
        get(b, "/api/files/master").andExpect(status().isNotFound)
    }

    @Test
    fun `download with the token returns the same bytes as an attachment`() {
        val sub = newSub()
        val bytes = "Lebenslauf — Müller".toByteArray()
        val id = idOf(upload(sub, "Lebenslauf.txt", bytes, kind = "CV", type = "text/plain"))

        val result = get(sub, "/api/files/$id/content")
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", startsWith("attachment")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andReturn()

        assertContentEquals(bytes, result.response.contentAsByteArray)
    }

    @Test
    fun `a range request answers the requested bytes with 206`() {
        val sub = newSub()
        val id = idOf(upload(sub, "range.bin", ByteArray(100) { it.toByte() }))

        val result = mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/$id/content")
                .header("Authorization", bearer(sub))
                .header("Range", "bytes=0-9"),
        ).andExpect(status().isPartialContent).andReturn()

        assertContentEquals(ByteArray(10) { it.toByte() }, result.response.contentAsByteArray)
    }

    @Test
    fun `another user's file has no content for this user`() {
        val a = newSub()
        val b = newSub()
        val id = idOf(upload(a, "a.txt", "AAA".toByteArray(), kind = "OTHER"))

        get(b, "/api/files/$id/content").andExpect(status().isNotFound)
        get(a, "/api/files/$id/content").andExpect(status().isOk)
    }

    @Test
    fun `delete removes the row and the bytes, and is fine for an unknown or foreign id`() {
        val a = newSub()
        val b = newSub()
        val aFile = idOf(upload(a, "a.txt", "AAA".toByteArray(), kind = "OTHER"))
        val bFile = idOf(upload(b, "b.txt", "BBB".toByteArray(), kind = "OTHER"))
        val filesBefore = storedFiles()

        delete(a, "/api/files/$bFile").andExpect(status().isNoContent)
        get(b, "/api/files/$bFile").andExpect(status().isOk)

        delete(a, "/api/files/$aFile").andExpect(status().isNoContent)
        assertEquals(filesBefore - 1, storedFiles())
        get(a, "/api/files/$aFile").andExpect(status().isNotFound)
        get(a, "/api/files/$aFile/content").andExpect(status().isNotFound)
        assertEquals(0, rows(a))

        delete(a, "/api/files/$aFile").andExpect(status().isNoContent)
        delete(a, "/api/files/${UUID.randomUUID()}").andExpect(status().isNoContent)
    }

    @Test
    fun `deleting the master CV leaves the user without a master`() {
        val sub = newSub()
        val cv = idOf(upload(sub, "cv.md", "# cv".toByteArray(), kind = "CV"))
        put(sub, "/api/files/master", """{"fileId":"$cv"}""").andExpect(status().isOk)

        delete(sub, "/api/files/$cv").andExpect(status().isNoContent)

        get(sub, "/api/files/master").andExpect(status().isNotFound)
    }

    /** A request with no Authorization header at all. */
    private fun plain(path: String): ResultActions =
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))

    private fun ticketPath(sub: String, id: String): ResultActions =
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/$id/download-ticket")
                .header("Authorization", bearer(sub)),
        )

    private fun ticketOf(sub: String, id: String): String {
        val path: String = JsonPath.read(ticketPath(sub, id).andReturn().response.contentAsString, "$.path")
        return path.substringAfter("ticket=")
    }

    @Test
    fun `a ticket downloads the file once without a token`() {
        val sub = newSub()
        val bytes = "ticket bytes".toByteArray()
        val id = idOf(upload(sub, "t.txt", bytes, kind = "OTHER"))

        val body = ticketPath(sub, id).andExpect(status().isOk)
            .andExpect(jsonPath("$.path").value(startsWith("/api/files/$id/content?ticket=")))
            .andReturn().response.contentAsString
        val path: String = JsonPath.read(body, "$.path")
        val ticket = path.substringAfter("ticket=")
        assertTrue(ticket.matches(Regex("[0-9a-f]{64}")))

        val result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", startsWith("attachment")))
            .andReturn()
        assertContentEquals(bytes, result.response.contentAsByteArray)

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a ticket is bound to its file, expires and cannot be guessed`() {
        val sub = newSub()
        val a = idOf(upload(sub, "a.txt", "A".toByteArray(), kind = "OTHER"))
        val b = idOf(upload(sub, "b.txt", "B".toByteArray(), kind = "OTHER"))

        val forA = ticketOf(sub, a)
        plain("/api/files/$b/content?ticket=$forA").andExpect(status().isUnauthorized)
        plain("/api/files/$a/content?ticket=$forA").andExpect(status().isUnauthorized)

        val expiring = ticketOf(sub, a)
        clock.advance(Duration.ofSeconds(settings.ticketSeconds + 1))
        plain("/api/files/$a/content?ticket=$expiring").andExpect(status().isUnauthorized)

        plain("/api/files/$a/content?ticket=" + "0".repeat(64)).andExpect(status().isUnauthorized)
        plain("/api/files/$a/content?ticket=").andExpect(status().isUnauthorized)
    }

    @Test
    fun `without a token or ticket the content is 401, and a ticket opens nothing but its content`() {
        val sub = newSub()
        val id = idOf(upload(sub, "x.txt", "X".toByteArray(), kind = "OTHER"))
        val ticket = ticketOf(sub, id)

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/$id/content"))
            .andExpect(status().isUnauthorized)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/$id/content?ticket=$ticket&x=1"))
            .andExpect(status().isOk)

        val again = ticketOf(sub, id)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files?ticket=$again"))
            .andExpect(status().isUnauthorized)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/$id?ticket=$again"))
            .andExpect(status().isUnauthorized)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/files/$id?ticket=$again"))
            .andExpect(status().isUnauthorized)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/master?ticket=$again"))
            .andExpect(status().isUnauthorized)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/$id/download-ticket?ticket=$again"))
            .andExpect(status().isUnauthorized)

        get(sub, "/api/files/$id").andExpect(status().isOk)
        assertEquals(1, rows(sub))
    }

    @Test
    fun `a ticket is issued only to the owner and only for an existing file`() {
        val a = newSub()
        val b = newSub()
        val id = idOf(upload(a, "a.txt", "A".toByteArray(), kind = "OTHER"))

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/$id/download-ticket"))
            .andExpect(status().isUnauthorized)
        ticketPath(b, id).andExpect(status().isNotFound)
        ticketPath(a, UUID.randomUUID().toString()).andExpect(status().isNotFound)
    }

    private fun get(sub: String, path: String): ResultActions =
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization", bearer(sub)))

    private fun put(sub: String, path: String, json: String): ResultActions =
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .header("Authorization", bearer(sub))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json),
        )

    private fun delete(sub: String, path: String): ResultActions =
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path).header("Authorization", bearer(sub)))
}

/** Disk protection in its own context: the reserve is larger than any free space, so every upload is refused. */
@ApiTest
@ContextConfiguration(initializers = [MockIssuerInitializer::class, FilesVolumeInitializer::class])
@TestPropertySource(properties = ["jobhunter.files.min-free-bytes=9000000000000000000"])
internal class FilesApiDiskTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val storage: FileStorage,
) {

    @Test
    fun `an upload with no room left is refused with 507 before anything is stored`() {
        val sub = "files-api-disk-" + UUID.randomUUID()
        val before = storage.keys().count()

        mvc.perform(
            multipart("/api/files")
                .file(MockMultipartFile("file", "small.txt", "text/plain", "x".toByteArray()))
                .header("Authorization", "Bearer " + SharedMockIssuer.instance.token(sub)),
        )
            .andExpect(status().isInsufficientStorage)
            .andExpect(content().string("""{"error":"invalid_file","message":"The server has no room for this file right now."}"""))

        assertEquals(0, jdbc.queryForObject("select count(*) from user_file where owner_sub = ?", Int::class.java, sub))
        assertEquals(before, storage.keys().count())
    }
}
