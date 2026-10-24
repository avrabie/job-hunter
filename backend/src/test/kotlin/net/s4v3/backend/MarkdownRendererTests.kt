package net.s4v3.backend

import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvLink
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.EntrySection
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.LineSection
import net.s4v3.backend.cv.export.ProseSection
import net.s4v3.backend.cv.export.TagSection
import net.s4v3.backend.cv.export.internal.CvBuilder
import net.s4v3.backend.cv.export.internal.MarkdownRenderer
import net.s4v3.backend.cv.export.internal.escapeMarkdown
import net.s4v3.backend.profile.Contact
import net.s4v3.backend.profile.internal.ProfileJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownRendererTests {

    private fun render(document: CvDocument, template: CvTemplate = CvTemplate.CLASSIC): String =
        MarkdownRenderer().render(document, template).decodeToString()

    private fun english() = CvDocument(
        language = CvLanguage.EN,
        name = "Ada Example",
        headline = "Senior Kotlin Engineer",
        contactLines = listOf(CvLink("ada@example.org"), CvLink("https://example.org", "https://example.org")),
        facts = listOf("Work authorisation: Swiss permit B (residence)"),
        sections = listOf(
            ProseSection("Profile", listOf("Invented engineer used only in tests.")),
            EntrySection(
                "Experience",
                listOf(
                    CvEntry(
                        heading = "Example AG",
                        subheading = "Senior Engineer",
                        period = "Mar 2021 – Present",
                        place = "Zurich",
                        bullets = listOf("Cut build time by half"),
                        tags = listOf("Kotlin", "PostgreSQL"),
                    ),
                ),
            ),
            TagSection("Skills", listOf("Kotlin (Expert)", "PostgreSQL")),
            LineSection("Languages", listOf("English – Advanced")),
        ),
    )

    @Test
    fun `english golden output`() {
        val expected = """
            # Ada Example

            Senior Kotlin Engineer

            - ada@example.org
            - [https://example.org](https://example.org)
            - Work authorisation: Swiss permit B (residence)

            ## Profile

            Invented engineer used only in tests.

            ## Experience

            ### Example AG

            Senior Engineer · Mar 2021 – Present · Zurich

            - Cut build time by half

            Kotlin, PostgreSQL

            ## Skills

            Kotlin (Expert), PostgreSQL

            ## Languages

            - English – Advanced
        """.trimIndent() + "\n"

        assertEquals(expected, render(english()))
    }

    @Test
    fun `german golden output`() {
        val document = CvDocument(
            language = CvLanguage.DE,
            name = "Ada Example",
            headline = null,
            contactLines = listOf(CvLink("Zurich, ZH")),
            facts = emptyList(),
            sections = listOf(
                EntrySection(
                    "Berufserfahrung",
                    listOf(CvEntry(heading = "Example AG", subheading = "Senior Engineer", period = "03.2021 – heute")),
                ),
                LineSection("Sprachen", listOf("Englisch – Fortgeschritten", "Deutsch – Gute Kenntnisse")),
            ),
        )
        val expected = """
            # Ada Example

            - Zurich, ZH

            ## Berufserfahrung

            ### Example AG

            Senior Engineer · 03.2021 – heute

            ## Sprachen

            - Englisch – Fortgeschritten
            - Deutsch – Gute Kenntnisse
        """.trimIndent() + "\n"

        assertEquals(expected, render(document))
    }

    @Test
    fun `markdown structure is not injectable`() {
        val document = english().let {
            it.copy(
                sections = listOf(ProseSection("Profile", listOf("# Heading", "[x](javascript:alert(1))", "<b>bold</b>"))),
                contactLines = listOf(CvLink("javascript:alert(1)", "javascript:alert(1)")),
            )
        }

        val lines = render(document).lines()

        assertEquals(1, lines.count { it.startsWith("# ") }, "only the name is a heading")
        assertTrue(lines.contains("\\# Heading"))
        assertTrue(lines.contains("\\[x\\](javascript:alert(1))"))
        assertTrue(lines.contains("\\<b\\>bold\\</b\\>"))
        assertTrue(lines.contains("- javascript:alert(1)"))
        assertFalse(lines.any { it.contains("[x](") })
        assertFalse(lines.any { it.contains("](javascript") && !it.contains("\\]") })
    }

    @Test
    fun `escapeMarkdown neutralises line starts and inline markup only`() {
        assertEquals("\\# x", escapeMarkdown("# x"))
        assertEquals("\\- item", escapeMarkdown("- item"))
        assertEquals("1\\. first", escapeMarkdown("1. first"))
        assertEquals("Open-Source and snake\\_case", escapeMarkdown("Open-Source and snake_case"))
        assertEquals("Plain text, Zurich.", escapeMarkdown("Plain text, Zurich."))
    }

    @Test
    fun `the same content is rendered for every template`() {
        val profile = ProfileJson.read(
            javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes().decodeToString(),
        ).copy(contact = Contact(fullName = "Ada Example", headline = "Senior Kotlin Engineer", email = "ada@example.org"))
        val document = CvBuilder.build(
            profile,
            ExportOptions(CvFormat.MARKDOWN, CvTemplate.CLASSIC, CvLanguage.DE, null),
        )

        val outputs = CvTemplate.entries.map { render(document, it) }

        assertEquals(outputs.first(), outputs[1])
        assertEquals(outputs.first(), outputs[2])
        assertTrue(outputs.first().startsWith("# Ada Example\n"))
        assertTrue(outputs.first().contains("## Berufserfahrung"))
    }
}
