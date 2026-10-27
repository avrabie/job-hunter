package net.s4v3.backend.applications.internal

import net.s4v3.backend.FakeHttpServer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LetterPdfTests {

    private val letter = LetterPdf()

    private fun text(pdf: ByteArray): String = Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

    @Test
    fun `renders umlauts and Markdown structure into an A4 PDF with embedded fonts`() {
        val pdf = letter.render("# Bewerbung\n\nSehr geehrte Damen und Herren,\n\nÄ ö ü ß – **fett** und *kursiv*.\n\n- Punkt eins\n- Punkt zwei\n\nMit freundlichen Grüßen")
        assertEquals("%PDF", String(pdf.copyOfRange(0, 4), Charsets.US_ASCII))
        val extracted = text(pdf)
        assertTrue(extracted.contains("Ä ö ü ß"))
        assertTrue(extracted.contains("Bewerbung") && extracted.contains("Punkt zwei") && extracted.contains("Grüßen"))
        assertFalse(extracted.contains("**"))
        Loader.loadPDF(pdf).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val box = doc.getPage(0).mediaBox
            assertEquals(595f, box.width, 1f)
            assertEquals(842f, box.height, 1f)
            val fonts = doc.pages.flatMap { it.resources.fontNames.map { n -> it.resources.getFont(n) } }
            assertTrue(fonts.isNotEmpty())
            fonts.forEach { assertTrue(it.isEmbedded, "font ${it.name} embedded") }
        }
    }

    @Test
    fun `an unfilled salutation becomes a greeting to the recruiting team in the letter's language`() {
        val german = text(letter.render("[Salutation],\n\nText.\n\nFreundliche Grüsse\nAdrian Vrabie"))
        assertTrue(german.contains("Sehr geehrtes Recruiting-Team,"))
        assertFalse(german.contains("Salutation"))
        val english = text(letter.render("[Salutation]\n\nText.\n\nKind regards\nAdrian Vrabie"))
        assertTrue(english.contains("Dear Recruiting Team,"))
        assertFalse(english.contains("Salutation"))
    }

    @Test
    fun `a given salutation is kept and the name stays on its own line`() {
        val extracted = text(letter.render("Sehr geehrte Frau Muster,\n\nText.\n\nFreundliche Grüsse\nAdrian Vrabie"))
        assertTrue(extracted.contains("Sehr geehrte Frau Muster,"))
        assertFalse(extracted.contains("Recruiting-Team"))
        assertTrue(extracted.lines().any { it.trim() == "Adrian Vrabie" })
    }

    @Test
    fun `a long letter paginates`() {
        val pdf = letter.render((1..80).joinToString("\n\n") { "Paragraph $it with some words to fill the line and a bit more." })
        assertTrue(Loader.loadPDF(pdf).use { it.numberOfPages } > 1)
    }

    @Test
    fun `HTML in the text is shown as written`() {
        val extracted = text(letter.render("Hi <script>alert(1)</script> & <b>bold</b>\n\n<div>block</div>"))
        assertTrue(extracted.contains("<script>alert(1)</script> & <b>bold</b>"))
        assertTrue(extracted.contains("<div>block</div>"))
    }

    @Test
    fun `no network request is made for images, links or markup`() {
        FakeHttpServer("127.0.0.1").use { server ->
            val url = "${server.baseUrl}/x.png"
            val pdf = letter.render("![pic]($url)\n\n<img src=\"$url\">\n\n[link]($url) javascript:alert(1) [bad](javascript:alert(1))")
            assertTrue(text(pdf).contains("link"))
            assertEquals(0, server.requests.size)
        }
    }
}
