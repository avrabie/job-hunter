package net.s4v3.backend.cv.export.internal

import com.sun.net.httpserver.HttpServer
import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.EntrySection
import net.s4v3.backend.cv.export.ProseSection
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfRendererTests {

    private val renderer = PdfRenderer()

    private fun text(pdf: ByteArray): String = Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

    private fun pages(pdf: ByteArray): Int = Loader.loadPDF(pdf).use { it.numberOfPages }

    @Test
    fun `starts with pdf magic and holds name and accents for every template`() {
        for (t in CvTemplate.entries) {
            val pdf = renderer.render(CvFixtures.small(), t)
            assertEquals("%PDF", String(pdf.copyOfRange(0, 4), Charsets.US_ASCII))
            val extracted = text(pdf)
            assertTrue(extracted.contains("Ada Example"), "name in $t")
            assertTrue(extracted.contains("ä ö ü é è ç"), "accents in $t")
        }
    }

    @Test
    fun `all fonts are embedded`() {
        for (t in CvTemplate.entries) {
            Loader.loadPDF(renderer.render(CvFixtures.small(), t)).use { doc ->
                val fonts = doc.pages.flatMap { it.resources.fontNames.map { n -> it.resources.getFont(n) } }
                assertTrue(fonts.isNotEmpty())
                fonts.forEach { assertTrue(it.isEmbedded, "font ${it.name} embedded in $t") }
            }
        }
    }

    @Test
    fun `compact with two positions fits one page`() {
        assertEquals(1, pages(renderer.render(CvFixtures.small(jobs = 2, skills = 10), CvTemplate.COMPACT)))
    }

    @Test
    fun `long profile paginates and keeps every employer`() {
        val pdf = renderer.render(CvFixtures.small(jobs = 30), CvTemplate.CLASSIC)
        assertTrue(pages(pdf) > 1)
        val extracted = text(pdf)
        for (i in 1..30) assertTrue(extracted.contains("Employer$i AG"), "employer $i")
    }

    @Test
    fun `markup characters appear as written`() {
        val doc = CvFixtures.small().copy(
            sections = listOf(EntrySection("Experience", listOf(CvEntry("Job", bullets = listOf("<script>alert(1)</script> & \"quoted\""))))),
        )
        assertTrue(text(renderer.render(doc, CvTemplate.CLASSIC)).contains("<script>alert(1)</script> & \"quoted\""))
    }

    @Test
    fun `no network request is made`() {
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { ex -> hits.incrementAndGet(); ex.sendResponseHeaders(404, -1); ex.close() }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/x.png"
            val evil = "<img src=\"$url\"> $url <link rel=\"stylesheet\" href=\"$url\">"
            val doc = CvFixtures.small().copy(
                sections = listOf(ProseSection("Profile", listOf(evil))),
            )
            val pdf = renderer.render(doc, CvTemplate.MODERN)
            assertTrue(text(pdf).contains("Ada Example"))
            Thread.sleep(300)
            assertEquals(0, hits.get())
        } finally {
            server.stop(0)
        }
    }
}
