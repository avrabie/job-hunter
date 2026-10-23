package net.s4v3.backend.llm

import net.s4v3.backend.llm.internal.CvProfileDraft
import net.s4v3.backend.llm.internal.DraftMapper
import net.s4v3.backend.llm.internal.EducationDraft
import net.s4v3.backend.llm.internal.ExperienceDraft
import net.s4v3.backend.llm.internal.LanguageDraft
import net.s4v3.backend.llm.internal.LocationDraft
import net.s4v3.backend.llm.internal.SkillDraft
import net.s4v3.backend.profile.LanguageLevel
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.SkillLevel
import net.s4v3.backend.profile.WorkAuthorisation
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftMapperTests {

    @Test
    fun `a full draft maps in order`() {
        val profile = DraftMapper.toProfile(
            CvProfileDraft(
                location = LocationDraft("Zurich", "zh"),
                workAuthorisation = "permit_b",
                languages = listOf(LanguageDraft("EN", "c1"), LanguageDraft("de", "NATIVE")),
                summary = "  Backend engineer.  ",
                experience = listOf(
                    ExperienceDraft("Acme", "Engineer", "2022-03", null, "Zurich", listOf("Built X", " ", "Led Y")),
                    ExperienceDraft("Beta", "Junior", "2019-01", "2022-02"),
                ),
                education = listOf(EducationDraft("ETH", "MSc", "CS", "2016-09", "2018-08")),
                skills = listOf(SkillDraft("Kotlin", "expert"), SkillDraft("SQL")),
            ),
        )

        assertEquals("Zurich", profile.location?.city)
        assertEquals("ZH", profile.location?.canton)
        assertEquals(WorkAuthorisation.PERMIT_B, profile.workAuthorisation)
        assertEquals(listOf("en", "de"), profile.languages.map { it.code })
        assertEquals(listOf(LanguageLevel.C1, LanguageLevel.NATIVE), profile.languages.map { it.level })
        assertEquals("Backend engineer.", profile.summary)
        assertEquals(listOf("Acme", "Beta"), profile.experience.map { it.employer })
        assertEquals(YearMonth.of(2022, 3), profile.experience[0].start)
        assertNull(profile.experience[0].end)
        assertEquals(listOf("Built X", "Led Y"), profile.experience[0].achievements)
        assertEquals(YearMonth.of(2022, 2), profile.experience[1].end)
        assertEquals(YearMonth.of(2016, 9), profile.education.single().start)
        assertEquals(listOf(SkillLevel.EXPERT, null), profile.skills.map { it.level })
    }

    @Test
    fun `unknown location and permit stay null`() {
        val profile = DraftMapper.toProfile(CvProfileDraft(location = LocationDraft(null, null)))

        assertNull(profile.location)
        assertNull(profile.workAuthorisation)
    }

    @Test
    fun `an invalid permit is dropped and the rest is kept`() {
        val profile = DraftMapper.toProfile(
            CvProfileDraft(workAuthorisation = "CITIZEN", skills = listOf(SkillDraft("Kotlin"))),
        )

        assertNull(profile.workAuthorisation)
        assertEquals(listOf("Kotlin"), profile.skills.map { it.name })
    }

    @Test
    fun `invalid optional values become null`() {
        val profile = DraftMapper.toProfile(
            CvProfileDraft(
                location = LocationDraft("Bern", "Bern"),
                experience = listOf(ExperienceDraft("Acme", "Dev", "2020-02", "2021-13")),
                education = listOf(EducationDraft("ETH", "BSc", start = "last year", end = "2019-06")),
                skills = listOf(SkillDraft("Kotlin", "GURU")),
            ),
        )

        assertNull(profile.location?.canton)
        assertEquals("Bern", profile.location?.city)
        assertNull(profile.experience.single().end)
        assertNull(profile.education.single().start)
        assertEquals(YearMonth.of(2019, 6), profile.education.single().end)
        assertNull(profile.skills.single().level)
    }

    @Test
    fun `entries missing a required value are left out`() {
        val profile = DraftMapper.toProfile(
            CvProfileDraft(
                languages = listOf(LanguageDraft("en", "fluent"), LanguageDraft("english", "C1"), LanguageDraft("fr", "B2")),
                experience = listOf(
                    ExperienceDraft("Acme", "Dev", "soon"),
                    ExperienceDraft("", "Dev", "2020-01"),
                    ExperienceDraft("Beta", "Dev", "2021-05"),
                ),
                skills = listOf(SkillDraft("  ")),
            ),
        )

        assertEquals(listOf("fr"), profile.languages.map { it.code })
        assertEquals(listOf("Beta"), profile.experience.map { it.employer })
        assertEquals(emptyList(), profile.skills)
    }

    @Test
    fun `an empty draft maps to an empty profile`() {
        assertEquals(Profile(), DraftMapper.toProfile(CvProfileDraft()))
    }
}
