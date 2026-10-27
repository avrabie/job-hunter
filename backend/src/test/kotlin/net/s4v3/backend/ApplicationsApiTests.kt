package net.s4v3.backend

import com.jayway.jsonpath.JsonPath
import net.s4v3.backend.applications.ApplicationLimitException
import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.applications.JobData
import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tracking jobs over MockMvc with real tokens. Everything is synthetic: "Employer1 AG" and invented job texts. */
@ApiTest
internal class ApplicationsApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val applications: ApplicationService,
) {

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "apps-api-" + UUID.randomUUID()

    /** A new user whose row exists (any authenticated call creates it), for tests that call the service directly. */
    private fun provisionedSub(): String = newSub().also {
        mvc.perform(get("/api/applications").header("Authorization", bearer(it))).andExpect(status().isOk)
    }

    private val body = "We are looking for an experienced engineer to build and run reliable services with Kotlin and PostgreSQL. " +
        "You will work in a small team, review code, and improve our delivery pipeline every week."

    private fun candidate(sourceId: String, description: String) = PostingCandidate(
        sourceId = sourceId, sourcePostingId = "1", url = "https://example.org/" + sourceId, title = "Backend Engineer " + sourceId.takeLast(8),
        company = "Employer1 AG", rawPayload = "{}", city = "Zurich", canton = "ZH", description = description,
    )

    private fun newPosting(description: String = body): Pair<String, UUID> {
        val source = "apps-src-" + UUID.randomUUID()
        return source to ingest.ingest(candidate(source, description)).postingId
    }

    private fun track(sub: String, posting: UUID): ResultActions =
        mvc.perform(post("/api/applications").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content("""{"postingId":"$posting"}"""))

    private fun idOf(result: ResultActions): String = JsonPath.read(result.andReturn().response.contentAsString, "$.id")

    private fun read(sub: String, id: String) = mvc.perform(get("/api/applications/$id").header("Authorization", bearer(sub)))

    private fun patchApp(sub: String, id: String, json: String) =
        mvc.perform(patch("/api/applications/$id").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(json))

    @Test
    fun `marking a posting interested creates an application with the frozen copy`() {
        val sub = newSub()
        val (_, posting) = newPosting()

        val id = idOf(track(sub, posting).andExpect(status().isCreated).andExpect(jsonPath("$.stage").value("INTERESTED")))

        read(sub, id).andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.startsWith("Backend Engineer")))
            .andExpect(jsonPath("$.company").value("Employer1 AG"))
            .andExpect(jsonPath("$.description").value(body))
            .andExpect(jsonPath("$.liveStatus").value("OPEN"))
            .andExpect(jsonPath("$.liveChanged").value(false))
            .andExpect(jsonPath("$.letterCount").value(0))
        mvc.perform(get("/api/applications").header("Authorization", bearer(sub)))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].description").doesNotExist()).andExpect(jsonPath("$[0].notes").doesNotExist())
    }

    @Test
    fun `tracking twice leaves one application with its stage and notes unchanged`() {
        val sub = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))
        patchApp(sub, id, """{"stage":"APPLYING","notes":"call on monday"}""").andExpect(status().isOk)

        track(sub, posting).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.stage").value("APPLYING"))
            .andExpect(jsonPath("$.notes").value("call on monday"))

        assertEquals(1, jdbc.queryForObject("select count(*) from application where owner_sub = ?", Int::class.java, sub))
    }

    @Test
    fun `an unknown posting answers 404 and a request without a token 401`() {
        track(newSub(), UUID.randomUUID()).andExpect(status().isNotFound)
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""{"postingId":"${UUID.randomUUID()}"}"""))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `the frozen description stays when the posting changes and the application says so`() {
        val sub = newSub()
        val (source, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))

        ingest.ingest(candidate(source, "$body Updated: we now also run a second team in Bern."))

        read(sub, id).andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value(body))
            .andExpect(jsonPath("$.liveChanged").value(true))
            .andExpect(jsonPath("$.liveStatus").value("CHANGED"))
        mvc.perform(get("/api/applications").header("Authorization", bearer(sub))).andExpect(jsonPath("$[0].liveStatus").value("CHANGED"))
    }

    @Test
    fun `a posting not seen for 14 days is gone`() {
        val sub = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))

        jdbc.update("update posting set last_seen = now() - interval '15 days' where id = ?", posting)

        read(sub, id).andExpect(jsonPath("$.liveStatus").value("GONE"))
        mvc.perform(get("/api/applications").header("Authorization", bearer(sub))).andExpect(jsonPath("$[0].liveStatus").value("GONE"))
    }

    @Test
    fun `moving to applied stores the stage, the change time and the applied date`() {
        val sub = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))
        val before: String = JsonPath.read(read(sub, id).andReturn().response.contentAsString, "$.stageChangedAt")

        Thread.sleep(20)
        patchApp(sub, id, """{"stage":"APPLIED"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.stage").value("APPLIED"))
            .andExpect(jsonPath("$.appliedOn").value(LocalDate.now(java.time.ZoneOffset.UTC).toString()))
        val after: String = JsonPath.read(read(sub, id).andReturn().response.contentAsString, "$.stageChangedAt")
        assertNotEquals(before, after)

        patchApp(sub, id, """{"appliedOn":"2026-01-15"}""").andExpect(jsonPath("$.appliedOn").value("2026-01-15"))
        // Any stage to any other stage; notes alone do not move the change time.
        patchApp(sub, id, """{"notes":"waiting"}""").andExpect(jsonPath("$.stageChangedAt").value(after))
        patchApp(sub, id, """{"stage":"INTERESTED"}""").andExpect(jsonPath("$.stage").value("INTERESTED"))
        patchApp(sub, id, """{"stage":"REJECTED"}""").andExpect(jsonPath("$.stage").value("REJECTED"))
    }

    @Test
    fun `an unknown stage, a bad date or notes over 5000 characters answer 400`() {
        val sub = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))

        patchApp(sub, id, """{"stage":"HIRED"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("stage_invalid"))
        patchApp(sub, id, """{"appliedOn":"yesterday"}""").andExpect(status().isBadRequest)
        patchApp(sub, id, """{"notes":"${"n".repeat(5001)}"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("notes_invalid"))
        patchApp(sub, id, """{"notes":"${"n".repeat(5000)}"}""").andExpect(status().isOk)
        mvc.perform(get("/api/applications?stage=HIRED").header("Authorization", bearer(sub))).andExpect(status().isBadRequest)
    }

    @Test
    fun `the list is newest change first and can be filtered by stage`() {
        val sub = newSub()
        val ids = (1..3).map { idOf(track(sub, newPosting().second).andExpect(status().isCreated)) }
        Thread.sleep(20)
        patchApp(sub, ids[0], """{"stage":"APPLIED"}""").andExpect(status().isOk)

        mvc.perform(get("/api/applications").header("Authorization", bearer(sub)))
            .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].id").value(ids[0]))
        mvc.perform(get("/api/applications?stage=INTERESTED").header("Authorization", bearer(sub)))
            .andExpect(jsonPath("$.length()").value(2))
        mvc.perform(get("/api/applications?stage=APPLIED").header("Authorization", bearer(sub)))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `another user's application answers 404 for every read and write`() {
        val owner = newSub()
        val other = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(owner, posting).andExpect(status().isCreated))
        // The other user exists (the filter provisions it) and tracks the same posting separately.
        val otherId = idOf(track(other, posting).andExpect(status().isCreated))
        assertNotEquals(id, otherId)
        val stranger = newSub()

        read(stranger, id).andExpect(status().isNotFound)
        patchApp(stranger, id, """{"stage":"APPLIED"}""").andExpect(status().isNotFound)
        mvc.perform(delete("/api/applications/$id").header("Authorization", bearer(stranger))).andExpect(status().isNotFound)
        read(stranger, UUID.randomUUID().toString()).andExpect(status().isNotFound)
        mvc.perform(get("/api/applications").header("Authorization", bearer(stranger))).andExpect(jsonPath("$.length()").value(0))
        read(owner, id).andExpect(status().isOk).andExpect(jsonPath("$.stage").value("INTERESTED"))
    }

    @Test
    fun `the 501st application answers 409 and tracking an existing one still works`() {
        val sub = newSub()
        val (_, first) = newPosting()
        track(sub, first).andExpect(status().isCreated)
        jdbc.update(
            """
            insert into application (id, owner_sub, posting_id, stage, title, company, url, description, job_language, captured_at,
                content_hash, stage_changed_at, created_at, updated_at)
            select gen_random_uuid(), ?, gen_random_uuid(), 'INTERESTED', 't', 'c', 'https://example.org', 'd', 'en', now(), 'h', now(), now(), now()
            from generate_series(1, 499)
            """.trimIndent(),
            sub,
        )
        val (_, extra) = newPosting()

        track(sub, extra).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("application_limit"))
        track(sub, first).andExpect(status().isOk)
        assertEquals(500, jdbc.queryForObject("select count(*) from application where owner_sub = ?", Int::class.java, sub))
    }

    @Test
    fun `by-posting answers the stages of the tracked postings only and caps the ids at 100`() {
        val sub = newSub()
        val (_, tracked) = newPosting()
        val (_, untracked) = newPosting()
        val id = idOf(track(sub, tracked).andExpect(status().isCreated))

        mvc.perform(get("/api/applications/by-posting?ids=$tracked,$untracked").header("Authorization", bearer(sub)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$['$tracked'].stage").value("INTERESTED"))
            .andExpect(jsonPath("$['$tracked'].applicationId").value(id))
            .andExpect(jsonPath("$['$untracked']").doesNotExist())
        mvc.perform(get("/api/applications/by-posting?ids=$tracked").header("Authorization", bearer(newSub())))
            .andExpect(status().isOk).andExpect(jsonPath("$['$tracked']").doesNotExist())
        val tooMany = (1..101).joinToString(",") { UUID.randomUUID().toString() }
        mvc.perform(get("/api/applications/by-posting?ids=$tooMany").header("Authorization", bearer(sub))).andExpect(status().isBadRequest)
        mvc.perform(get("/api/applications/by-posting?ids=not-an-id").header("Authorization", bearer(sub))).andExpect(status().isBadRequest)
    }

    @Test
    fun `removing an application deletes its letters too`() {
        val sub = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))
        jdbc.update(
            "insert into motivation_letter (id, application_id, owner_sub, version, language, text, how, created_at) values (gen_random_uuid(), ?::uuid, ?, 1, 'EN', 'Dear team', 'GENERATED', now())",
            id, sub,
        )
        read(sub, id).andExpect(jsonPath("$.letterCount").value(1))

        mvc.perform(delete("/api/applications/$id").header("Authorization", bearer(sub))).andExpect(status().isNoContent)

        read(sub, id).andExpect(status().isNotFound)
        assertEquals(0, jdbc.queryForObject("select count(*) from motivation_letter where application_id = ?::uuid", Int::class.java, id))
    }

    @Test
    fun `descriptions and notes never reach the logs`() {
        val sub = newSub()
        val (_, posting) = newPosting("$body DESCMARKER7731 invented perks for Employer1 AG.")
        var id = ""
        val events = captureAppLogs {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            id = idOf(track(sub, posting).andExpect(status().isCreated))
            patchApp(sub, id, """{"stage":"APPLIED","notes":"NOTESMARKER7731 private thoughts"}""").andExpect(status().isOk)
            read(sub, id).andExpect(status().isOk)
            mvc.perform(get("/api/applications").header("Authorization", bearer(sub))).andExpect(status().isOk)
            mvc.perform(delete("/api/applications/$id").header("Authorization", bearer(sub))).andExpect(status().isNoContent)
        }

        val relevant = events.filter { e -> listOf("org.springframework.jdbc.core.", "org.postgresql.").none { e.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { it.fullText() }
        assertTrue("capture alive" in logged, "the capture does not work, so it proves nothing")
        assertTrue(relevant.any { it.loggerName.startsWith("net.s4v3.backend.applications") }, "our own applications code logged nothing")
        listOf("DESCMARKER7731", "NOTESMARKER7731", "Employer1 AG", "private thoughts").forEach {
            assertFalse(it in logged, "$it appears in the logs")
        }
    }

    private fun importedJob(url: String = "https://jobs.example.com/" + UUID.randomUUID()) =
        JobData(url, "Imported Engineer", "Employer1 AG", body, "en")

    @Test
    fun `an imported job is an application without a posting, always open and unchanged`() {
        val sub = provisionedSub()
        val postings = jdbc.queryForObject("select count(*) from posting", Int::class.java)

        val result = applications.trackImported(sub, importedJob())

        assertTrue(result.created)
        assertEquals(postings, jdbc.queryForObject("select count(*) from posting", Int::class.java))
        read(sub, result.application.id.toString()).andExpect(status().isOk)
            .andExpect(jsonPath("$.stage").value("INTERESTED")).andExpect(jsonPath("$.liveStatus").value("OPEN"))
            .andExpect(jsonPath("$.liveChanged").value(false)).andExpect(jsonPath("$.postingId").doesNotExist())
            .andExpect(jsonPath("$.description").value(body))
        mvc.perform(get("/api/applications").header("Authorization", bearer(sub))).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].liveStatus").value("OPEN"))
        assertEquals("IMPORT", jdbc.queryForObject("select source from application where id = ?", String::class.java, result.application.id))
    }

    @Test
    fun `the same url imported twice by one user is one application, and another user has their own`() {
        val a = provisionedSub()
        val b = provisionedSub()
        val job = importedJob()

        val first = applications.trackImported(a, job)
        val second = applications.trackImported(a, job.copy(title = "Other title"))
        val others = applications.trackImported(b, job)

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.application.id, second.application.id)
        assertEquals("Imported Engineer", second.application.title)
        assertTrue(others.created)
        assertNotEquals(first.application.id, others.application.id)
    }

    @Test
    fun `another user's imported application answers 404 for every read and write`() {
        val a = provisionedSub()
        val b = provisionedSub()
        val id = applications.trackImported(a, importedJob()).application.id.toString()

        read(b, id).andExpect(status().isNotFound)
        patchApp(b, id, """{"stage":"APPLIED"}""").andExpect(status().isNotFound)
        mvc.perform(delete("/api/applications/$id").header("Authorization", bearer(b))).andExpect(status().isNotFound)
        mvc.perform(get("/api/applications").header("Authorization", bearer(b))).andExpect(jsonPath("$.length()").value(0))
        read(a, id).andExpect(status().isOk)
    }

    @Test
    fun `an imported application counts towards the 500 limit`() {
        val sub = provisionedSub()
        repeat(ApplicationService.MAX_PER_USER) { applications.trackImported(sub, importedJob()) }

        assertFailsWith<ApplicationLimitException> { applications.trackImported(sub, importedJob()) }
    }

    @Test
    fun `the RAV proof of an applied job gives the title and the original link and null for what is not stored`() {
        val sub = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))
        patchApp(sub, id, """{"stage":"APPLIED"}""").andExpect(status().isOk)

        mvc.perform(get("/api/applications/$id/rav").header("Authorization", bearer(sub))).andExpect(status().isOk)
            .andExpect(jsonPath("$.position").value(org.hamcrest.Matchers.startsWith("Backend Engineer")))
            .andExpect(jsonPath("$.postingUrl").value(org.hamcrest.Matchers.startsWith("https://example.org/")))
            .andExpect(jsonPath("$.companyAddress").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.contactPerson").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.contactPhone").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.contactEmail").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.postalCode").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.description").doesNotExist())
    }

    @Test
    fun `the RAV proof answers 409 before the job is applied, for every earlier stage`() {
        val sub = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(sub, posting).andExpect(status().isCreated))

        for (stage in listOf("INTERESTED", "APPLYING", "REJECTED")) {
            patchApp(sub, id, """{"stage":"$stage"}""").andExpect(status().isOk)
            mvc.perform(get("/api/applications/$id/rav").header("Authorization", bearer(sub)))
                .andExpect(status().isConflict).andExpect(jsonPath("$.error").value("not_applied"))
        }
    }

    @Test
    fun `the RAV proof answers 404 for another user's or an unknown application and 401 without a token`() {
        val owner = newSub()
        val other = newSub()
        val (_, posting) = newPosting()
        val id = idOf(track(owner, posting).andExpect(status().isCreated))
        patchApp(owner, id, """{"stage":"APPLIED"}""").andExpect(status().isOk)

        mvc.perform(get("/api/applications/$id/rav").header("Authorization", bearer(other))).andExpect(status().isNotFound)
        mvc.perform(get("/api/applications/${UUID.randomUUID()}/rav").header("Authorization", bearer(owner))).andExpect(status().isNotFound)
        mvc.perform(get("/api/applications/$id/rav")).andExpect(status().isUnauthorized)
    }
}
