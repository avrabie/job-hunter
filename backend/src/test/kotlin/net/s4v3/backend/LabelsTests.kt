package net.s4v3.backend

import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.internal.Labels
import net.s4v3.backend.profile.LanguageLevel
import net.s4v3.backend.profile.SkillLevel
import net.s4v3.backend.profile.WorkAuthorisation
import org.junit.jupiter.api.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LabelsTests {

    @Test
    fun `english headings`() {
        assertEquals(
            listOf("Profile", "Experience", "Open source projects", "Education", "Skills", "Languages", "Publications"),
            CvSectionKind.entries.map { Labels.heading(it, CvLanguage.EN) },
        )
    }

    @Test
    fun `german headings`() {
        assertEquals(
            listOf("Profil", "Berufserfahrung", "Open-Source-Projekte", "Ausbildung", "Kenntnisse", "Sprachen", "Publikationen"),
            CvSectionKind.entries.map { Labels.heading(it, CvLanguage.DE) },
        )
    }

    @Test
    fun `no label contains a sharp s in either language`() {
        val labels = buildList {
            CvLanguage.entries.forEach { language ->
                CvSectionKind.entries.forEach { add(Labels.heading(it, language)) }
                add(Labels.present(language))
                add(Labels.workAuthorisationLabel(language))
                SkillLevel.entries.forEach { add(Labels.skillLevel(it, language)) }
                LanguageLevel.entries.forEach { add(Labels.languageLevel(it, language)) }
                WorkAuthorisation.entries.forEach { add(Labels.workAuthorisation(it, language)) }
                add(Labels.stars(2, language))
            }
        }
        assertFalse(labels.any { "ß" in it }, labels.toString())
    }

    @Test
    fun `months read Mar 2021 in english and 03 2021 in german`() {
        val march = YearMonth.of(2021, 3)
        assertEquals("Mar 2021", Labels.month(march, CvLanguage.EN))
        assertEquals("03.2021", Labels.month(march, CvLanguage.DE))
    }

    @Test
    fun `current position reads Present or heute`() {
        val start = YearMonth.of(2021, 3)
        assertEquals("Mar 2021 – Present", Labels.period(start, null, CvLanguage.EN))
        assertEquals("03.2021 – heute", Labels.period(start, null, CvLanguage.DE))
    }

    @Test
    fun `closed period shows both months`() {
        val start = YearMonth.of(2017, 9)
        val end = YearMonth.of(2021, 2)
        assertEquals("Sep 2017 – Feb 2021", Labels.period(start, end, CvLanguage.EN))
        assertEquals("09.2017 – 02.2021", Labels.period(start, end, CvLanguage.DE))
    }

    @Test
    fun `missing start and end give no period, a missing start prints the end`() {
        assertNull(Labels.period(null, null, CvLanguage.EN))
        assertEquals("Jun 2016", Labels.period(null, YearMonth.of(2016, 6), CvLanguage.EN))
    }

    @Test
    fun `skill and language levels are words`() {
        assertEquals("Expert", Labels.skillLevel(SkillLevel.EXPERT, CvLanguage.EN))
        assertEquals("Experte", Labels.skillLevel(SkillLevel.EXPERT, CvLanguage.DE))
        assertEquals("Advanced", Labels.languageLevel(LanguageLevel.C1, CvLanguage.EN))
        assertEquals("Fortgeschritten", Labels.languageLevel(LanguageLevel.C1, CvLanguage.DE))
        assertEquals("Native", Labels.languageLevel(LanguageLevel.NATIVE, CvLanguage.EN))
        assertEquals("Muttersprache", Labels.languageLevel(LanguageLevel.NATIVE, CvLanguage.DE))
    }

    @Test
    fun `work authorisation is in words`() {
        assertEquals("Swiss permit B (residence)", Labels.workAuthorisation(WorkAuthorisation.PERMIT_B, CvLanguage.EN))
        assertEquals("Aufenthaltsbewilligung B", Labels.workAuthorisation(WorkAuthorisation.PERMIT_B, CvLanguage.DE))
    }

    @Test
    fun `language names follow the CV language`() {
        assertEquals("English", Labels.languageName("en", CvLanguage.EN))
        assertEquals("Englisch", Labels.languageName("en", CvLanguage.DE))
        assertEquals("Französisch", Labels.languageName("fr", CvLanguage.DE))
    }

    @Test
    fun `stars use singular for one`() {
        assertEquals("1 star", Labels.stars(1, CvLanguage.EN))
        assertEquals("42 stars", Labels.stars(42, CvLanguage.EN))
        assertEquals("42 Sterne", Labels.stars(42, CvLanguage.DE))
    }
}
