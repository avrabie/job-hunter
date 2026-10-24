package net.s4v3.backend

import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.internal.CvBuilder
import net.s4v3.backend.cv.export.internal.CvFixtures
import net.s4v3.backend.cv.export.internal.HtmlRenderer
import net.s4v3.backend.profile.Contact
import net.s4v3.backend.profile.internal.ProfileJson
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the HTML of the CLASSIC, MODERN and COMPACT templates for the synthetic "Ada Example" profile, byte for byte.
 * Set the environment variable `GOLDEN_UPDATE=1` to rewrite the expectations (only when a change is meant).
 */
class CvGoldenHtmlTests {

    private val renderer = HtmlRenderer()
    private val fixture = javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes().decodeToString()
    private val profile = ProfileJson.read(fixture).copy(
        contact = Contact(
            fullName = "Ada Example", headline = "Senior Kotlin Engineer", email = "ada@example.org",
            phone = "+41 79 000 00 00", website = "https://example.org",
            linkedin = "https://www.linkedin.com/in/ada-example",
        ),
    )

    private fun check(name: String, actual: String) {
        if (System.getenv("GOLDEN_UPDATE") == "1") {
            val dir = Paths.get("src/test/resources/cv-export/golden")
            Files.createDirectories(dir)
            Files.write(dir.resolve(name), actual.toByteArray(Charsets.UTF_8))
            return
        }
        val expected = javaClass.getResourceAsStream("/cv-export/golden/$name")!!.readBytes().decodeToString()
        assertEquals(expected, actual, "golden $name")
    }

    @Test
    fun `classic modern and compact html of the built profile is unchanged`() {
        for (language in CvLanguage.entries) for (template in listOf(CvTemplate.CLASSIC, CvTemplate.MODERN, CvTemplate.COMPACT)) {
            val options = ExportOptions(CvFormat.HTML, template, language, sections = null, includeAnalysis = true)
            check("built-${template.name.lowercase()}-${language.name.lowercase()}.html", renderer.html(CvBuilder.build(profile, options), template))
        }
    }

    @Test
    fun `classic modern and compact html of the fixture document is unchanged`() {
        for (template in listOf(CvTemplate.CLASSIC, CvTemplate.MODERN, CvTemplate.COMPACT)) {
            check("fixture-${template.name.lowercase()}.html", renderer.html(CvFixtures.small(), template))
        }
    }
}
