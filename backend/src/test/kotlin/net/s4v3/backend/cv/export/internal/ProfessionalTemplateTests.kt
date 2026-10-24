package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvLink
import net.s4v3.backend.cv.export.CvPhoto
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.EntrySection
import net.s4v3.backend.cv.export.GroupedTagSection
import net.s4v3.backend.cv.export.LineSection
import net.s4v3.backend.cv.export.ProseSection
import net.s4v3.backend.cv.export.TagGroup
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfessionalTemplateTests {

    private val html = HtmlRenderer()
    private val pdf = PdfRenderer(html)

    /** A synthetic round "photo": a gradient disc on white, drawn here (never a real face). */
    private fun photo(): CvPhoto {
        val img = BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, 320, 320)
        g.clip = Ellipse2D.Float(0f, 0f, 320f, 320f)
        g.paint = GradientPaint(0f, 0f, Color(60, 110, 170), 320f, 320f, Color(230, 170, 90))
        g.fillRect(0, 0, 320, 320)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return CvPhoto(out.toByteArray())
    }

    private fun doc(language: CvLanguage = CvLanguage.EN, jobs: Int = 2, photo: CvPhoto? = null, name: String = "Ada Example") =
        CvDocument(
            language = language,
            name = name,
            headline = "Senior Kotlin Engineer",
            contactLines = listOf(
                CvLink("ada@example.org"), CvLink("+41 79 000 00 00"), CvLink("Zürich, ZH"),
                CvLink("https://example.org", "https://example.org"),
            ),
            facts = listOf("Work authorisation: Permit B"),
            sections = listOf(
                ProseSection("Profile", listOf("Builds backends that last. ".repeat(12).trim())),
                EntrySection(
                    "Experience",
                    (1..jobs).map {
                        CvEntry(
                            heading = "Employer$it AG", subheading = "Senior Engineer $it", period = "Mar 2021 – Present",
                            place = "Zürich", text = "Payments platform for retail banks",
                            bullets = listOf(
                                "Architecture: designed the event-driven core number $it",
                                "Reduced latency by 30% without a colon",
                                "A long first part that is certainly longer than forty characters: not bold",
                            ),
                            tags = listOf("Kotlin", "Spring"), titleFirst = true,
                        )
                    },
                ),
                GroupedTagSection(
                    "Skills",
                    listOf(
                        TagGroup("Programming languages", listOf("Java (Expert)", "Kotlin")),
                        TagGroup("Messaging", listOf("Kafka")),
                        TagGroup("Other", listOf("Basket weaving")),
                    ),
                ),
                LineSection("Languages", listOf("German – Native", "English – Proficient")),
            ),
            photo = photo,
        )

    private fun text(bytes: ByteArray): String = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }

    private fun images(page: PDPage): Int {
        val res: PDResources = page.resources
        return res.xObjectNames.count { res.isImageXObject(it) }
    }

    private fun imageCount(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { d -> d.pages.sumOf { images(it) } }

    @Test
    fun `html carries the colours, headings, group rows and one data uri with a photo`() {
        val out = html.html(doc(photo = photo()), CvTemplate.PROFESSIONAL)
        for (c in listOf("#1f3a68", "#c0792a")) assertTrue(out.contains(c), c)
        assertTrue(out.contains("letter-spacing: 3pt"))
        for (h in listOf("<h2>Profile</h2>", "<h2>Experience</h2>", "<h2>Skills</h2>")) assertTrue(out.contains(h), h)
        assertTrue(out.contains("<td class=\"group\">Programming languages</td><td>Java (Expert), Kotlin</td>"))
        assertTrue(out.contains("<td class=\"group\">Other</td><td>Basket weaving</td>"))
        assertEquals(1, Regex("data:image/jpeg;base64,").findAll(out).count())
        assertTrue(out.contains("default-src 'none'; style-src 'unsafe-inline'; img-src data:"))
    }

    @Test
    fun `html without a photo has no image and no img-src`() {
        val out = html.html(doc(), CvTemplate.PROFESSIONAL)
        assertFalse(out.contains("data:image"))
        assertFalse(out.contains("<img"))
        assertFalse(out.contains("img-src"))
    }

    @Test
    fun `a photo is ignored by the other templates`() {
        for (t in listOf(CvTemplate.CLASSIC, CvTemplate.MODERN, CvTemplate.COMPACT)) {
            val out = html.html(doc(photo = photo()), t)
            assertFalse(out.contains("data:image"), "$t")
            assertFalse(out.contains("img-src"), "$t")
        }
    }

    @Test
    fun `bold lead-in only before a short colon`() {
        val out = html.html(doc(jobs = 1), CvTemplate.PROFESSIONAL)
        assertTrue(out.contains("<li><strong>Architecture:</strong> designed the event-driven core number 1</li>"))
        assertTrue(out.contains("<li>Reduced latency by 30% without a colon</li>"))
        assertTrue(out.contains("<li>A long first part that is certainly longer than forty characters: not bold</li>"))
    }

    @Test
    fun `entry rows put the title and period first, then company and place`() {
        val out = html.html(doc(jobs = 1), CvTemplate.PROFESSIONAL)
        assertTrue(out.contains("<td class=\"title\">Senior Engineer 1</td><td class=\"period\">Mar 2021 – Present</td>"))
        assertTrue(out.contains("<td class=\"org\">Employer1 AG</td><td class=\"place\">Zürich</td>"))
    }

    @Test
    fun `hostile names and text are escaped everywhere`() {
        val hostile = "<script>alert(1)</script>\"'&"
        val d = doc(name = hostile).copy(headline = "</style><img src=x>")
        val out = html.html(d, CvTemplate.PROFESSIONAL)
        assertFalse(out.contains("<script>"))
        assertFalse(out.contains("<img src=x>"))
        assertFalse(out.contains("</style><img"))
        assertTrue(out.contains("&lt;script&gt;"))
        // The footer string is CSS-escaped: no raw angle bracket or quote of the user text reaches the stylesheet.
        val css = out.substringAfter("<style>").substringBefore("</style>")
        assertFalse(css.contains("<"))
    }

    @Test
    fun `contact lines are inline with middle dots and headline and footer css follow the rules`() {
        val out = html.html(doc(), CvTemplate.PROFESSIONAL)
        assertEquals(1, Regex("<p class=\"contact\">").findAll(out).count())
        val contact = out.substringAfter("<p class=\"contact\">").substringBefore("</p>")
        assertTrue(contact.contains(" · "), contact)
        val css = out.substringAfter("<style>").substringBefore("</style>")
        assertTrue(Regex("\\.headline \\{[^}]*font-weight: bold;[^}]*text-transform: uppercase").containsMatchIn(css))
        assertTrue(css.contains("@bottom-right"))
        assertFalse(css.contains("@bottom-center"))
    }

    @Test
    fun `german labels reach the html`() {
        val d = doc(CvLanguage.DE).copy(
            sections = listOf(GroupedTagSection("Kenntnisse", listOf(TagGroup("Programmiersprachen", listOf("Kotlin"))))),
        )
        val out = html.html(d, CvTemplate.PROFESSIONAL)
        assertTrue(out.contains("<h2>Kenntnisse</h2>"))
        assertTrue(out.contains("Programmiersprachen"))
    }

    @Test
    fun `pdf with a photo holds one image, the name and the footer on both pages`() {
        val bytes = pdf.render(doc(jobs = 14, photo = photo()), CvTemplate.PROFESSIONAL)
        dump(bytes, "professional-photo")
        assertEquals("%PDF-", String(bytes.copyOfRange(0, 5), Charsets.US_ASCII))
        assertEquals(1, imageCount(bytes))
        assertTrue(bytes.size < 300_000, "size ${bytes.size}")
        Loader.loadPDF(bytes).use { d ->
            assertTrue(d.numberOfPages >= 2, "long profile paginates")
            for (page in 1..d.numberOfPages) {
                val t = PDFTextStripper().apply { startPage = page; endPage = page }.getText(d)
                assertTrue(t.contains("Ada Example · Senior Kotlin Engineer"), "footer on page $page")
            }
            assertTrue(PDFTextStripper().getText(d).contains("ADA EXAMPLE") || PDFTextStripper().getText(d).contains("Ada Example"))
        }
        val all = text(bytes)
        for (i in 1..14) assertTrue(all.contains("Senior Engineer $i"), "title $i")
    }

    @Test
    fun `pdf without a photo has no image`() {
        val bytes = pdf.render(doc(), CvTemplate.PROFESSIONAL)
        dump(bytes, "professional-nophoto")
        assertEquals(0, imageCount(bytes))
        assertTrue(text(bytes).contains("Ada Example · Senior Kotlin Engineer"))
        assertTrue(bytes.size < 200_000, "size ${bytes.size}")
    }

    @Test
    fun `pdf footer survives accents and german`() {
        val bytes = pdf.render(doc(CvLanguage.DE, name = "Zoë Müller-Ärger"), CvTemplate.PROFESSIONAL)
        assertTrue(text(bytes).contains("Zoë Müller-Ärger · Senior Kotlin Engineer"))
    }

    /** For a local look only: set `PRO_PDF_OUT` to a directory. */
    private fun dump(bytes: ByteArray, name: String) {
        val dir = System.getenv("PRO_PDF_OUT") ?: return
        Files.write(Paths.get(dir, "$name.pdf"), bytes)
    }
}
