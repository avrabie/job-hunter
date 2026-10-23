package net.s4v3.backend.llm.internal

import net.s4v3.backend.profile.Education
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.LanguageLevel
import net.s4v3.backend.profile.LanguageSkill
import net.s4v3.backend.profile.Location
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.Project
import net.s4v3.backend.profile.ProjectLink
import net.s4v3.backend.profile.ProjectType
import net.s4v3.backend.profile.Publication
import net.s4v3.backend.profile.Skill
import net.s4v3.backend.profile.SkillLevel
import net.s4v3.backend.profile.WorkAuthorisation
import java.time.LocalDate
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
            publications = draft.publications.mapNotNull(::publication),
            skills = draft.skills.mapNotNull(::skill),
            projects = draft.projects.mapNotNull(::project),
        )

    fun toProfile(section: ExperienceSection): Profile = Profile(experience = section.experience.mapNotNull(::experience))

    fun toProfile(section: EducationSection): Profile = Profile(education = section.education.mapNotNull(::education))

    fun toProfile(section: PublicationsSection): Profile = Profile(publications = section.publications.mapNotNull(::publication))

    fun toProfile(section: ProjectsSection): Profile = Profile(projects = section.projects.mapNotNull(::project))

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
            technologies = tags(draft.technologies),
        )
    }

    private fun education(draft: EducationDraft): Education? {
        val institution = text(draft.institution) ?: return null
        val degree = text(draft.degree) ?: return null
        return Education(institution, degree, text(draft.field), month(draft.start), month(draft.end))
    }

    private fun publication(draft: PublicationDraft): Publication? {
        val title = text(draft.title) ?: return null
        return Publication(title, text(draft.venue), year(draft.year), text(draft.authors), link(draft.url))
    }

    /** A project without a name is left out. An unknown type becomes PERSONAL, the least specific honest choice. */
    private fun project(draft: ProjectDraft): Project? {
        val name = text(draft.name) ?: return null
        return Project(
            name = name,
            type = enumOrNull<ProjectType>(draft.type) ?: ProjectType.PERSONAL,
            context = text(draft.context),
            start = month(draft.start),
            end = month(draft.end),
            description = text(draft.description),
            technologies = tags(draft.technologies),
            results = draft.results.mapNotNull(::text).take(RESULTS_MAX),
            links = draft.links.mapNotNull(::projectLink),
            highlight = false,
        )
    }

    private fun projectLink(draft: ProjectLinkDraft): ProjectLink? {
        val label = text(draft.label) ?: return null
        val url = link(draft.url) ?: return null
        return ProjectLink(label, url)
    }

    /**
     * Trims, drops blanks, tags longer than 60 characters (a sentence is not a tag) and case-insensitive duplicates (the
     * first spelling wins), keeps at most 30. What the profile rules would reject on Save is not drafted in the first place.
     */
    private fun tags(values: List<String>): List<String> {
        val seen = HashSet<String>()
        return values.mapNotNull(::text)
            .filter { it.length <= TAG_MAX && seen.add(it.lowercase()) }
            .take(TAGS_MAX)
    }

    private fun year(value: String?): Int? =
        text(value)?.toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year + 1 }

    private fun link(value: String?): String? =
        text(value)?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }

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

    private const val TAGS_MAX = 30
    private const val TAG_MAX = 60
    private const val RESULTS_MAX = 20
    private val CANTON = Regex("[A-Z]{2}")
    private val LANGUAGE_CODE = Regex("[a-z]{2}")
}
