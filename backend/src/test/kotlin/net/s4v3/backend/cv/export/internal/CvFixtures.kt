package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvLink
import net.s4v3.backend.cv.export.EntrySection
import net.s4v3.backend.cv.export.LineSection
import net.s4v3.backend.cv.export.ProseSection
import net.s4v3.backend.cv.export.TagSection

/** Synthetic documents for the renderer tests (an invented person). */
internal object CvFixtures {

    fun job(i: Int, bullets: List<String> = listOf("Built the thing number $i", "Reduced latency by 30%")) = CvEntry(
        heading = "Engineer $i",
        subheading = "Employer$i AG",
        period = "03.2021 – heute",
        place = "Zürich",
        bullets = bullets,
        tags = listOf("Kotlin", "Spring"),
    )

    fun small(language: CvLanguage = CvLanguage.DE, jobs: Int = 2, skills: Int = 10) = CvDocument(
        language = language,
        name = "Ada Example",
        headline = "Senior Kotlin Engineer",
        contactLines = listOf(
            CvLink("ada@example.org"),
            CvLink("+41 79 000 00 00"),
            CvLink("https://example.org", "https://example.org"),
        ),
        facts = listOf(if (language == CvLanguage.DE) "Arbeitsbewilligung: Bewilligung B" else "Work authorisation: Permit B"),
        sections = listOf(
            ProseSection("Profile", listOf("Ingénieure à Genève, très motivée: ä ö ü é è ç.")),
            EntrySection("Experience", (1..jobs).map { job(it) }),
            EntrySection(
                "Open source projects",
                listOf(CvEntry("example/lib", link = CvLink("example/lib", "https://example.org/lib"), text = "A small library", tags = listOf("Kotlin"))),
            ),
            EntrySection("Education", listOf(CvEntry("BSc Informatik", subheading = "ETH Zürich", period = "09.2012 – 06.2015"))),
            TagSection("Skills", (1..skills).map { "Skill$it (Advanced)" }),
            LineSection("Languages", listOf("Deutsch: Muttersprache", "Französisch: Fortgeschritten")),
        ),
    )
}
