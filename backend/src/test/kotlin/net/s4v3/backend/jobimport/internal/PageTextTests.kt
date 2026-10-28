package net.s4v3.backend.jobimport.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageTextTests {

    private val body = "Ada Example Ltd is hiring a software engineer for its platform team. ".repeat(8)

    @Test
    fun `keeps the article text and drops script, style, nav, footer and form`() {
        val html = """
            <html><head><style>.x{color:red}</style><script>var secret = 1;</script></head><body>
            <nav>Menu Home Jobs</nav>
            <h1>Software Engineer</h1><p>$body</p><ul><li>Kotlin</li><li>Spring</li></ul>
            <form><label>Your email</label><input></form>
            <footer>Cookie settings</footer>
            </body></html>
        """.trimIndent()
        val text = assertNotNull(PageText.extract(html))
        assertTrue("Software Engineer" in text && "Kotlin" in text)
        listOf("secret", "color:red", "Menu Home", "Your email", "Cookie settings").forEach {
            assertFalse(it in text, it)
        }
        assertTrue("Software Engineer\n" in text, "block elements end with a line break")
    }

    @Test
    fun `collapses whitespace`() {
        val text = assertNotNull(PageText.extract("<p>Ada     Example\t\t$body</p>\n\n\n\n<p>second</p>"))
        assertTrue(text.startsWith("Ada Example Ada Example Ltd"))
        assertFalse("\n\n\n" in text)
    }

    @Test
    fun `short or script-only pages are unreadable`() {
        assertNull(PageText.extract("<html><body><script>render()</script><div id=app></div></body></html>"))
        assertNull(PageText.extract("<p>${"x".repeat(PageText.MIN_CHARS - 1)}</p>"))
        assertNotNull(PageText.extract("<p>${"x".repeat(PageText.MIN_CHARS)}</p>"))
    }

    @Test
    fun `caps the text`() {
        val text = assertNotNull(PageText.extract("<p>${"word ".repeat(20_000)}</p>"))
        assertEquals(PageText.MAX_CHARS, text.length)
    }

    private val sentence1 = "At Acme we build distributed systems that process billions of events every day."
    private val sentence2 = "You will design, review and ship backend services written in Kotlin and Java."
    private val descHtml = "<p>$sentence1</p><h2><strong>Qualifications:</strong></h2><ul><li>$sentence2</li><li>Five years of experience with relational databases and cloud platforms.</li></ul>"

    @Test
    fun `appends schema org JobPosting from JSON-LD, also inside a graph`() {
        val ld = """{"@context":"https://schema.org","@graph":[{"@type":"WebSite","name":"x"},
            {"@type":"JobPosting","title":"Staff Engineer","hiringOrganization":{"@type":"Organization","name":"Acme AG"},
            "jobLocation":{"@type":"Place","address":{"streetAddress":"Bahnhofstrasse 1","postalCode":"8001","addressLocality":"Zurich","addressCountry":"CH"}},
            "description":"$descHtml"}]}"""
        val text = assertNotNull(PageText.extract("<html><body><p>$body</p><script type=\"application/ld+json\">$ld</script></body></html>"))
        listOf("Title: Staff Engineer", "Company: Acme AG", "Bahnhofstrasse 1, 8001, Zurich, CH", sentence1, sentence2).forEach {
            assertTrue(it in text, it)
        }
        assertFalse("<p>" in text)
        assertTrue("Qualifications:\n" in text)
    }

    @Test
    fun `reads a long html description from inline page state when the visible text is a placeholder`() {
        val json = """{"title":"Senior Engineer","location":"Zug, Switzerland","city":"Zug","applyUrl":"https://x.example/apply","description":"$descHtml"}"""
        val html = """<html><body><h1>OH SNAP! THIS JOB HAS BEEN CLOSED</h1><p>${"Browse other openings. ".repeat(15)}</p>
            <script>var phApp = phApp || {"ddo":{"jobDetail":$json}}; phApp.run();</script></body></html>"""
        val text = assertNotNull(PageText.extract(html))
        assertFalse("CLOSED" in text, "the placeholder is left out")
        listOf("Title: Senior Engineer", "Location: Zug, Switzerland", "Apply url: https://x.example/apply", sentence1, sentence2).forEach {
            assertTrue(it in text, it)
        }
    }

    @Test
    fun `ignores invalid json and short or non-html descriptions`() {
        val short = """{"description":"<p>short</p>"}"""
        val plain = """{"description":"${"plain words without markup ".repeat(20)}"}"""
        val html = """<p>$body</p><script>var a = {"description": "<p>unterminated;</script>
            <script type="application/ld+json">{not json</script><script>var b = $short;</script><script>var c = $plain;</script>"""
        val text = assertNotNull(PageText.extract(html))
        assertEquals(PageText.extract("<p>$body</p>"), text)
    }

    @Test
    fun `reads the job from a later assignment when an earlier object in the script is only configuration`() {
        val description = "<p>Build and run the enterprise platform with a small team in Zurich.</p><h2>Qualifications:</h2><ul><li>Five years of backend experience</li></ul>" +
            "<p>We offer a flexible workplace and a strong engineering culture.</p>"
        val html = """
            <html><body><h1>OH SNAP! THIS JOB HAS BEEN CLOSED</h1><p>Check out a team below. $body</p>
            <script type="text/javascript">/*<!--*/ var phApp = phApp || {"country":"us","locale":"en_us"};
            phApp.ddo = {"jobDetail":{"data":{"job":{"title":"Senior Engineer","location":"Zurich, Switzerland","description":"$description"}}}}; phApp.run();</script>
            </body></html>
        """.trimIndent()
        val text = assertNotNull(PageText.extract(html))
        assertTrue("Build and run the enterprise platform" in text && "Five years of backend experience" in text)
        assertTrue("Senior Engineer" in text && "Zurich, Switzerland" in text)
    }

    @Test
    fun `embedded job data comes before the visible placeholder text`() {
        val html = """
            <html><body><h1>THIS JOB HAS BEEN CLOSED</h1><p>${"Visible page text of the careers site. ".repeat(40)}</p>
            <script>var s = {"title":"Senior Engineer","description":"${"<p>Build the platform with a small team in Zurich and own it end to end.</p>".repeat(6)}"};</script>
            </body></html>
        """.trimIndent()
        val text = assertNotNull(PageText.extract(html))
        assertTrue(text.indexOf("Senior Engineer") < text.indexOf("THIS JOB HAS BEEN CLOSED"))
        assertTrue(text.startsWith("--- Structured data embedded in the page ---"))
    }

    @Test
    fun `a thin placeholder text is left out when the page holds embedded job data`() {
        val html = """
            <html><body><h1>THIS JOB HAS BEEN CLOSED</h1><p>Check out a team below.</p>
            <script>var s = {"title":"Senior Engineer","description":"${"<p>Build the platform with a small team in Zurich and own it end to end.</p>".repeat(6)}"};</script>
            </body></html>
        """.trimIndent()
        val text = assertNotNull(PageText.extract(html))
        assertTrue("Senior Engineer" in text && "Build the platform" in text)
        assertFalse("CLOSED" in text)
    }
}
