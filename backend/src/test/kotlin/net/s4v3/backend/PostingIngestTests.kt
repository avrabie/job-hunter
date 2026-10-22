package net.s4v3.backend

import net.s4v3.backend.postings.IngestOutcome
import net.s4v3.backend.postings.JobIngested
import net.s4v3.backend.postings.Posting
import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.postings.PostingRejectedException
import net.s4v3.backend.postings.RemoteMode
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.EnableAsync
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

open class IngestedEvents {
    open val received = CopyOnWriteArrayList<JobIngested>()

    @ApplicationModuleListener
    open fun on(event: JobIngested) {
        received += event
    }
}

@TestConfiguration(proxyBeanMethods = false)
@EnableAsync
class IngestedEventsConfig {
    @Bean
    fun ingestedEvents() = IngestedEvents()
}

@IntegrationTest
@Import(IngestedEventsConfig::class)
class PostingIngestTests(
    @Autowired private val ingest: PostingIngest,
    @Autowired private val query: PostingQuery,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val events: IngestedEvents,
) {

    private val english = "We are looking for an experienced backend engineer to join our team and help us build and " +
        "operate reliable services for our customers across Europe."
    private val german = "Wir suchen eine erfahrene Softwareentwicklerin oder einen erfahrenen Softwareentwickler, " +
        "der mit uns moderne Backend-Systeme baut und zuverlässig betreibt."
    private val french = "Nous recherchons un développeur expérimenté pour rejoindre notre équipe à Genève et " +
        "construire des systèmes fiables pour nos clients dans toute la Suisse romande."

    /** Every test uses its own company and source ids so tests do not see each other's rows. */
    private fun unique() = UUID.randomUUID().toString().take(8)

    private fun candidate(
        company: String,
        source: String = "src-" + unique(),
        sourcePostingId: String = "1",
        title: String = "Senior Kotlin Developer",
        description: String = english,
        city: String? = "Zurich",
        raw: String = """{"id":"$sourcePostingId"}""",
        workloadText: String? = null,
    ) = PostingCandidate(
        sourceId = source, sourcePostingId = sourcePostingId, url = "https://$source.example/$sourcePostingId",
        title = title, company = company, description = description, rawPayload = raw, city = city,
        workloadText = workloadText,
    )

    private fun postingRows(company: String) =
        jdbc.queryForObject<Int>("select count(*) from posting where company = ?", company)

    private fun eventsFor(id: UUID) = events.received.filter { it.postingId == id }

    @Test
    fun `first sight creates a posting with normalized fields`() {
        val company = "Acme " + unique()
        val c = candidate(company, workloadText = "80-100%").copy(remoteMode = RemoteMode.HYBRID, salaryMin = 120000, salaryMax = 150000)

        val result = ingest.ingest(c)

        assertEquals(IngestOutcome.CREATED, result.outcome)
        val posting = assertNotNull(query.find(result.postingId))
        assertEquals(company, posting.company)
        assertEquals(80, posting.workloadMin)
        assertEquals(100, posting.workloadMax)
        assertEquals(RemoteMode.HYBRID, posting.remoteMode)
        assertEquals(120000, posting.salaryMin)
        assertEquals("en", posting.language)
        assertFalse(posting.outOfScope)
        assertEquals(c.url, posting.url)
    }

    @Test
    fun `a minimal posting leaves the optional fields unknown`() {
        val result = ingest.ingest(candidate("Min " + unique(), city = null))

        val posting = assertNotNull(query.find(result.postingId))
        assertNull(posting.city)
        assertNull(posting.canton)
        assertNull(posting.workloadMin)
        assertNull(posting.workloadMax)
        assertNull(posting.remoteMode)
        assertNull(posting.salaryMin)
    }

    @Test
    fun `a blank required field rejects the posting and stores nothing`() {
        val company = "Blank " + unique()
        val ok = candidate(company)
        val bad = mapOf(
            "title" to ok.copy(title = " "), "company" to ok.copy(company = ""), "sourceId" to ok.copy(sourceId = ""),
            "sourcePostingId" to ok.copy(sourcePostingId = ""), "url" to ok.copy(url = " "),
        )
        for ((field, candidate) in bad) {
            assertEquals(field, assertFailsWith<PostingRejectedException> { ingest.ingest(candidate) }.field)
        }
        assertEquals(0, postingRows(company))
    }

    @Test
    fun `the same posting twice is unchanged and its last-seen time moves on`() {
        val c = candidate("Repeat " + unique())
        val first = ingest.ingest(c)
        val seenFirst = assertNotNull(query.find(first.postingId)).lastSeen

        val second = ingest.ingest(c)

        assertEquals(first.postingId, second.postingId)
        assertEquals(IngestOutcome.UNCHANGED, second.outcome)
        assertEquals(1, postingRows(c.company))
        assertTrue(assertNotNull(query.find(first.postingId)).lastSeen.isAfter(seenFirst))
    }

    @Test
    fun `a changed description updates the posting and keeps its first-seen time`() {
        val c = candidate("Change " + unique())
        val first = ingest.ingest(c)
        val before = assertNotNull(query.find(first.postingId))

        val second = ingest.ingest(c.copy(description = english + " We also offer a hybrid setup."))

        assertEquals(IngestOutcome.UPDATED, second.outcome)
        val after = assertNotNull(query.find(first.postingId))
        assertTrue(after.description.endsWith("hybrid setup."))
        assertEquals(before.firstSeen, after.firstSeen)
    }

    @Test
    fun `the raw payload is stored unchanged and replaced by a newer one`() {
        val raw = "{\n  \"b\": 1,  \"a\": \"ü\"\n}  "
        val c = candidate("Raw " + unique(), raw = raw)
        val id = ingest.ingest(c).postingId
        fun stored() = jdbc.queryForObject<String>("select raw_payload from posting where id = ?", id)

        assertEquals(raw, stored())
        ingest.ingest(c.copy(rawPayload = "<html>newer</html>"))
        assertEquals("<html>newer</html>", stored())
    }

    @Test
    fun `the language follows the text, also after an update`() {
        val c = candidate("Lang " + unique(), description = german, title = "Softwareentwickler Kotlin")
        val id = ingest.ingest(c).postingId
        assertEquals("de", assertNotNull(query.find(id)).language)

        ingest.ingest(c.copy(description = english, title = "Kotlin Developer"))

        assertEquals("en", assertNotNull(query.find(id)).language)
    }

    @Test
    fun `a french posting is stored and flagged, and a very short one is flagged for review`() {
        val fr = ingest.ingest(candidate("Fr " + unique(), title = "Développeur Kotlin", description = french))
        val short = ingest.ingest(candidate("Short " + unique(), title = "Dev", description = "Kotlin"))

        val french = assertNotNull(query.find(fr.postingId))
        assertEquals("fr", french.language)
        assertTrue(french.outOfScope)
        val tooShort = assertNotNull(query.find(short.postingId))
        assertEquals("other", tooShort.language)
        assertTrue(tooShort.outOfScope)
        assertTrue(tooShort.needsReview)
    }

    @Test
    fun `the same ad on two sources is one canonical posting that the second source does not overwrite`() {
        val company = "Two " + unique()
        val a = candidate(company, title = "Senior Kotlin Developer (m/w/d)", city = "Zürich")
        val b = candidate(company, title = "senior kotlin developer", city = "Zurich", description = english + " Extra words.")

        val first = ingest.ingest(a)
        val second = ingest.ingest(b)

        assertEquals(first.postingId, second.postingId)
        assertEquals(1, postingRows(company))
        assertEquals(
            2,
            jdbc.queryForObject<Int>("select count(*) from posting_source where posting_id = ?", first.postingId),
        )
        val posting = assertNotNull(query.find(first.postingId))
        assertEquals(a.title, posting.title)
        assertEquals(english, posting.description)

        // A later crawl of the second source must not change the canonical row either.
        assertEquals(IngestOutcome.UNCHANGED, ingest.ingest(b.copy(description = "changed again " + english)).outcome)
        assertEquals(english, assertNotNull(query.find(first.postingId)).description)
    }

    @Test
    fun `a different city is a different posting`() {
        val company = "City " + unique()

        val zurich = ingest.ingest(candidate(company, city = "Zurich"))
        val bern = ingest.ingest(candidate(company, city = "Bern"))

        assertTrue(zurich.postingId != bern.postingId)
        assertEquals(2, postingRows(company))
    }

    @Test
    fun `without a city a posting is only deduplicated within its own source`() {
        val company = "NoCity " + unique()

        val a = ingest.ingest(candidate(company, city = null))
        val b = ingest.ingest(candidate(company, city = null))
        val aAgain = ingest.ingest(candidate(company, source = "fixed-" + company, city = null))
        val aRepeat = ingest.ingest(candidate(company, source = "fixed-" + company, city = null))

        assertTrue(a.postingId != b.postingId)
        assertEquals(aAgain.postingId, aRepeat.postingId)
        assertEquals(3, postingRows(company))
    }

    @Test
    fun `one event for a new canonical posting and none for a repeat, an update or a link`() {
        val company = "Event " + unique()
        val a = candidate(company)

        val id = ingest.ingest(a).postingId
        ingest.ingest(a)
        ingest.ingest(a.copy(description = english + " More."))
        ingest.ingest(candidate(company))

        await().atMost(Duration.ofSeconds(10)).until { eventsFor(id).isNotEmpty() }
        assertEquals(1, eventsFor(id).size)
        // The registry keeps one row per listener (this test's and matching's), but only one distinct event.
        assertEquals(1, jdbc.queryForObject<Int>("select count(distinct serialized_event) from event_publication where serialized_event like ?", "%$id%"))
        await().atMost(Duration.ofSeconds(10)).until {
            jdbc.queryForObject<Int>("select count(*) from event_publication where serialized_event like ? and completion_date is null", "%$id%") == 0
        }
        assertEquals(1, eventsFor(id).size)
    }

    @Test
    fun `parallel ingests of one new source posting leave one row and none fail`() {
        val c = candidate("Parallel " + unique())
        val parallel = 16
        val ready = CountDownLatch(parallel)
        val go = CountDownLatch(1)

        val results = Executors.newFixedThreadPool(parallel).use { pool ->
            val futures = (1..parallel).map {
                pool.submit<IngestOutcome> {
                    ready.countDown()
                    go.await()
                    ingest.ingest(c).outcome
                }
            }
            ready.await()
            go.countDown()
            futures.map { it.get() }
        }

        assertEquals(1, results.count { it == IngestOutcome.CREATED })
        assertEquals(parallel - 1, results.count { it == IngestOutcome.UNCHANGED })
        assertEquals(1, postingRows(c.company))
        assertEquals(1, jdbc.queryForObject<Int>("select count(*) from posting_source where source_id = ?", c.sourceId))
    }

    @Test
    fun `lookup of an unknown id is empty`() {
        assertNull(query.find(UUID.randomUUID()))
    }

    @Test
    fun `listing is newest first-seen first and paged`() {
        val company = "Page " + unique()
        val ids = (1..30).map { ingest.ingest(candidate(company, sourcePostingId = "p$it", city = "City$it")).postingId }

        val page0 = query.list(0, 10)
        val page1 = query.list(1, 10)

        assertEquals(10, page0.size)
        assertEquals(ids.takeLast(10).reversed(), page0.map { it.id })
        assertEquals(ids.dropLast(10).takeLast(10).reversed(), page1.map { it.id })
        assertTrue(query.list(0, 100_000).size <= PostingQuery.MAX_PAGE_SIZE)
    }

    @Test
    fun `a posting has no raw payload and reads take no user, so every caller gets the same data`() {
        val id = ingest.ingest(candidate("Shared " + unique())).postingId

        assertEquals(query.find(id), query.find(id))
        assertTrue(Posting::class.memberProperties.none { it.name.contains("raw", ignoreCase = true) })
    }
}
