package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvLink
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.EntrySection
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlRendererTests {

    private val renderer = HtmlRenderer()

    private fun html(doc: net.s4v3.backend.cv.export.CvDocument, t: CvTemplate = CvTemplate.CLASSIC) =
        String(renderer.render(doc, t), Charsets.UTF_8)

    private fun parse(html: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(html.toByteArray()))

    private fun docWith(entry: CvEntry) =
        CvFixtures.small().copy(sections = listOf(EntrySection("Experience", listOf(entry))))

    /** Visible text: markup removed, whitespace collapsed. */
    private fun text(html: String) = parse(html.replace(Regex("<style>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")).documentElement.textContent.replace(Regex("\\s+"), " ")

    @Test
    fun `markup in the profile is shown as text`() {
        val bullet = "<script>alert(1)</script> & \"quoted\" '\"><img src=x>"
        val out = html(docWith(CvEntry("Job", bullets = listOf(bullet))))
        assertTrue(out.contains("&lt;script&gt;"))
        assertTrue(out.contains("&amp;"))
        assertFalse(out.contains("<script"))
        assertFalse(out.contains("<img"))
        assertTrue(text(out).contains(bullet))
    }

    @Test
    fun `unsafe link is dropped and safe link kept`() {
        val bad = html(docWith(CvEntry("Job", link = CvLink("evil", "javascript:alert(1)"))).copy(contactLines = emptyList()))
        assertFalse(bad.contains("href=\"javascript"))
        assertFalse(bad.contains("<a "))
        assertTrue(bad.contains("evil"))
        val data = html(docWith(CvEntry("Job")).copy(contactLines = listOf(CvLink("x", "data:text/html,hi"), CvLink("y", "ftp://h/x"))))
        assertFalse(data.contains("<a "))
        val ok = html(docWith(CvEntry("Job", link = CvLink("repo", "https://example.org/a?b=1&c=2"))))
        assertTrue(ok.contains("<a href=\"https://example.org/a?b=1&amp;c=2\">repo</a>"))
    }

    @Test
    fun `standalone html forbids scripts and external resources`() {
        val out = html(CvFixtures.small())
        assertTrue(out.contains("default-src 'none'; style-src 'unsafe-inline'"))
        assertFalse(out.contains("<script"))
    }

    @Test
    fun `xhtml parses for every template`() {
        for (t in CvTemplate.entries) parse(html(CvFixtures.small(), t))
    }

    @Test
    fun `same content in the three templates`() {
        val doc = CvFixtures.small()
        val texts = CvTemplate.entries.associateWith { t ->
            text(html(doc, t)).filter { !it.isWhitespace() }.toList().sorted()
        }
        assertEquals(texts.getValue(CvTemplate.CLASSIC), texts.getValue(CvTemplate.MODERN))
        assertEquals(texts.getValue(CvTemplate.CLASSIC), texts.getValue(CvTemplate.COMPACT))
        assertTrue(text(html(doc)).contains("Ada Example"))
    }

    @Test
    fun `templates differ in layout`() {
        val doc = CvFixtures.small()
        assertTrue(html(doc, CvTemplate.MODERN).contains("<table class=\"layout\">"))
        assertFalse(html(doc, CvTemplate.CLASSIC).contains("<table"))
        assertFalse(html(doc, CvTemplate.COMPACT).contains("<table"))
    }
}
