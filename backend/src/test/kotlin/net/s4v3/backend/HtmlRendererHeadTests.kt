package net.s4v3.backend

import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.internal.HtmlRenderer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlRendererHeadTests {

    private val document = CvDocument(CvLanguage.EN, "Ada Example", null, emptyList(), emptyList(), emptyList())

    @Test
    fun `without extra head the output is what it was`() {
        for (template in CvTemplate.entries) {
            assertEquals(HtmlRenderer().html(document, template), HtmlRenderer().html(document, template, ""))
            assertEquals(HtmlRenderer().render(document, template).decodeToString(), HtmlRenderer().html(document, template))
            assertTrue("<title>Ada Example</title>\n<style>" in HtmlRenderer().html(document, template))
        }
    }

    @Test
    fun `extra head lands in the head after the title`() {
        val out = HtmlRenderer().html(document, CvTemplate.CLASSIC, "<meta name=\"robots\" content=\"noindex\"/>\n")
        val head = out.substringBefore("</head>")
        assertTrue("<title>Ada Example</title>\n<meta name=\"robots\" content=\"noindex\"/>\n<style>" in head)
    }
}
