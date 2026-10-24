package net.s4v3.backend

import net.s4v3.backend.cv.export.CvDocument
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
import net.s4v3.backend.profile.Contact
import net.s4v3.backend.profile.Preferences
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.WorkAuthorisation
import net.s4v3.backend.profile.internal.ProfileJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CvBuilderTests {

    // Synthetic profile: the shared fixture with an invented name and contact.
    private val fixture = javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes().decodeToString()

    private fun profile(): Profile = ProfileJson.read(fixture).copy(
        contact = Contact(
            fullName = "Ada Example",
            headline = "Senior Kotlin Engineer",
            email = "ada@example.org",
            phone = "+41 79 000 00 00",
            website = "https://example.org",
            linkedin = "https://www.linkedin.com/in/ada-example",
        ),
    )

    private fun options(
        language: CvLanguage = CvLanguage.EN,
        sections: List<CvSectionKind>? = null,
        includeOpenSource: Boolean = true,
        includeAnalysis: Boolean = false,
        includeWorkAuthorisation: Boolean = true,
    ) = ExportOptions(
        format = CvFormat.MARKDOWN,
        template = CvTemplate.CLASSIC,
        language = language,
        sections = sections,
        includeOpenSource = includeOpenSource,
        includeAnalysis = includeAnalysis,
        includeWorkAuthorisation = includeWorkAuthorisation,
    )

    private fun build(profile: Profile = profile(), options: ExportOptions = options()): CvDocument =
        CvBuilder.build(profile, options)

    private fun titles(document: CvDocument) = document.sections.map { it.title }

    @Test
    fun `header holds name, headline and contact lines`() {
        val document = build()

        assertEquals("Ada Example", document.name)
        assertEquals("Senior Kotlin Engineer", document.headline)
        assertEquals(
            listOf(
                "ada@example.org",
                "+41 79 000 00 00",
                "Zurich, ZH",
                "https://example.org",
                "https://www.linkedin.com/in/ada-example",
                "github.com/octocat",
            ),
            document.contactLines.map { it.text },
        )
        assertEquals("https://github.com/octocat", document.contactLines.last().url)
    }

    @Test
    fun `sections come in the default order and empty ones are omitted`() {
        val profile = profile().copy(
            summary = null,
            education = emptyList(),
            publications = emptyList(),
            openSourceProjects = emptyList(),
            languages = emptyList(),
        )

        assertEquals(listOf("Experience", "Skills"), titles(build(profile, options())))
    }

    @Test
    fun `selected sections come in the given order and nothing else`() {
        val document = build(options = options(sections = listOf(CvSectionKind.SKILLS, CvSectionKind.EXPERIENCE)))

        assertEquals(listOf("Skills", "Experience"), titles(document))
    }

    @Test
    fun `an empty section list prints only the header`() {
        val document = build(options = options(sections = emptyList()))

        assertEquals("Ada Example", document.name)
        assertTrue(document.sections.isEmpty())
    }

    @Test
    fun `current position reads Present in english and heute in german`() {
        val english = build(options = options(sections = listOf(CvSectionKind.EXPERIENCE)))
        val german = build(options = options(language = CvLanguage.DE, sections = listOf(CvSectionKind.EXPERIENCE)))

        val englishEntries = (english.sections.single() as EntrySection).entries
        val germanEntries = (german.sections.single() as EntrySection).entries
        assertEquals("Mar 2021 – Present", englishEntries.first().period)
        assertEquals("03.2021 – heute", germanEntries.first().period)
        assertEquals("Example AG", englishEntries.first().heading)
        assertEquals("Senior Engineer", englishEntries.first().subheading)
        assertEquals(listOf("Cut build time by half", "Mentored two juniors"), englishEntries.first().bullets)
    }

    @Test
    fun `open source shows analysis only when asked`() {
        val with = build(options = options(sections = listOf(CvSectionKind.OPEN_SOURCE), includeAnalysis = true))
        val without = build(options = options(sections = listOf(CvSectionKind.OPEN_SOURCE), includeAnalysis = false))

        val withEntry = (with.sections.single() as EntrySection).entries.single()
        val withoutEntry = (without.sections.single() as EntrySection).entries.single()
        assertTrue("A small Spring Boot service built with Gradle." in withEntry.bullets)
        assertEquals(listOf("Spring Boot"), withEntry.tags)
        assertTrue(withoutEntry.bullets.none { "Spring Boot" in it })
        assertTrue(withoutEntry.tags.isEmpty())
        for (entry in listOf(withEntry, withoutEntry)) {
            assertEquals("hello-world", entry.heading)
            assertEquals("octocat/hello-world", entry.link?.text)
            assertEquals("https://github.com/octocat/hello-world", entry.link?.url)
            assertEquals("My first repository", entry.text)
            assertEquals("Kotlin · 42 stars", entry.subheading)
            assertTrue("Maintainer since 2022" in entry.bullets)
        }
    }

    @Test
    fun `open source off prints no open source section`() {
        val document = build(options = options(includeOpenSource = false))

        assertFalse("Open source projects" in titles(document))
    }

    @Test
    fun `preferences are never printed`() {
        val profile = profile().copy(
            preferences = Preferences(
                targetRoles = listOf("Tech Lead"),
                cantons = listOf("VS"),
                minSalaryChf = 150000,
            ),
        )

        val text = build(profile, options(language = CvLanguage.DE)).toString()

        assertFalse("Tech Lead" in text)
        assertFalse("VS" in text)
        assertFalse("150000" in text)
    }

    @Test
    fun `work authorisation is shown only when asked`() {
        val profile = profile().copy(workAuthorisation = WorkAuthorisation.PERMIT_B)

        assertEquals(listOf("Work authorisation: Swiss permit B (residence)"), build(profile, options()).facts)
        assertTrue(build(profile, options(includeWorkAuthorisation = false)).facts.isEmpty())
    }

    @Test
    fun `german labels and spelling`() {
        val document = build(options = options(language = CvLanguage.DE))

        assertEquals(
            listOf("Berufserfahrung", "Open-Source-Projekte", "Ausbildung", "Kenntnisse", "Sprachen", "Publikationen"),
            titles(document).drop(1),
        )
        assertEquals("Profil", titles(document).first())
        assertFalse("ß" in document.toString())
    }

    @Test
    fun `english labels`() {
        val document = build(options = options(language = CvLanguage.EN))

        assertEquals(
            listOf("Profile", "Experience", "Open source projects", "Education", "Skills", "Languages", "Publications"),
            titles(document),
        )
    }

    @Test
    fun `user text is never translated`() {
        val achievement = "Mise en œuvre de tests, à grande échelle"
        val profile = profile().copy(
            experience = profile().experience.map { it.copy(achievements = listOf(achievement)) },
        )

        for (language in CvLanguage.entries) {
            val experience = build(profile, options(language = language, sections = listOf(CvSectionKind.EXPERIENCE)))
            val entry = (experience.sections.single() as EntrySection).entries.first()
            assertEquals(listOf(achievement), entry.bullets)
        }
    }

    @Test
    fun `skills show their level in words and languages their level`() {
        val document = build(options = options(language = CvLanguage.DE, sections = listOf(CvSectionKind.SKILLS, CvSectionKind.LANGUAGES)))

        val skills = document.sections[0] as TagSection
        assertEquals(listOf("Kotlin (Expertise)", "PostgreSQL", "Spring Boot"), skills.tags)
        val languages = document.sections[1] as LineSection
        assertEquals(listOf("Englisch – Fortgeschritten", "Deutsch – Gute Kenntnisse"), languages.lines)
    }

    @Test
    fun `profile summary is printed as prose`() {
        val section = build(options = options(sections = listOf(CvSectionKind.PROFILE))).sections.single()

        assertEquals(ProseSection("Profile", listOf("Invented engineer used only in tests.")), section)
    }

    @Test
    fun `publication shows authors, venue, year and link`() {
        val section = build(options = options(sections = listOf(CvSectionKind.PUBLICATIONS))).sections.single() as EntrySection
        val entry = section.entries.single()

        assertEquals("Fast Joins on Skewed Data", entry.heading)
        assertEquals("J. Doe, A. Smith", entry.subheading)
        assertEquals("2021", entry.period)
        assertEquals("VLDB", entry.place)
        assertEquals(CvLink("https://example.org/papers/fast-joins", "https://example.org/papers/fast-joins"), entry.link)
    }

    @Test
    fun `no publications means no publications section`() {
        val document = build(profile().copy(publications = emptyList()), options())

        assertFalse("Publications" in titles(document))
    }
}
