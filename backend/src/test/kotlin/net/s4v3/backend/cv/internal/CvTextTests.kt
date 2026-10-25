package net.s4v3.backend.cv.internal

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CvTextTests {

    /** A one-page PDF whose only text is a name; the email and LinkedIn are link annotations without text. */
    private fun pdfWithLinks(vararg uris: String): ByteArray =
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4).also(doc::addPage)
            PDPageContentStream(doc, page).use {
                it.beginText()
                it.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                it.newLineAtOffset(50f, 700f)
                it.showText("Jane Doe")
                it.endText()
            }
            for (uri in uris) {
                page.annotations.add(
                    PDAnnotationLink().apply {
                        rectangle = PDRectangle(50f, 600f, 20f, 20f)
                        action = PDActionURI().also { it.uri = uri }
                    },
                )
            }
            ByteArrayOutputStream().also(doc::save).toByteArray()
        }

    @Test
    fun `a PDF gives the addresses behind its links, mailto removed`() {
        val text = CvText.of(
            pdfWithLinks("mailto:jane@example.com", "https://www.linkedin.com/in/jane/", "https://www.linkedin.com/in/jane/"),
            "application/pdf",
        )
        assertTrue(text.startsWith("Jane Doe"))
        assertEquals(listOf("jane@example.com", "https://www.linkedin.com/in/jane/"), text.lines().drop(1).filter { it.isNotBlank() })
    }

    @Test
    fun `a PDF without links gives only its text`() {
        assertEquals("Jane Doe", CvText.of(pdfWithLinks(), "application/pdf"))
    }
}
