package net.s4v3.backend

import net.s4v3.backend.sources.internal.greenhouse.DescriptionText
import net.s4v3.backend.sources.internal.greenhouse.GreenhouseMapping
import net.s4v3.backend.sources.internal.greenhouse.MalformedResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GreenhouseMappingTests {

    private fun job(
        id: String = "123", title: String = "Backend Engineer", company: String = "Acme", location: String = "Zurich",
        url: String = "https://acme.example/jobs?gh_jid=123", content: String = "&lt;p&gt;Build things&lt;/p&gt;",
    ) = GreenhouseMapping.parseJob(
        """{"id":$id,"title":"$title","company_name":"$company","location":{"name":"$location"},"absolute_url":"$url",
            "content":"$content","extra":{"ignored":true}}""",
    )

    @Test
    fun `a full job maps to a candidate`() {
        val c = GreenhouseMapping.toCandidate("acme", job())!!

        assertEquals("greenhouse-acme", c.sourceId)
        assertEquals("123", c.sourcePostingId)
        assertEquals("Backend Engineer", c.title)
        assertEquals("Acme", c.company)
        assertEquals("https://acme.example/jobs?gh_jid=123", c.url)
        assertEquals("Zurich", c.city)
        assertEquals("ZH", c.canton)
        assertContains(c.rawPayload, "\"extra\"")
    }

    @Test
    fun `salary, workload and remote mode stay unknown`() {
        val c = GreenhouseMapping.toCandidate("acme", job())!!

        assertNull(c.salaryMin)
        assertNull(c.salaryMax)
        assertNull(c.workloadText)
        assertNull(c.remoteMode)
    }

    @Test
    fun `a job missing id, title or url is not mapped`() {
        assertNull(GreenhouseMapping.toCandidate("acme", job(id = "\"\"")))
        assertNull(GreenhouseMapping.toCandidate("acme", job(title = "")))
        assertNull(GreenhouseMapping.toCandidate("acme", job(url = "")))
    }

    @Test
    fun `a country-only location gives no city`() {
        val c = GreenhouseMapping.toCandidate("acme", job(location = "Switzerland"))!!

        assertNull(c.city)
        assertNull(c.canton)
    }

    @Test
    fun `a job without content is not mapped`() {
        assertNull(GreenhouseMapping.toCandidate("acme", job(content = "")))
    }

    @Test
    fun `list parsing ignores unknown fields and rejects non-JSON`() {
        val jobs = GreenhouseMapping.parseList("""{"jobs":[{"id":1,"title":"A","location":{"name":"Bern"},"x":1},{"title":"no id"}],"meta":{}}""")

        assertEquals(1, jobs.size)
        assertEquals("Bern", jobs[0].location)
        assertFailsWith<MalformedResponse> { GreenhouseMapping.parseList("<html>nope</html>") }
        assertFailsWith<MalformedResponse> { GreenhouseMapping.parseList("""{"other":[]}""") }
    }

    @Test
    fun `escaped html becomes plain text with list lines`() {
        val text = DescriptionText.fromContent(
            "&lt;p&gt;Build &amp;amp; run services&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Kotlin&lt;/li&gt;&lt;li&gt;Postgres&lt;/li&gt;&lt;/ul&gt;",
        )

        assertContains(text, "Build & run services")
        assertContains(text.lines(), "- Kotlin")
        assertContains(text.lines(), "- Postgres")
        assertFalse('<' in text)
    }

    @Test
    fun `nested lists, line breaks and entities are handled`() {
        val text = DescriptionText.fromContent(
            "&lt;h3&gt;Role&lt;/h3&gt;&lt;ul&gt;&lt;li&gt;&lt;p&gt;Own the API&lt;/p&gt;&lt;/li&gt;&lt;/ul&gt;line one&lt;br&gt;line two&amp;nbsp;end",
        )

        assertContains(text.lines(), "- Own the API")
        assertContains(text, "line one\nline two end")
    }

    @Test
    fun `script text never appears and empty content gives empty text`() {
        val text = DescriptionText.fromContent("&lt;p&gt;Hello&lt;/p&gt;&lt;script&gt;alert(1)&lt;/script&gt;")

        assertEquals("Hello", text)
        assertEquals("", DescriptionText.fromContent(""))
        assertEquals("", DescriptionText.fromContent("   "))
    }
}
