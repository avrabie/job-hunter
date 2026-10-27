package net.s4v3.backend

import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val publicIp = AtomicInteger(1)

/**
 * GET /api/public/postings with no token. The database is shared with the other tests, so every test works in its own
 * canton (and old dates where it needs exact counts) and uses its own client address.
 */
@ApiTest
class PublicPostingsApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val zurich = ZoneId.of("Europe/Zurich")
    private fun unique() = "uq" + UUID.randomUUID().toString().replace("-", "").take(10)
    private fun newIp() = publicIp.getAndIncrement().let { "203.0.${it / 250}.${it % 250 + 1}" }

    private fun add(
        canton: String, seen: Instant = Instant.now(), language: String? = null, raw: String = "{}", description: String? = null,
    ): UUID {
        val source = "pub-src-" + unique()
        val id = ingest.ingest(
            PostingCandidate(
                sourceId = source, sourcePostingId = unique(), url = "https://example.org/$source/" + unique(),
                title = "Public Dev " + unique(), company = "Pubco " + unique(), rawPayload = raw, city = "Somewhere",
                canton = canton,
                description = description ?: ("We are looking for an experienced engineer to build and run reliable services " +
                    "with Kotlin. You will work in a small team, review code, and improve our delivery pipeline every week."),
            ),
        ).postingId
        jdbc.update("UPDATE posting SET first_seen = ? WHERE id = ?", Timestamp.from(seen), id)
        if (language != null) jdbc.update("UPDATE posting SET language = ? WHERE id = ?", language, id)
        return id
    }

    private fun list(query: String, ip: String = newIp()): ResultActions =
        mvc.perform(get("/api/public/postings$query").header("X-Forwarded-For", ip))

    private fun ids(result: ResultActions): List<String> {
        val body = result.andReturn().response.contentAsString
        return JsonMapper().readTree(body).path("postings").toList().map { it.path("id").asString() }
    }

    private fun daysAgo(days: Long) = Instant.now().minus(days, ChronoUnit.DAYS)

    @Test
    fun `default list needs no token, is newest first and holds links but no description`() {
        val old = add("GL", seen = daysAgo(3))
        val newer = add("GL", seen = daysAgo(1))
        add("GL", seen = daysAgo(40))

        val result = list("?canton=GL").andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.postings[0].id").value(newer.toString()))
            .andExpect(jsonPath("$.postings[1].id").value(old.toString()))
            .andExpect(jsonPath("$.postings[0].links[0].label").exists())
            .andExpect(jsonPath("$.postings[0].links[0].url").exists())
            .andExpect(jsonPath("$.postings[0].description").doesNotExist())

        assertEquals(listOf(newer.toString(), old.toString()), ids(result))
    }

    @Test
    fun `days=14 lists the posting from 3 days ago and not the one from 20 days ago`() {
        val recent = add("UR", seen = daysAgo(3))
        add("UR", seen = daysAgo(20))

        list("?canton=UR&days=14").andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.postings.length()").value(1))
            .andExpect(jsonPath("$.postings[0].id").value(recent.toString()))
        list("?canton=UR&days=31").andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun `since and until include both days in Zurich time`() {
        val since = LocalDate.now(zurich).minusDays(80)
        val until = since.plusDays(6)
        fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zurich).toInstant()
        val first = add("NW", seen = at(since, 0))
        val middle = add("NW", seen = at(since.plusDays(3), 12))
        val last = add("NW", seen = at(until, 23, 59))
        add("NW", seen = at(since.minusDays(1), 23, 59))
        add("NW", seen = at(until.plusDays(1), 0))

        val result = list("?canton=NW&since=$since&until=$until").andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(3))

        assertEquals(listOf(last, middle, first).map { it.toString() }, ids(result))
    }

    @Test
    fun `paging is stable when postings share one first-seen time`() {
        val at = daysAgo(2).truncatedTo(ChronoUnit.SECONDS)
        val all = (1..3).map { add("OW", seen = at) }.map { it.toString() }

        val first = ids(list("?canton=OW&size=2&page=0&days=5"))
        val second = ids(list("?canton=OW&size=2&page=1&days=5"))

        assertEquals(2, first.size)
        assertEquals(1, second.size)
        assertEquals(all.sorted(), (first + second).sorted())
        assertEquals(all.sorted(), (first + second))
        list("?canton=OW&days=5").andExpect(jsonPath("$.total").value(3))
    }

    @Test
    fun `size is capped at 50`() {
        list("?size=500").andExpect(status().isOk).andExpect(jsonPath("$.size").value(50))
            .andExpect(jsonPath("$.postings.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(50)))
    }

    @Test
    fun `bad windows and parameters are 400`() {
        val today = LocalDate.now(zurich)
        val bad = listOf(
            "since=${today.minusDays(40)}&until=$today",
            "since=${today.minusDays(120)}",
            "since=${today.minusDays(120)}&until=${today.minusDays(115)}",
            "since=${today.minusDays(5)}&until=${today.minusDays(9)}",
            "page=-1", "days=0", "days=32", "days=abc", "page=100&size=20", "page=2000&size=500", "size=0", "page=x",
            "language=fr", "language=it", "canton=ZUR", "canton=1A", "since=yesterday", "until=2026-13-40",
            "days=7&since=$today",
        )
        bad.forEach { query -> list("?$query").andExpect(status().isBadRequest) }
    }

    @Test
    fun `limits at their edge are accepted`() {
        val today = LocalDate.now(zurich)
        list("?page=50&size=20").andExpect(status().isOk)
        list("?days=31").andExpect(status().isOk)
        list("?since=${today.minusDays(30)}&until=$today").andExpect(status().isOk)
        list("?since=${today.minusDays(90)}&until=${today.minusDays(60)}").andExpect(status().isOk)
    }

    @Test
    fun `neither the description, the raw payload nor the last-seen time is in the answer`() {
        val id = add(
            "AI", raw = """{"secret":"RAWMARKER5531"}""",
            description = "DESCMARKER7742 We are looking for an experienced engineer to build and run reliable services with Kotlin. " +
                "You will work in a small team, review code, and improve our delivery pipeline every week.",
        )

        val result = list("?canton=AI").andExpect(status().isOk)
        val body = result.andReturn().response.contentAsString

        assertTrue(id.toString() in body)
        assertFalse("RAWMARKER5531" in body)
        assertFalse("DESCMARKER7742" in body)
        assertFalse("lastSeen" in body)
        val keys = JsonMapper().readTree(body).path("postings").first().propertyNames().toSet()
        assertEquals(
            setOf("id", "title", "company", "city", "canton", "remoteMode", "salaryMin", "salaryMax", "language", "firstSeen", "links"),
            keys,
        )
    }

    @Test
    fun `canton and language filters combine`() {
        val match = add("AR", language = "de")
        add("AR", language = "en")
        add("SH", language = "de")

        val result = list("?canton=ar&language=de").andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))

        assertEquals(listOf(match.toString()), ids(result))
        list("?canton=AR&language=DE").andExpect(jsonPath("$.total").value(1))
    }

    @Test
    fun `an answer may be cached publicly for 60 seconds, an error may not`() {
        list("").andExpect(status().isOk).andExpect(header().string("Cache-Control", "max-age=60, public"))
        list("?language=fr").andExpect(status().isBadRequest).andExpect(header().string("Cache-Control", "no-store"))
    }

    @Test
    fun `the 61st request from one address is 429 with Retry-After and another address is served`() {
        val ip = newIp()
        repeat(60) { list("?size=1", ip).andExpect(status().isOk) }

        list("?size=1", ip).andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(header().string("Cache-Control", "no-store"))
        list("?size=1").andExpect(status().isOk)
    }

    @Test
    fun `the first X-Forwarded-For value is the client, else the peer address`() {
        val ip = newIp()
        repeat(60) {
            mvc.perform(get("/api/public/postings?size=1").header("X-Forwarded-For", "$ip, 10.1.1.1")).andExpect(status().isOk)
        }

        mvc.perform(get("/api/public/postings?size=1").header("X-Forwarded-For", "$ip, 10.9.9.9")).andExpect(status().isTooManyRequests)
        mvc.perform(get("/api/public/postings?size=1").header("X-Forwarded-For", "${newIp()}, $ip")).andExpect(status().isOk)
    }
}
