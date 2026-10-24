package net.s4v3.backend

import net.s4v3.backend.cv.publiccv.internal.PreviewTickets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// One counter for the whole JVM: the limiters live in the shared Spring context, so no test may reuse an address.
private val ipCounter = AtomicInteger(1)

/** The public CV page end to end with synthetic profiles (an invented person). Every client uses its own address. */
@ApiTest
internal class PublicCvApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val tickets: PreviewTickets,
) {

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun admin() = "Bearer " + issuer.token("admin-" + UUID.randomUUID(), roles = listOf("USER", "ADMIN"))
    private fun newSub() = "pcv-api-" + UUID.randomUUID()
    private fun newHandle() = "ada-" + UUID.randomUUID().toString().replace("-", "").take(10)

    private fun newIp() = ipCounter.getAndIncrement().let { "198.51.${it / 250}.${it % 250 + 1}" }

    private val defaults = "{}"
    private fun settings(more: String) = "{$more}"

    private fun profileJson(name: String? = "Ada Example", summary: String = "Builds backends.", phone: String = "+41 79 555 01 23") =
        """
        {"contact":${if (name == null) "{}" else """{"fullName":"$name","headline":"Senior Kotlin Engineer","email":"ada@example.org","phone":"$phone","website":"https://ada.example.org"}"""},
         "location":{"city":"Basel","canton":"BS"},"workAuthorisation":"PERMIT_B","summary":"$summary",
         "experience":[{"employer":"Employer AG","title":"Engineer","start":"2021-03","achievements":["Built the thing"]}],
         "skills":[{"name":"Kotlin","level":"EXPERT"}],
         "preferences":{"targetRoles":["Secret Role"],"cantons":["ZH"],"minSalaryChf":150000}}
        """.trimIndent()

    private val profileVersion = HashMap<String, Int>()

    private fun saveProfile(sub: String, profile: String = profileJson()) {
        val version = profileVersion[sub] ?: 0
        mvc.perform(
            put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":$version,"profile":$profile}"""),
        ).andExpect(status().isOk)
        profileVersion[sub] = version + 1
    }

    private fun user(name: String? = "Ada Example"): String = newSub().also { saveProfile(it, profileJson(name)) }

    private fun putCv(sub: String, handle: String, published: Boolean = true, settings: String = defaults): ResultActions =
        putRaw(sub, """{"handle":"$handle","published":$published,"settings":$settings}""")

    private fun putRaw(sub: String, body: String): ResultActions =
        mvc.perform(put("/api/cv/public").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun getCv(sub: String): ResultActions = mvc.perform(get("/api/cv/public").header("Authorization", bearer(sub)))

    private fun page(handle: String, ip: String = newIp(), ifNoneMatch: String? = null): ResultActions =
        mvc.perform(
            get("/api/public/cv/$handle").header("X-Forwarded-For", ip).apply { ifNoneMatch?.let { header("If-None-Match", it) } },
        )

    private fun html(result: ResultActions): String = result.andReturn().response.contentAsString

    private fun publish(sub: String = user(), settings: String = defaults): Pair<String, String> {
        val handle = newHandle()
        putCv(sub, handle, true, settings).andExpect(status().isOk)
        return sub to handle
    }

    private fun previewPath(sub: String, settings: String = defaults): String =
        mvc.perform(
            post("/api/cv/public/preview").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"settings":$settings}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString.substringAfter("\"path\":\"").substringBefore('"')

    private fun count(sql: String, vararg args: Any) = jdbc.queryForObject(sql, Int::class.java, *args)!!

    // ---- Handle ----

    @Test
    fun `a handle is lower-cased and stored`() {
        val sub = user()
        val handle = newHandle()

        putCv(sub, handle.uppercase()).andExpect(status().isOk).andExpect(jsonPath("$.handle").value(handle))
        getCv(sub).andExpect(jsonPath("$.handle").value(handle))
    }

    @Test
    fun `invalid forms are 400 with a field message that does not repeat the value`() {
        val sub = user()
        listOf("ab", "-ada", "ada--x", "ada_x", "a".repeat(31), "123456").forEach { handle ->
            val body = putCv(sub, handle).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.handle").isString).andReturn().response.contentAsString
            assertFalse(handle in body, "message repeats the value: $handle")
        }
        getCv(sub).andExpect(jsonPath("$.configured").value(false))
    }

    @Test
    fun `reserved handles are 409 handle_reserved`() {
        val sub = user()
        listOf("admin", "Profile").forEach {
            putCv(sub, it).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("handle_reserved"))
        }
    }

    @Test
    fun `a taken handle is 409 handle_taken and the page of the owner is unchanged`() {
        val (a, handle) = publish()
        val b = user("Bob Beta")

        putCv(b, handle).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("handle_taken"))

        assertTrue("Ada Example" in html(page(handle).andExpect(status().isOk)))
        getCv(a).andExpect(jsonPath("$.handle").value(handle))
    }

    @Test
    fun `a released handle is held for 90 days and then free`() {
        val a = user()
        val old = newHandle()
        putCv(a, old).andExpect(status().isOk)
        putCv(a, newHandle()).andExpect(status().isOk)
        val b = user("Bob Beta")

        putCv(b, old).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("handle_held"))
        // The previous owner may take it back (the 30-day change rule still applies to the change itself).
        mvc.perform(get("/api/cv/public/handle").param("handle", old).header("Authorization", bearer(a)))
            .andExpect(jsonPath("$.available").value(true))

        jdbc.update("update public_cv_handle_hold set held_until = ? where handle = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), old)
        putCv(b, old).andExpect(status().isOk)
    }

    @Test
    fun `a second change within 30 days is 409 handle_change_too_soon and the date is shown`() {
        val sub = user()
        putCv(sub, newHandle()).andExpect(status().isOk)
        putCv(sub, newHandle()).andExpect(status().isOk)
            .andExpect(jsonPath("$.handleChangeableAt").isString)

        putCv(sub, newHandle()).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("handle_change_too_soon"))
        getCv(sub).andExpect(jsonPath("$.handleChangeableAt").isString)
    }

    @Test
    fun `the availability check names the reason and treats the own handle as available`() {
        val (a, taken) = publish()
        val b = user("Bob Beta")
        fun check(who: String, handle: String) =
            mvc.perform(get("/api/cv/public/handle").param("handle", handle).header("Authorization", bearer(who)))

        check(b, newHandle()).andExpect(status().isOk).andExpect(jsonPath("$.available").value(true))
        check(a, taken).andExpect(jsonPath("$.available").value(true))
        check(b, taken).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("taken"))
        check(b, "admin").andExpect(jsonPath("$.reason").value("reserved"))
        check(b, "a_b").andExpect(jsonPath("$.reason").value("invalid"))
        val old = newHandle()
        putCv(b, old).andExpect(status().isOk)
        putCv(b, newHandle()).andExpect(status().isOk)
        check(a, old).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("held"))
        mvc.perform(get("/api/cv/public/handle").header("Authorization", bearer(a))).andExpect(status().isBadRequest)
    }

    @Test
    fun `handle checks are limited per user and hour`() {
        val sub = user()
        repeat(60) {
            mvc.perform(get("/api/cv/public/handle").param("handle", "abc").header("Authorization", bearer(sub))).andExpect(status().isOk)
        }

        mvc.perform(get("/api/cv/public/handle").param("handle", "abc").header("Authorization", bearer(sub)))
            .andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").value("rate_limited"))
    }

    // ---- Visibility settings ----

    @Test
    fun `defaults are private`() {
        val (_, handle) = publish()

        val body = html(page(handle).andExpect(status().isOk))

        assertTrue("Ada Example" in body)
        listOf("ada@example.org", "555", "Senior Kotlin", "Basel", "PERMIT", "Permit", "Builds backends", "Employer AG", "Kotlin", "ada.example.org")
            .forEach { assertFalse(it in body, "$it is public by default") }
    }

    @Test
    fun `ticked items appear and nothing else`() {
        val (_, handle) = publish(settings = settings(""""sections":["EXPERIENCE","SKILLS"],"headline":true,"contact":{"website":true}"""))

        val body = html(page(handle).andExpect(status().isOk))

        listOf("Ada Example", "Senior Kotlin Engineer", "ada.example.org", "Employer AG", "Kotlin").forEach { assertTrue(it in body, "$it missing") }
        listOf("ada@example.org", "555", "Basel", "Builds backends").forEach { assertFalse(it in body, "$it appears") }
    }

    @Test
    fun `preferences are never printed even with everything ticked`() {
        val all = settings(
            """"sections":["PROFILE","EXPERIENCE","OPEN_SOURCE","EDUCATION","SKILLS","LANGUAGES","PUBLICATIONS"],"headline":true,"analysis":true,""" +
                """"workAuthorisation":true,"location":true,"contact":{"email":true,"phone":true,"website":true,"linkedin":true,"codeAccounts":true}""",
        )
        val (_, handle) = publish(settings = all)

        val body = html(page(handle).andExpect(status().isOk))

        listOf("150000", "Secret Role", "ZH").forEach { assertFalse(it in body, "$it printed") }
    }

    @Test
    fun `sections are printed in the order given`() {
        val (_, handle) = publish(settings = settings(""""sections":["SKILLS","EXPERIENCE"]"""))

        val body = html(page(handle).andExpect(status().isOk))

        assertTrue(body.indexOf("Skills") in 0 until body.indexOf("Experience"))
    }

    @Test
    fun `an unknown field or value is 400 and nothing is stored`() {
        val sub = user()
        val handle = newHandle()
        listOf(
            """{"handle":"$handle","published":true,"settings":{"template":"FANCY"}}""",
            """{"handle":"$handle","published":true,"settings":{"extra":true}}""",
            """{"handle":"$handle","published":true,"settings":{"contact":{"fax":true}}}""",
            """{"handle":"$handle","published":true,"settings":{"sections":["NOPE"]}}""",
            """{"handle":"$handle","published":true,"settings":{"sections":[null]}}""",
            """{"handle":"$handle","published":true,"settings":{"headline":null}}""",
            """{"handle":"$handle","published":true,"ownerSub":"someone"}""",
            """{"published":true}""",
            "not json",
            "",
        ).forEach { putRaw(sub, it).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_request")) }

        getCv(sub).andExpect(jsonPath("$.configured").value(false))
    }

    // ---- Publish, pause, remove ----

    @Test
    fun `publish makes the page readable without a token`() {
        val sub = user()
        val handle = newHandle()

        putCv(sub, handle).andExpect(status().isOk).andExpect(jsonPath("$.published").value(true))
            .andExpect(jsonPath("$.locked").value(false)).andExpect(jsonPath("$.settings.template").value("CLASSIC"))
            .andExpect(jsonPath("$.updatedAt").isString)

        page(handle).andExpect(status().isOk)
    }

    @Test
    fun `pause answers 404 and resume brings the page back`() {
        val (sub, handle) = publish(settings = settings(""""headline":true"""))

        putCv(sub, handle, published = false, settings = settings(""""headline":true""")).andExpect(status().isOk)
        page(handle).andExpect(status().isNotFound)
        getCv(sub).andExpect(jsonPath("$.settings.headline").value(true))

        putCv(sub, handle, published = true, settings = settings(""""headline":true""")).andExpect(status().isOk)
        assertTrue("Senior Kotlin Engineer" in html(page(handle).andExpect(status().isOk)))
    }

    @Test
    fun `remove deletes the page and holds the handle`() {
        val (sub, handle) = publish()

        mvc.perform(delete("/api/cv/public").header("Authorization", bearer(sub))).andExpect(status().isNoContent)

        page(handle).andExpect(status().isNotFound)
        getCv(sub).andExpect(jsonPath("$.configured").value(false))
        putCv(user("Bob Beta"), handle).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("handle_held"))
        mvc.perform(delete("/api/cv/public").header("Authorization", bearer(sub))).andExpect(status().isNoContent)
    }

    @Test
    fun `publishing without a full name is 409 name_required`() {
        val sub = user(name = null)

        putCv(sub, newHandle()).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("name_required"))
        putCv(sub, newHandle(), published = false).andExpect(status().isOk)
    }

    @Test
    fun `remove while locked is 409 locked and the page and its lock stay`() {
        val (sub, handle) = publish()
        mvc.perform(delete("/api/admin/public-cv/$handle").header("Authorization", admin())).andExpect(status().isNoContent)

        mvc.perform(delete("/api/cv/public").header("Authorization", bearer(sub)))
            .andExpect(status().isConflict).andExpect(jsonPath("$.error").value("locked"))

        getCv(sub).andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.locked").value(true))
    }

    @Test
    fun `a user sees and changes only the own page`() {
        val (a, handleA) = publish()
        val (b, handleB) = publish(user("Bob Beta"))

        getCv(a).andExpect(jsonPath("$.handle").value(handleA))
        getCv(b).andExpect(jsonPath("$.handle").value(handleB))
        mvc.perform(delete("/api/cv/public").header("Authorization", bearer(a))).andExpect(status().isNoContent)
        page(handleB).andExpect(status().isOk)
        putRaw(a, """{"handle":"${newHandle()}","published":true,"owner":"$b"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `every management endpoint answers 401 without a token`() {
        val json = MediaType.APPLICATION_JSON
        mvc.perform(get("/api/cv/public")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/cv/public").contentType(json).content("""{"handle":"abc","published":true}""")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/cv/public")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/cv/public/handle").param("handle", "abc")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/cv/public/preview").contentType(json).content("""{"settings":{}}""")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/admin/public-cv/abc")).andExpect(status().isUnauthorized)
    }

    // ---- The public page ----

    @Test
    fun `an anonymous client reads a published page`() {
        val (_, handle) = publish()

        page(handle).andExpect(status().isOk).andExpect(header().string("Content-Type", "text/html; charset=utf-8"))
            .andExpect(header().exists("ETag"))
        // The handle is matched ignoring case.
        page(handle.uppercase()).andExpect(status().isOk)
    }

    @Test
    fun `a saved profile edit shows on the next request and an unticked one stays private`() {
        val sub = user()
        val handle = newHandle()
        putCv(sub, handle, settings = settings(""""sections":["PROFILE"]""")).andExpect(status().isOk)
        assertTrue("Builds backends." in html(page(handle)))

        saveProfile(sub, profileJson(summary = "Now builds frontends.", phone = "+41 79 999 88 77"))

        val body = html(page(handle).andExpect(status().isOk))
        assertTrue("Now builds frontends." in body)
        assertFalse("999 88 77" in body)
        assertFalse("Builds backends." in body)
    }

    @Test
    fun `every state that is not served answers the same 404`() {
        val (_, paused) = publish().also { (s, h) -> putCv(s, h, published = false).andExpect(status().isOk) }
        val (removedSub, removed) = publish()
        mvc.perform(delete("/api/cv/public").header("Authorization", bearer(removedSub))).andExpect(status().isNoContent)
        val (_, locked) = publish().also { (_, h) -> mvc.perform(delete("/api/admin/public-cv/$h").header("Authorization", admin())).andExpect(status().isNoContent) }
        val nameless = user().let { s -> publish(s).second.also { saveProfile(s, profileJson(name = null)) } }

        val answers = listOf(newHandle(), paused, removed, locked, nameless, "x", "a_b", "ünï","a".repeat(80)).map {
            page(it).andReturn().also { r -> assertEquals(404, r.response.status, "status for [$it]") }
        }

        val first = answers.first()
        answers.forEach { r ->
            assertEquals("""{"error":"not_found"}""", r.response.contentAsString)
            assertEquals(first.response.headerNames.toSet(), r.response.headerNames.toSet())
            assertEquals("no-store", r.response.getHeader("Cache-Control"))
            assertEquals("noindex, nofollow, noarchive, nosnippet", r.response.getHeader("X-Robots-Tag"))
            assertEquals(first.response.contentType, r.response.contentType)
        }
    }

    @Test
    fun `a deleted account takes its page and rows with it`() {
        val (sub, handle) = publish()
        putCv(sub, handle, published = false)
        assertTrue(count("select count(*) from public_cv_audit where owner_sub = ?", sub) > 0)

        jdbc.update("delete from app_user where sub = ?", sub)

        page(handle).andExpect(status().isNotFound)
        assertEquals(0, count("select count(*) from public_cv where owner_sub = ?", sub))
        assertEquals(0, count("select count(*) from public_cv_audit where owner_sub = ?", sub))
        assertEquals(0, count("select count(*) from public_cv_handle_hold where owner_sub = ?", sub))
    }

    @Test
    fun `no other public path is open`() {
        listOf("/api/public", "/api/public/", "/api/public/cv", "/api/public/cv/", "/api/public/other", "/api/public/cv/a/b", "/api/cv/public", "/api/cv/public/handle", "/api/cv/public/preview/")
            .forEach { path ->
                val r = mvc.perform(get(path).header("X-Forwarded-For", newIp())).andReturn().response
                assertTrue(r.status == 401 || r.status == 404, "$path answered ${r.status}")
                assertFalse("Ada" in r.contentAsString)
            }
        mvc.perform(post("/api/public/cv/abc")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/public/cv/abc")).andExpect(status().isUnauthorized)
    }

    // ---- Headers and content ----

    @Test
    fun `the page carries the safe headers`() {
        val (_, handle) = publish()

        val r = page(handle).andExpect(status().isOk).andReturn().response

        assertEquals(
            "default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox",
            r.getHeader("Content-Security-Policy"),
        )
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"))
        assertEquals("no-referrer", r.getHeader("Referrer-Policy"))
        assertEquals("no-cache", r.getHeader("Cache-Control"))
        assertEquals("noindex, nofollow, noarchive, nosnippet", r.getHeader("X-Robots-Tag"))
        assertTrue(Regex("\"[0-9a-f]{64}\"").matches(r.getHeader("ETag")!!))
        assertTrue("<meta name=\"robots\" content=\"noindex, nofollow, noarchive, nosnippet\"" in r.contentAsString)
        assertFalse("<script" in r.contentAsString)
    }

    @Test
    fun `indexable is an explicit opt-in and the meta tag agrees`() {
        val (_, handle) = publish(settings = settings(""""indexable":true"""))

        val r = page(handle).andExpect(status().isOk).andReturn().response

        assertEquals("index, follow", r.getHeader("X-Robots-Tag"))
        assertTrue("content=\"index, follow\"" in r.contentAsString)
    }

    @Test
    fun `markup in the profile is text`() {
        val sub = newSub()
        saveProfile(
            sub,
            """{"contact":{"fullName":"Ada Example"},"experience":[{"employer":"E","title":"T","start":"2021-03","achievements":["<script>alert(1)</script> \"x\" & y"]}]}""",
        )
        val handle = newHandle()
        putCv(sub, handle, settings = settings(""""sections":["EXPERIENCE"]""")).andExpect(status().isOk)

        val body = html(page(handle).andExpect(status().isOk))

        assertTrue("&lt;script&gt;" in body)
        assertFalse("<script" in body)
    }

    @Test
    fun `a matching If-None-Match answers 304 without a body and a change gives a new ETag`() {
        val sub = user()
        val handle = newHandle()
        putCv(sub, handle, settings = settings(""""sections":["PROFILE"]""")).andExpect(status().isOk)
        val etag = page(handle).andReturn().response.getHeader("ETag")!!

        val same = page(handle, ifNoneMatch = etag).andExpect(status().isNotModified).andReturn().response
        assertEquals(0, same.contentAsByteArray.size)
        assertEquals(etag, same.getHeader("ETag"))
        page(handle, ifNoneMatch = "W/$etag").andExpect(status().isNotModified)
        page(handle, ifNoneMatch = "\"other\"").andExpect(status().isOk)

        saveProfile(sub, profileJson(summary = "A changed summary."))
        val changed = page(handle, ifNoneMatch = etag).andExpect(status().isOk).andReturn().response
        assertNotEquals(etag, changed.getHeader("ETag"))
    }

    // ---- Limits ----

    @Test
    fun `the 61st request of one address in a minute is 429 with Retry-After and other addresses are unaffected`() {
        val (_, handle) = publish()
        val ip = newIp()
        repeat(60) { page(handle, ip).andExpect(status().isOk) }

        val limited = page(handle, ip).andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").value("rate_limited")).andReturn().response
        assertTrue(limited.getHeader("Retry-After")!!.toLong() >= 1)

        page(handle).andExpect(status().isOk)
    }

    @Test
    fun `the 11th unknown handle of one address in a minute is 429`() {
        val ip = newIp()
        repeat(10) { page(newHandle(), ip).andExpect(status().isNotFound) }

        page(newHandle(), ip).andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
        page("a_b", ip).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `the 601st request of one handle from many addresses is 429`() {
        val (_, handle) = publish()
        repeat(600) { page(handle).andExpect(status().isOk) }

        page(handle).andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `the client address is the first X-Forwarded-For value, else the socket`() {
        val (_, handle) = publish()
        val first = newIp()
        repeat(60) {
            mvc.perform(get("/api/public/cv/$handle").header("X-Forwarded-For", " $first , 10.9.9.9")).andExpect(status().isOk)
        }

        mvc.perform(get("/api/public/cv/$handle").header("X-Forwarded-For", "$first,10.8.8.8")).andExpect(status().isTooManyRequests)
        // A header that is too long is ignored: the socket address counts instead.
        mvc.perform(get("/api/public/cv/$handle").header("X-Forwarded-For", "1".repeat(46))).andExpect(status().isOk)
    }

    // ---- Review fixes ----

    private fun deleteCv(sub: String): ResultActions = mvc.perform(delete("/api/cv/public").header("Authorization", bearer(sub)))

    @Test
    fun `delete and save again with the email off never shows the email from the cache`() {
        val sub = user()
        val handle = newHandle()
        putCv(sub, handle, settings = settings(""""contact":{"email":true}""")).andExpect(status().isOk)
        assertTrue("ada@example.org" in html(page(handle).andExpect(status().isOk)))

        deleteCv(sub).andExpect(status().isNoContent)
        putCv(sub, handle, settings = settings(""""contact":{"email":false}""")).andExpect(status().isOk)

        assertFalse("ada@example.org" in html(page(handle).andExpect(status().isOk)))
    }

    @Test
    fun `two saves in a row and a profile edit never bring the email back`() {
        val sub = user()
        val handle = newHandle()
        putCv(sub, handle, settings = settings(""""contact":{"email":true}""")).andExpect(status().isOk)
        assertTrue("ada@example.org" in html(page(handle)))

        putCv(sub, handle, settings = settings(""""headline":true,"contact":{"email":true}""")).andExpect(status().isOk)
        putCv(sub, handle, settings = settings(""""headline":true""")).andExpect(status().isOk)
        assertFalse("ada@example.org" in html(page(handle)))

        saveProfile(sub, profileJson(summary = "Edited summary."))
        assertFalse("ada@example.org" in html(page(handle)))
    }

    @Test
    fun `the 31st save of one user in an hour is 429 and a delete counts too`() {
        val sub = user()
        val handle = newHandle()
        repeat(30) { putCv(sub, handle, settings = settings(""""headline":${it % 2 == 0}""")).andExpect(status().isOk) }

        putCv(sub, handle).andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").value("rate_limited"))
        deleteCv(sub).andExpect(status().isTooManyRequests)
        putCv(user(), newHandle()).andExpect(status().isOk)
    }

    @Test
    fun `a taken handle is still told to the owner while saves are not limited`() {
        val (_, taken) = publish()
        putCv(user(), taken).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("handle_taken"))
    }

    @Test
    fun `delete then a different handle within 30 days is 409 handle_change_too_soon`() {
        val sub = user()
        putCv(sub, newHandle()).andExpect(status().isOk)
        deleteCv(sub).andExpect(status().isNoContent)

        putCv(sub, newHandle()).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("handle_change_too_soon"))
    }

    @Test
    fun `forged X-Forwarded-For values fall back to the socket address`() {
        val socket = newIp()
        fun call(forwarded: String) = mvc.perform(
            get("/api/public/cv/" + newHandle()).header("X-Forwarded-For", forwarded).with { it.remoteAddr = socket; it },
        )
        listOf("junk-1", "evil.example.com", "999.1.1.1", "1.2.3", "::zz", "a b c", "1.2.3.4.5", "<script>", "-1.2.3.4", "0x7f.0.0.1")
            .forEach { call(it).andExpect(status().isNotFound) }

        call("junk-last").andExpect(status().isTooManyRequests)
    }

    @Test
    fun `valid IPv4 and IPv6 forwarded addresses are used as the client`() {
        val v6 = "2001:db8::" + ipCounter.getAndIncrement().toString(16)
        repeat(10) {
            mvc.perform(get("/api/public/cv/" + newHandle()).header("X-Forwarded-For", v6)).andExpect(status().isNotFound)
        }
        mvc.perform(get("/api/public/cv/" + newHandle()).header("X-Forwarded-For", v6)).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `HEAD on a public page answers like GET without a body and a missing page is 404`() {
        val (_, handle) = publish()
        val get = page(handle).andReturn().response
        val head = mvc.perform(head("/api/public/cv/$handle").header("X-Forwarded-For", newIp())).andExpect(status().isOk)
            .andReturn().response

        assertEquals(0, head.contentAsByteArray.size)
        assertEquals(get.getHeader("ETag"), head.getHeader("ETag"))
        assertEquals(get.getHeader("Content-Security-Policy"), head.getHeader("Content-Security-Policy"))
        mvc.perform(head("/api/public/cv/" + newHandle()).header("X-Forwarded-For", newIp())).andExpect(status().isNotFound)
        // Nothing broader: HEAD elsewhere still needs a token.
        mvc.perform(head("/api/cv/public")).andExpect(status().isUnauthorized)
        mvc.perform(head("/api/cv/public/preview/" + "0".repeat(32))).andExpect(status().isUnauthorized)
    }

    // ---- Preview ----

    @Test
    fun `a preview opens once with the safe headers and then is 404`() {
        val sub = user()
        val path = previewPath(sub, settings(""""headline":true,"indexable":true"""))
        assertTrue(path.startsWith("/api/cv/public/preview/"))

        val r = mvc.perform(get(path)).andExpect(status().isOk).andReturn().response

        assertTrue("Senior Kotlin Engineer" in r.contentAsString)
        assertEquals("no-store", r.getHeader("Cache-Control"))
        assertEquals("noindex, nofollow, noarchive, nosnippet", r.getHeader("X-Robots-Tag"))
        assertEquals("text/html; charset=utf-8", r.getHeader("Content-Type"))
        assertTrue("sandbox" in r.getHeader("Content-Security-Policy")!!)
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"))
        mvc.perform(get(path)).andExpect(status().isNotFound).andExpect(jsonPath("$.error").value("not_found"))
    }

    @Test
    fun `a preview ticket expires after 60 seconds`() {
        val path = previewPath(user())
        // The clock of the ticket store is the system clock: move the expiry of every ticket into the past.
        val map = PreviewTickets::class.java.getDeclaredField("tickets").apply { isAccessible = true }.get(tickets) as Map<*, *>
        val expires = Class.forName("net.s4v3.backend.cv.publiccv.internal.PreviewTickets\$Ticket")
            .getDeclaredField("expiresAt").apply { isAccessible = true }
        map.values.forEach { expires.set(it, Instant.now().minusSeconds(61)) }

        mvc.perform(get(path)).andExpect(status().isNotFound)
    }

    @Test
    fun `a preview works without a published page and a forged ticket is 404`() {
        val sub = user()
        getCv(sub).andExpect(jsonPath("$.configured").value(false))

        mvc.perform(get(previewPath(sub))).andExpect(status().isOk)

        mvc.perform(get("/api/cv/public/preview/" + "ab".repeat(16))).andExpect(status().isNotFound)
        mvc.perform(get("/api/cv/public/preview/forged")).andExpect(status().isNotFound)
    }

    @Test
    fun `a preview is bad for unknown settings and limited to 30 per hour`() {
        val sub = user()
        mvc.perform(
            post("/api/cv/public/preview").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"settings":{"template":"FANCY"}}"""),
        ).andExpect(status().isBadRequest)

        repeat(30) { previewPath(sub) }

        mvc.perform(
            post("/api/cv/public/preview").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content("""{"settings":{}}"""),
        ).andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
    }

    // ---- Administrator ----

    @Test
    fun `takedown pauses and locks, unlock lifts the lock`() {
        val (sub, handle) = publish()
        val admin = admin()

        mvc.perform(delete("/api/admin/public-cv/${handle.uppercase()}").header("Authorization", admin)).andExpect(status().isNoContent)

        page(handle).andExpect(status().isNotFound)
        getCv(sub).andExpect(jsonPath("$.locked").value(true))
        putCv(sub, handle).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("locked"))

        mvc.perform(post("/api/admin/public-cv/$handle/unlock").header("Authorization", admin)).andExpect(status().isNoContent)

        getCv(sub).andExpect(jsonPath("$.locked").value(false))
        putCv(sub, handle).andExpect(status().isOk)
        page(handle).andExpect(status().isOk)
    }

    @Test
    fun `the admin endpoints answer 404 for an unknown handle and 403 for a user`() {
        val admin = admin()
        mvc.perform(delete("/api/admin/public-cv/${newHandle()}").header("Authorization", admin)).andExpect(status().isNotFound)
        mvc.perform(post("/api/admin/public-cv/${newHandle()}/unlock").header("Authorization", admin)).andExpect(status().isNotFound)

        val (_, handle) = publish()
        mvc.perform(delete("/api/admin/public-cv/$handle").header("Authorization", bearer(newSub()))).andExpect(status().isForbidden)
        mvc.perform(post("/api/admin/public-cv/$handle/unlock").header("Authorization", bearer(newSub()))).andExpect(status().isForbidden)
        page(handle).andExpect(status().isOk)
    }

    // ---- Audit and privacy ----

    @Test
    fun `publish, handle change and pause are audited with flags and no values`() {
        val sub = user()
        val first = newHandle()
        putCv(sub, first, settings = settings(""""headline":true,"contact":{"email":true}""")).andExpect(status().isOk)
        putCv(sub, newHandle(), settings = settings(""""headline":true,"contact":{"email":true}""")).andExpect(status().isOk)
        putCv(sub, getHandle(sub), published = false, settings = settings(""""headline":true,"contact":{"email":true}""")).andExpect(status().isOk)

        fun n(event: String) = count("select count(*) from public_cv_audit where owner_sub = ? and event = ?", sub, event)
        assertEquals(1, n("PUBLISHED"))
        assertEquals(1, n("HANDLE_CHANGED"))
        assertEquals(1, n("PAUSED"))
        val flags = jdbc.queryForObject(
            "select flags::text from public_cv_audit where owner_sub = ? and event = 'PUBLISHED'", String::class.java, sub,
        )!!
        assertTrue("\"headline\": true" in flags && "\"contact.email\": true" in flags && "\"contact.phone\": false" in flags, flags)
        listOf("Ada Example", "ada@example.org", first).forEach { assertFalse(it in flags) }
    }

    private fun getHandle(sub: String): String =
        getCv(sub).andReturn().response.contentAsString.substringAfter("\"handle\":\"").substringBefore('"')

    @Test
    fun `nothing about a page, a client or a save reaches the log`() {
        val sub = user()
        val handle = newHandle()
        val ip = newIp()
        val ignoredHandle = "zzz" + UUID.randomUUID().toString().take(8)
        // The level as configured (no override): the test checks what the application logs.
        val events = capture(levels = emptyMap()) {
            putCv(sub, handle, settings = settings(""""headline":true,"contact":{"email":true,"phone":true}""")).andExpect(status().isOk)
            page(handle, ip).andExpect(status().isOk)
            page(ignoredHandle, ip).andExpect(status().isNotFound)
            repeat(70) { page(handle, ip) }
            page(handle, ip).andExpect(status().isTooManyRequests)
            mvc.perform(get(previewPath(sub))).andExpect(status().isOk)
        }

        val text = events.joinToString("\n") { it.fullText() }
        listOf(handle, ignoredHandle, ip, "ada@example.org", "555 01 23", "Ada Example", "Senior Kotlin", "Builds backends").forEach {
            assertFalse(it in text, "the log holds $it")
        }
        assertTrue("Public CV answer status=200" in text, "the page answers are logged by status")
        assertFalse(Regex("/preview/[0-9a-f]{32}").containsMatchIn(text), "a ticket is in the log")
    }
}
