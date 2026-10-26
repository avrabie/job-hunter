package net.s4v3.backend

import net.s4v3.backend.postings.RemoteMode
import net.s4v3.backend.sources.internal.greenhouse.MalformedResponse
import net.s4v3.backend.sources.internal.swissdevjobs.SwissDevJobsMapping
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SwissDevJobsMappingTests {

    private fun entry(
        slug: String = "Acme-Backend-Engineer", name: String = "Backend Engineer", company: String = "Acme",
        city: String = """"actualCity":"Zug",""", workplace: String = """"workplace":"hybrid",""",
        salary: String = """"annualSalaryFrom":80000,"annualSalaryTo":120000,""",
    ) = SwissDevJobsMapping.parseList(
        """[{"jobUrl":"$slug","name":"$name","company":"$company",$city$workplace$salary
            "redirectJobUrl":"https://apply.example/secret-link","isPaused":false,"extra":{"ignored":true}}]""",
    ).single()

    private val html = "<p><b>Salary: CHF 80'000</b></p><ul><li>Kotlin</li><li>Spring</li></ul>"

    @Test
    fun `a full entry maps to a candidate`() {
        val c = SwissDevJobsMapping.toCandidate(entry(), html)!!

        assertEquals("swissdevjobs", c.sourceId)
        assertEquals("Acme-Backend-Engineer", c.sourcePostingId)
        assertEquals("https://swissdevjobs.ch/jobs/Acme-Backend-Engineer", c.url)
        assertEquals("Backend Engineer", c.title)
        assertEquals("Acme", c.company)
        assertEquals("Zug", c.city)
        assertEquals("ZG", c.canton)
        assertEquals(RemoteMode.HYBRID, c.remoteMode)
        assertEquals(80000, c.salaryMin)
        assertEquals(120000, c.salaryMax)
        assertNull(c.workloadText)
        assertContains(c.description, "- Kotlin")
        assertFalse("<" in c.description)
    }

    @Test
    fun `the apply link is not the url but the raw payload keeps the entry and the feed text`() {
        val c = SwissDevJobsMapping.toCandidate(entry(), html)!!

        assertFalse("apply.example" in c.url)
        assertContains(c.rawPayload, "\"extra\"")
        assertContains(c.rawPayload, "<li>Kotlin</li>")
    }

    @Test
    fun `no salary amounts leave the salary unknown`() {
        val c = SwissDevJobsMapping.toCandidate(entry(salary = ""), html)!!

        assertNull(c.salaryMin)
        assertNull(c.salaryMax)
    }

    @Test
    fun `one salary amount leaves the salary unknown`() {
        val c = SwissDevJobsMapping.toCandidate(entry(salary = """"annualSalaryFrom":90000,"""), html)!!

        assertNull(c.salaryMin)
        assertNull(c.salaryMax)
    }

    @Test
    fun `a zero amount leaves the salary unknown`() {
        val c = SwissDevJobsMapping.toCandidate(entry(salary = """"annualSalaryFrom":0,"annualSalaryTo":100000,"""), html)!!

        assertNull(c.salaryMin)
        assertNull(c.salaryMax)
    }

    @Test
    fun `workplace values map to remote modes and anything else is unknown`() {
        fun mode(workplace: String) =
            SwissDevJobsMapping.toCandidate(entry(workplace = workplace), html)!!.remoteMode

        assertEquals(RemoteMode.REMOTE, mode(""""workplace":"remote","""))
        assertEquals(RemoteMode.HYBRID, mode(""""workplace":"hybrid","""))
        assertEquals(RemoteMode.ONSITE, mode(""""workplace":"office","""))
        assertNull(mode(""""workplace":"moon","""))
        assertNull(mode(""))
    }

    @Test
    fun `the city falls back to the city category and an unknown city has no canton`() {
        val category = SwissDevJobsMapping.toCandidate(entry(city = """"cityCategory":"Bern","""), html)!!
        val unknown = SwissDevJobsMapping.toCandidate(entry(city = """"actualCity":"Nowhereville","""), html)!!
        val none = SwissDevJobsMapping.toCandidate(entry(city = ""), html)!!

        assertEquals("Bern", category.city)
        assertEquals("BE", category.canton)
        assertEquals("Nowhereville", unknown.city)
        assertNull(unknown.canton)
        assertNull(none.city)
    }

    @Test
    fun `an entry without a title or an empty description gives no candidate`() {
        assertNull(SwissDevJobsMapping.toCandidate(entry(name = ""), html))
        assertNull(SwissDevJobsMapping.toCandidate(entry(), "  <p> </p> "))
    }

    @Test
    fun `a list that is not an array is malformed`() {
        assertFailsWith<MalformedResponse> { SwissDevJobsMapping.parseList("""{"jobs":[]}""") }
        assertFailsWith<MalformedResponse> { SwissDevJobsMapping.parseList("<html>") }
    }

    @Test
    fun `the feed is read by slug and the query of the link is ignored`() {
        val feed = SwissDevJobsMapping.parseFeed(
            """<rss><channel><item><title>x</title>
               <link>https://swissdevjobs.ch/jobs/Acme-Backend?utm_source=a&amp;utm_medium=b</link>
               <description><![CDATA[<p>Hello &amp; welcome</p>]]></description></item>
               <item><link>https://swissdevjobs.ch/jobs/Other-Job</link><description></description></item></channel></rss>""",
        )

        assertEquals(setOf("Acme-Backend", "Other-Job"), feed.keys)
        assertEquals("<p>Hello &amp; welcome</p>", feed["Acme-Backend"])
        assertEquals("", feed["Other-Job"])
    }

    @Test
    fun `an xml entity in the description is decoded`() {
        val feed = SwissDevJobsMapping.parseFeed(
            "<rss><channel><item><link>https://swissdevjobs.ch/jobs/a</link><description>R&amp;D &lt;b&gt;x&lt;/b&gt;</description></item></channel></rss>",
        )

        assertEquals("R&D <b>x</b>", feed["a"])
    }

    @Test
    fun `a document type definition with an external entity is refused`() {
        val body = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><link>https://swissdevjobs.ch/jobs/a</link><description>&xxe;</description></item></channel></rss>"""

        assertFailsWith<MalformedResponse> { SwissDevJobsMapping.parseFeed(body) }
    }

    @Test
    fun `a feed that is not xml is malformed`() {
        assertFailsWith<MalformedResponse> { SwissDevJobsMapping.parseFeed("<rss><channel>") }
        assertFailsWith<MalformedResponse> { SwissDevJobsMapping.parseFeed("not xml") }
    }

    @Test
    fun `items without a job link are ignored`() {
        val feed = SwissDevJobsMapping.parseFeed(
            "<rss><channel><item><link>https://swissdevjobs.ch/</link><description>x</description></item></channel></rss>",
        )

        assertEquals(emptyMap(), feed)
    }
}
