package net.s4v3.backend.llm.internal

import net.s4v3.backend.profile.Education
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.LanguageLevel
import net.s4v3.backend.profile.LanguageSkill
import net.s4v3.backend.profile.Location
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.Skill
import net.s4v3.backend.profile.SkillLevel
import net.s4v3.backend.profile.WorkAuthorisation
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * Turns the model's [CvProfileDraft] into a typed [Profile]. The model makes small mistakes, so a value that is not
 * allowed never fails the whole draft: an optional value becomes null, and an entry that lacks a value the profile
 * requires (an experience without a start month, a language without a level) is left out. The user reviews the draft
 * before anything is saved, and the normal profile validation still runs on Save.
 */
internal object DraftMapper {

    fun toProfile(draft: CvProfileDraft): Profile =
        Profile(
            location = location(draft.location),
            workAuthorisation = enumOrNull<WorkAuthorisation>(draft.workAuthorisation),
            languages = draft.languages.mapNotNull(::language),
            summary = text(draft.summary),
            experience = draft.experience.mapNotNull(::experience),
            education = draft.education.mapNotNull(::education),
            skills = draft.skills.mapNotNull(::skill),
        )

    private fun location(draft: LocationDraft?): Location? {
        val city = text(draft?.city)
        val canton = draft?.canton?.trim()?.uppercase()?.takeIf { CANTON.matches(it) }
        return if (city == null && canton == null) null else Location(city, canton)
    }

    private fun language(draft: LanguageDraft): LanguageSkill? {
        val code = draft.code.trim().lowercase().takeIf { LANGUAGE_CODE.matches(it) } ?: return null
        val level = enumOrNull<LanguageLevel>(draft.level) ?: return null
        return LanguageSkill(code, level)
    }

    private fun experience(draft: ExperienceDraft): Experience? {
        val employer = text(draft.employer) ?: return null
        val title = text(draft.title) ?: return null
        val start = month(draft.start) ?: return null
        return Experience(
            employer = employer,
            title = title,
            start = start,
            end = month(draft.end),
            location = text(draft.location),
            achievements = draft.achievements.mapNotNull(::text),
        )
    }

    private fun education(draft: EducationDraft): Education? {
        val institution = text(draft.institution) ?: return null
        val degree = text(draft.degree) ?: return null
        return Education(institution, degree, text(draft.field), month(draft.start), month(draft.end))
    }

    private fun skill(draft: SkillDraft): Skill? {
        val name = text(draft.name) ?: return null
        return Skill(name, enumOrNull<SkillLevel>(draft.level))
    }

    private fun text(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun month(value: String?): YearMonth? =
        try {
            text(value)?.let(YearMonth::parse)
        } catch (_: DateTimeParseException) {
            null
        }

    private inline fun <reified E : Enum<E>> enumOrNull(value: String?): E? {
        val wanted = text(value)?.uppercase() ?: return null
        return enumValues<E>().firstOrNull { it.name == wanted }
    }

    private val CANTON = Regex("[A-Z]{2}")
    private val LANGUAGE_CODE = Regex("[a-z]{2}")
}
