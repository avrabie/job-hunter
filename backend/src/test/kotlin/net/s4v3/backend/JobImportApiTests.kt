package net.s4v3.backend

import com.jayway.jsonpath.JsonPath
import net.s4v3.backend.jobimport.JobImportService
import net.s4v3.backend.jobimport.internal.FetchError
import net.s4v3.backend.jobimport.internal.FetchResult
import net.s4v3.backend.jobimport.internal.Grounding
import net.s4v3.backend.jobimport.internal.ImportDrafts
import net.s4v3.backend.jobimport.internal.PageFetcher
import net.s4v3.backend.llm.JobDraft
import net.s4v3.backend.llm.JobExtractor
import net.s4v3.backend.llm.LetterLanguage
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Job import over MockMvc with real tokens, a scripted fetcher and a scripted extractor. Synthetic data only. The limits are
 * 3 previews per hour and 2 confirms per day here, so every test uses its own user.
 */
@ApiTest
@Import(JobImportApiTests.FakesConfig::class)
@TestPropertySource(properties = ["jobhunter.import.previews-per-hour=3", "jobhunter.import.confirms-per-day=2"])
internal class JobImportApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val service: JobImportService,
    @Autowired private val drafts: ImportDrafts,
    @Autowired private val fetcher: FakeFetcher,
    @Autowired private val extractor: FakeExtractor,
) {

    class FakeFetcher : PageFetcher {
        val urls = CopyOnWriteArrayList<String>()
        var answer: (String) -> FetchResult = { FetchResult.Page(page(DESCRIPTION)) }

        override fun fetch(url: String): FetchResult {
            urls += url
            return answer(url)
        }
    }

    class FakeExtractor : JobExtractor {
        val texts = CopyOnWriteArrayList<String>()
        var answer: (String) -> JobDraft = { job() }

        override fun extract(pageText: String): JobDraft {
            texts += pageText
            return answer(pageText)
        }
    }

    @TestConfiguration
    class FakesConfig {
        @Bean
        fun fakeFetcher() = FakeFetcher()

        @Bean
        @Primary
        fun pageFetcher(fake: FakeFetcher): PageFetcher = fake

        @Bean
        fun fakeExtractor() = FakeExtractor()

        @Bean
        @Primary
        fun jobExtractor(fake: FakeExtractor): JobExtractor = fake
    }

    private companion object {
        const val DESCRIPTION = "Employer1 AG is looking for a backend engineer who builds and runs reliable services with Kotlin and " +
            "PostgreSQL. You will work in a small team, review code, improve the delivery pipeline and talk to customers every week. " +
            "We offer flexible hours, a learning budget, regular team events and a calm office close to the main station."

        fun page(text: String) = "<html><body><h1>Backend Engineer</h1><p>$text</p></body></html>"

        fun job(title: String = "Backend Engineer", company: String = "Employer1 AG", location: String? = "Zurich", description: String = DESCRIPTION) =
            JobDraft(true, title, company, location, description, LetterLanguage.EN)
    }

    private fun bearer(sub: String) = "Bearer " + SharedMockIssuer.instance.token(sub)
    private fun newSub() = "import-api-" + UUID.randomUUID()
    private fun unique() = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun reset() {
        fetcher.urls.clear()
        fetcher.answer = { FetchResult.Page(page(DESCRIPTION)) }
        extractor.texts.clear()
        extractor.answer = { job() }
    }

    private fun preview(sub: String, body: String): ResultActions =
        mvc.perform(post("/api/job-imports/preview").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun previewUrl(sub: String, url: String) = preview(sub, """{"url":"$url"}""")

    private fun confirm(sub: String, body: String): ResultActions =
        mvc.perform(post("/api/job-imports").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun field(result: ResultActions, path: String): String = JsonPath.read(result.andReturn().response.contentAsString, path)

    private fun draftId(sub: String, url: String = "https://jobs.example.com/ada-${unique()}"): String =
        field(previewUrl(sub, url).andExpect(status().isOk), "$.draftId")

    private fun count(sql: String, vararg args: Any) = jdbc.queryForObject(sql, Int::class.java, *args)!!

    @Test
    fun `a readable job page gives a draft and creates nothing`() {
        val sub = newSub()
        val before = count("select count(*) from posting_source where source_id = 'user-import'")

        val result = previewUrl(sub, "https://jobs.example.com/ada-engineer").andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Backend Engineer"))
            .andExpect(jsonPath("$.company").value("Employer1 AG"))
            .andExpect(jsonPath("$.location").value("Zurich"))
            .andExpect(jsonPath("$.description").value(DESCRIPTION))
            .andExpect(jsonPath("$.language").value("EN"))

        val id = field(result, "$.draftId")
        assertEquals(1, count("select count(*) from job_import_draft where id = ?::uuid and owner = ?", id, sub))
        assertEquals(before, count("select count(*) from posting_source where source_id = 'user-import'"))
        assertEquals(0, count("select count(*) from application where owner_sub = ?", sub))
    }

    @Test
    fun `a page that is not a job ad is 422 not_a_job and stores no draft`() {
        val sub = newSub()
        extractor.answer = { JobDraft(false, "", "", null, "", LetterLanguage.EN) }

        previewUrl(sub, "https://blog.example.com/post").andExpect(status().isUnprocessableEntity).andExpect(jsonPath("$.error").value("not_a_job"))

        assertEquals(0, count("select count(*) from job_import_draft where owner = ?", sub))
    }

    @Test
    fun `a page with under 300 characters is 422 unreadable and tells the user to paste`() {
        val sub = newSub()
        fetcher.answer = { FetchResult.Page(page("Please enable JavaScript.")) }

        previewUrl(sub, "https://spa.example.com/job").andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value("unreadable"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Paste")))

        assertTrue(extractor.texts.isEmpty())
    }

    @Test
    fun `a description the page does not contain is 502 extraction_failed and stores no draft`() {
        val sub = newSub()
        extractor.answer = { job(description = "Totally different invented words about unicorn wrangling and rainbow stewardship " + "x".repeat(10)) }

        previewUrl(sub, "https://jobs.example.com/ada").andExpect(status().isBadGateway).andExpect(jsonPath("$.error").value("extraction_failed"))

        assertEquals(0, count("select count(*) from job_import_draft where owner = ?", sub))
    }

    @Test
    fun `a model that is not configured is 502 llm_unavailable`() {
        extractor.answer = { throw LlmFailure(LlmFailureKind.NOT_CONFIGURED) }

        previewUrl(newSub(), "https://jobs.example.com/ada").andExpect(status().isBadGateway).andExpect(jsonPath("$.error").value("llm_unavailable"))
    }

    @Test
    fun `pasted text makes no fetch and gives a draft`() {
        val sub = newSub()
        val text = DESCRIPTION + " " + DESCRIPTION

        preview(sub, """{"url":"https://jobs.example.com/ada","text":"$text"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.draftId").exists())

        assertTrue(fetcher.urls.isEmpty())
        assertEquals(text, extractor.texts.single())
    }

    @Test
    fun `fetch failures map to their status and code`() {
        val expected = mapOf(
            FetchError.BLOCKED_ADDRESS to (400 to "blocked_address"),
            FetchError.NOT_HTML to (422 to "not_html"),
            FetchError.TOO_LARGE to (422 to "too_large"),
            FetchError.UNREACHABLE to (502 to "unreachable"),
        )
        for ((error, outcome) in expected) {
            fetcher.answer = { FetchResult.Failed(error) }
            previewUrl(newSub(), "https://jobs.example.com/ada").andExpect(status().`is`(outcome.first)).andExpect(jsonPath("$.error").value(outcome.second))
        }
    }

    @Test
    fun `bad urls and unsupported sites are 400 and nothing is fetched`() {
        for (url in listOf("http://jobs.example.com/a", "https://203.0.113.5/a", "https://jobs.example.com:8443/a", "https://user@jobs.example.com/a", "")) {
            previewUrl(newSub(), url).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_url"))
        }
        previewUrl(newSub(), "https://www.linkedin.com/jobs/1").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("unsupported_site"))
        mvc.perform(post("/api/job-imports/preview").header("Authorization", bearer(newSub())).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_url"))

        assertTrue(fetcher.urls.isEmpty())
    }

    @Test
    fun `the endpoints need a token`() {
        mvc.perform(post("/api/job-imports/preview").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/job-imports").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a new job becomes a user-added posting and an interested application with the draft text`() {
        val sub = newSub()
        val title = "Backend Engineer ${unique()}"
        extractor.answer = { job(title = title) }
        val id = draftId(sub)

        val result = confirm(sub, """{"draftId":"$id"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.created").value(true)).andExpect(jsonPath("$.postingExisted").value(false))
        val postingId = field(result, "$.postingId")
        val applicationId = field(result, "$.applicationId")

        mvc.perform(get("/api/postings/$postingId").header("Authorization", bearer(sub))).andExpect(status().isOk)
            .andExpect(jsonPath("$.userAdded").value(true)).andExpect(jsonPath("$.title").value(title))
        mvc.perform(get("/api/applications/$applicationId").header("Authorization", bearer(sub))).andExpect(status().isOk)
            .andExpect(jsonPath("$.stage").value("INTERESTED")).andExpect(jsonPath("$.description").value(DESCRIPTION))
        assertEquals(0, count("select count(*) from job_import_draft where id = ?::uuid", id))
        assertEquals(1, count("select count(*) from posting_import where posting_id = ?::uuid and user_sub = ?", postingId, sub))
        assertEquals(DESCRIPTION, jdbc.queryForObject("select raw_payload from posting where id = ?::uuid", String::class.java, postingId))
    }

    @Test
    fun `edits replace the draft fields and are checked against their limits`() {
        val sub = newSub()
        val id = draftId(sub)

        confirm(sub, """{"draftId":"$id","title":"  "}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_edit"))
        confirm(sub, """{"draftId":"$id","company":"${"c".repeat(201)}"}""").andExpect(status().isBadRequest)
        confirm(sub, """{"draftId":"$id","description":"<script>x</script>"}""").andExpect(status().isBadRequest)
        val title = "Edited Engineer ${unique()}"
        val result = confirm(sub, """{"draftId":"$id","title":"$title","location":"Bern"}""").andExpect(status().isCreated)

        mvc.perform(get("/api/postings/${field(result, "$.postingId")}").header("Authorization", bearer(sub)))
            .andExpect(jsonPath("$.title").value(title)).andExpect(jsonPath("$.city").value("Bern"))
    }

    @Test
    fun `the same canonical url is the same posting and the second user finds it known`() {
        val a = newSub()
        val b = newSub()
        val path = "ada-${unique()}"
        extractor.answer = { job(title = "Backend Engineer ${unique()}") }
        val first = confirm(a, """{"draftId":"${draftId(a, "https://jobs.example.com/$path")}"}""").andExpect(status().isCreated)

        val second = confirm(b, """{"draftId":"${draftId(b, "https://Jobs.Example.com/$path/?utm_source=x#top")}","title":"Rewritten"}""")
            .andExpect(status().isCreated).andExpect(jsonPath("$.created").value(true)).andExpect(jsonPath("$.postingExisted").value(true))

        assertEquals(field(first, "$.postingId"), field(second, "$.postingId"))
        mvc.perform(get("/api/postings/${field(first, "$.postingId")}").header("Authorization", bearer(b)))
            .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.startsWith("Backend Engineer")))
    }

    @Test
    fun `the same company, title and city under another url is the existing posting`() {
        val a = newSub()
        val b = newSub()
        val title = "Backend Engineer ${unique()}"
        extractor.answer = { job(title = title) }
        val first = confirm(a, """{"draftId":"${draftId(a)}"}""").andExpect(status().isCreated)

        val second = confirm(b, """{"draftId":"${draftId(b)}"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.postingExisted").value(true))

        assertEquals(field(first, "$.postingId"), field(second, "$.postingId"))
    }

    @Test
    fun `a job the user already tracks gives created false and the same application`() {
        val sub = newSub()
        val url = "https://jobs.example.com/ada-${unique()}"
        val first = confirm(sub, """{"draftId":"${draftId(sub, url)}"}""").andExpect(status().isCreated)

        val second = confirm(sub, """{"draftId":"${draftId(sub, url)}"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.created").value(false)).andExpect(jsonPath("$.postingExisted").value(true))

        assertEquals(field(first, "$.applicationId"), field(second, "$.applicationId"))
    }

    @Test
    fun `another user's draft, an expired draft and an unknown id are 404 draft_not_found and create nothing`() {
        val a = newSub()
        val b = newSub()
        val id = draftId(a)
        val postings = count("select count(*) from posting")

        confirm(b, """{"draftId":"$id"}""").andExpect(status().isNotFound).andExpect(jsonPath("$.error").value("draft_not_found"))
        confirm(a, """{"draftId":"not-a-uuid"}""").andExpect(status().isNotFound).andExpect(jsonPath("$.error").value("draft_not_found"))
        confirm(a, """{"draftId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)
        jdbc.update("update job_import_draft set expires_at = now() - interval '1 minute' where id = ?::uuid", id)
        confirm(a, """{"draftId":"$id"}""").andExpect(status().isNotFound).andExpect(jsonPath("$.error").value("draft_not_found"))

        assertEquals(postings, count("select count(*) from posting"))
    }

    @Test
    fun `the purge removes expired drafts only`() {
        val sub = newSub()
        val old = draftId(sub)
        val fresh = draftId(sub)
        jdbc.update("update job_import_draft set expires_at = now() - interval '1 minute' where id = ?::uuid", old)

        assertTrue(drafts.purgeExpired(java.time.Instant.now()) >= 1)
        assertEquals(0, count("select count(*) from job_import_draft where id = ?::uuid", old))
        assertEquals(1, count("select count(*) from job_import_draft where id = ?::uuid", fresh))
    }

    @Test
    fun `the fourth preview in an hour is 429 with Retry-After and makes no fetch or model call`() {
        val sub = newSub()
        repeat(3) { previewUrl(sub, "https://jobs.example.com/ada-${unique()}").andExpect(status().isOk) }
        val fetches = fetcher.urls.size
        val calls = extractor.texts.size

        previewUrl(sub, "https://jobs.example.com/ada").andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("rate_limited")).andExpect(header().exists("Retry-After"))

        assertEquals(fetches, fetcher.urls.size)
        assertEquals(calls, extractor.texts.size)
        previewUrl(newSub(), "https://jobs.example.com/ada").andExpect(status().isOk)
    }

    @Test
    fun `the third confirm in a day is 429`() {
        val sub = newSub()
        val ids = List(3) { draftId(sub) }
        extractor.answer = { job(title = "Backend Engineer ${unique()}") }

        confirm(sub, """{"draftId":"${ids[0]}"}""").andExpect(status().isCreated)
        confirm(sub, """{"draftId":"${ids[1]}"}""").andExpect(status().isCreated)
        confirm(sub, """{"draftId":"${ids[2]}"}""").andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("rate_limited")).andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `no user id or import record appears in any response`() {
        val sub = newSub()
        extractor.answer = { job(title = "Backend Engineer ${unique()}") }
        val id = draftId(sub)
        val confirmed = confirm(sub, """{"draftId":"$id"}""").andExpect(status().isCreated)
        val postingId = field(confirmed, "$.postingId")
        val other = newSub()

        val bodies = listOf(
            confirmed.andReturn().response.contentAsString,
            mvc.perform(get("/api/postings/$postingId").header("Authorization", bearer(other))).andReturn().response.contentAsString,
            mvc.perform(get("/api/postings").header("Authorization", bearer(other))).andReturn().response.contentAsString,
            mvc.perform(get("/api/applications/${field(confirmed, "$.applicationId")}").header("Authorization", bearer(sub))).andReturn().response.contentAsString,
        )

        for (body in bodies) {
            assertFalse(sub in body, "the importing user id appears in a response")
            assertFalse("posting_import" in body || "userSub" in body || "user_sub" in body)
        }
    }

    @Test
    fun `logs carry no url, page text or extracted text`() {
        val sub = newSub()
        val marker = "zq" + unique()
        val url = "https://jobs.example.com/$marker"
        val title = "Backend Engineer $marker"
        extractor.answer = { job(title = title, company = "Employer1 AG $marker", description = DESCRIPTION) }
        fetcher.answer = { FetchResult.Page(page("$DESCRIPTION $marker")) }

        val events = captureAppLogs {
            val id = field(previewUrl(sub, url).andExpect(status().isOk), "$.draftId")
            confirm(sub, """{"draftId":"$id"}""").andExpect(status().isCreated)
            previewUrl(sub, "http://jobs.example.com/$marker").andExpect(status().isBadRequest)
            extractor.answer = { throw LlmFailure(LlmFailureKind.FAILED) }
            previewUrl(sub, url).andExpect(status().isBadGateway)
        }

        val text = events.joinToString("\n") { it.fullText() }
        assertTrue("Import preview ok" in text && "Import confirm ok" in text && "code=invalid_url" in text && "code=extraction_failed" in text, text)
        for (secret in listOf(marker, "example.com", "Employer1", "PostgreSQL", "reliable services")) {
            assertFalse(secret in text, "log output contains: $secret")
        }
    }

    @Test
    fun `removing a user's data deletes their drafts and import rows and keeps the posting`() {
        val sub = newSub()
        val other = newSub()
        extractor.answer = { job(title = "Backend Engineer ${unique()}") }
        val posting = field(confirm(sub, """{"draftId":"${draftId(sub)}"}""").andExpect(status().isCreated), "$.postingId")
        val pending = draftId(sub)
        val othersDraft = draftId(other)

        service.removeUserData(sub)

        assertEquals(0, count("select count(*) from job_import_draft where id = ?::uuid", pending))
        assertEquals(0, count("select count(*) from posting_import where user_sub = ?", sub))
        assertEquals(1, count("select count(*) from posting where id = ?::uuid", posting))
        assertEquals(1, count("select count(*) from job_import_draft where id = ?::uuid", othersDraft))
    }

    @Test
    fun `grounding needs 80 percent of the description words on the page`() {
        val page = "alpha beta gamma delta epsilon zeta eta theta iota kappa"

        assertTrue(Grounding.isGrounded("Alpha beta gamma delta epsilon zeta eta theta", page))
        assertTrue(Grounding.isGrounded("alpha beta gamma delta other", page))
        assertFalse(Grounding.isGrounded("alpha beta gamma other another", page))
        assertFalse(Grounding.isGrounded("   ", page))
    }
}
