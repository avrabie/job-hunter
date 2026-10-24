package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeAccount
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.Contact
import net.s4v3.backend.profile.Education
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.LanguageSkill
import net.s4v3.backend.profile.Location
import net.s4v3.backend.profile.OpenSourceProject
import net.s4v3.backend.profile.Preferences
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileUpdate
import net.s4v3.backend.profile.ProjectAnalysis
import net.s4v3.backend.profile.Publication
import net.s4v3.backend.profile.Skill
import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDate
import java.time.YearMonth

/**
 * The rules a profile must satisfy. Messages name the rule only: they never contain the submitted value, because the
 * profile is personal data and the messages end up in responses and logs.
 */
internal object ProfileValidator {

    const val NAME_MAX = 200
    const val CONTACT_TEXT_MAX = 200
    const val PHONE_MAX = 30
    const val SUMMARY_MAX = 4000
    const val ACHIEVEMENT_MAX = 600
    const val EXPERIENCE_MAX = 30
    const val ACHIEVEMENTS_PER_ENTRY_MAX = 20
    const val EDUCATION_MAX = 20
    const val SKILLS_MAX = 200
    const val SKILL_SOURCES_MAX = 20
    const val SOURCE_EVIDENCE_MAX = 300
    const val ANALYSIS_SUMMARY_MAX = 1500
    const val ANALYSIS_ARCHITECTURE_MAX = 2500
    const val TECH_STACK_MAX = 20
    const val TECHNOLOGY_NAME_MAX = 60
    const val EVIDENCE_PATH_MAX = 300
    const val TECHNOLOGY_EVIDENCE_MAX = 300
    const val ANALYSIS_FILES_MAX = 9
    const val ANALYSIS_FILE_PATH_MAX = 300
    const val LANGUAGES_MAX = 10
    const val TARGET_ROLES_MAX = 10
    const val CANTONS_MAX = 30
    const val PUBLICATIONS_MAX = 100
    const val URL_MAX = 500
    const val AUTHORS_MAX = 500
    const val OPEN_SOURCE_PROJECTS_MAX = 30
    const val REPOSITORY_DESCRIPTION_MAX = 500
    const val REPOSITORY_LANGUAGE_MAX = 60
    const val REPOSITORY_TOPICS_MAX = 20
    const val REPOSITORY_NOTE_MAX = 2000
    const val TAG_MAX = 60
    const val TAGS_MAX = 30

    private val languageCode = Regex("[a-z]{2}")
    private val cantonCode = Regex("[A-Z]{2}")
    private val emailShape = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    private val phoneCharacters = Regex("[0-9 +()/-]+")
    private val httpSchemes = setOf("http", "https")

    fun validate(update: ProfileUpdate) {
        val problems = linkedMapOf<String, String>()
        if (update.version < 0) problems["version"] = "must not be negative"
        check(update.profile, problems)
        if (problems.isNotEmpty()) throw ProfileInvalidException(problems)
    }

    private fun check(profile: Profile, problems: MutableMap<String, String>) {
        contact(profile.contact, problems)
        location(profile.location, problems)
        optionalText("summary", profile.summary, SUMMARY_MAX, problems)
        languages(profile.languages, problems)
        experience(profile.experience, problems)
        education(profile.education, problems)
        publications(profile.publications, problems)
        codeAccounts(profile.codeAccounts, problems)
        openSourceProjects(profile.openSourceProjects, problems)
        skills(profile.skills, problems)
        preferences(profile.preferences, problems)
    }

    private fun location(location: Location?, problems: MutableMap<String, String>) {
        location?.let {
            optionalText("location.city", it.city, NAME_MAX, problems)
            it.canton?.let { canton -> if (!cantonCode.matches(canton)) problems["location.canton"] = "must be two uppercase letters" }
        }
    }

    private fun languages(languages: List<LanguageSkill>, problems: MutableMap<String, String>) {
        limit("languages", languages.size, LANGUAGES_MAX, problems)
        languages.forEachIndexed { i, language ->
            if (!languageCode.matches(language.code)) problems["languages[$i].code"] = "must be two lowercase letters"
        }
    }

    private fun experience(experience: List<Experience>, problems: MutableMap<String, String>) {
        limit("experience", experience.size, EXPERIENCE_MAX, problems)
        experience.forEachIndexed { i, entry ->
            val at = "experience[$i]"
            requiredText("$at.employer", entry.employer, NAME_MAX, problems)
            requiredText("$at.title", entry.title, NAME_MAX, problems)
            optionalText("$at.location", entry.location, NAME_MAX, problems)
            order("$at.end", entry.start, entry.end, problems)
            limit("$at.achievements", entry.achievements.size, ACHIEVEMENTS_PER_ENTRY_MAX, problems)
            entry.achievements.forEachIndexed { j, text -> requiredText("$at.achievements[$j]", text, ACHIEVEMENT_MAX, problems) }
            tags("$at.technologies", entry.technologies, problems)
        }
    }

    private fun education(education: List<Education>, problems: MutableMap<String, String>) {
        limit("education", education.size, EDUCATION_MAX, problems)
        education.forEachIndexed { i, entry ->
            val at = "education[$i]"
            requiredText("$at.institution", entry.institution, NAME_MAX, problems)
            requiredText("$at.degree", entry.degree, NAME_MAX, problems)
            optionalText("$at.field", entry.field, NAME_MAX, problems)
            order("$at.end", entry.start, entry.end, problems)
        }
    }

    private fun publications(publications: List<Publication>, problems: MutableMap<String, String>) {
        limit("publications", publications.size, PUBLICATIONS_MAX, problems)
        publications.forEachIndexed { i, publication ->
            val at = "publications[$i]"
            requiredText("$at.title", publication.title, NAME_MAX, problems)
            optionalText("$at.venue", publication.venue, NAME_MAX, problems)
            publication.year?.let { if (it !in 1900..LocalDate.now().year + 1) problems["$at.year"] = "must be between 1900 and next year" }
            optionalText("$at.authors", publication.authors, AUTHORS_MAX, problems)
            publication.url?.let { httpUrl("$at.url", it, problems) }
        }
    }

    private fun codeAccounts(codeAccounts: List<CodeAccount>, problems: MutableMap<String, String>) {
        val providers = mutableSetOf<CodeProvider>()
        codeAccounts.forEachIndexed { i, account ->
            val at = "codeAccounts[$i]"
            if (!providers.add(account.provider)) problems["$at.provider"] = "must appear once per provider"
            if (!isValidUsername(account.provider, account.username)) {
                problems["$at.username"] = "must follow the username rule of the provider"
            }
        }
    }

    private fun openSourceProjects(openSourceProjects: List<OpenSourceProject>, problems: MutableMap<String, String>) {
        limit("openSourceProjects", openSourceProjects.size, OPEN_SOURCE_PROJECTS_MAX, problems)
        val repositories = mutableSetOf<Pair<CodeProvider, String>>()
        openSourceProjects.forEachIndexed { i, project ->
            val at = "openSourceProjects[$i]"
            requiredText("$at.fullName", project.fullName, NAME_MAX, problems)
            if (!repositories.add(project.provider to project.fullName)) problems["$at.fullName"] = "must appear once per provider"
            requiredText("$at.name", project.name, NAME_MAX, problems)
            httpUrl("$at.url", project.url, problems, setOf("https"))
            optionalText("$at.description", project.description, REPOSITORY_DESCRIPTION_MAX, problems)
            optionalText("$at.language", project.language, REPOSITORY_LANGUAGE_MAX, problems)
            limit("$at.topics", project.topics.size, REPOSITORY_TOPICS_MAX, problems)
            project.topics.forEachIndexed { j, topic -> requiredText("$at.topics[$j]", topic, TAG_MAX, problems) }
            project.stars?.let { if (it < 0) problems["$at.stars"] = "must not be negative" }
            optionalText("$at.note", project.note, REPOSITORY_NOTE_MAX, problems)
            project.analysis?.let { analysis(at, it, problems) }
        }
    }

    private fun skills(skills: List<Skill>, problems: MutableMap<String, String>) {
        limit("skills", skills.size, SKILLS_MAX, problems)
        skills.forEachIndexed { i, skill ->
            val at = "skills[$i]"
            requiredText("$at.name", skill.name, NAME_MAX, problems)
            limit("$at.sources", skill.sources.size, SKILL_SOURCES_MAX, problems)
            skill.sources.forEachIndexed { j, source ->
                val sourceAt = "$at.sources[$j]"
                requiredText("$sourceAt.fullName", source.fullName, NAME_MAX, problems)
                httpUrl("$sourceAt.url", source.url, problems, setOf("https"))
                requiredText("$sourceAt.evidence", source.evidence, SOURCE_EVIDENCE_MAX, problems)
            }
        }
    }

    private fun preferences(prefs: Preferences, problems: MutableMap<String, String>) {
        limit("preferences.targetRoles", prefs.targetRoles.size, TARGET_ROLES_MAX, problems)
        prefs.targetRoles.forEachIndexed { i, role -> requiredText("preferences.targetRoles[$i]", role, NAME_MAX, problems) }
        limit("preferences.cantons", prefs.cantons.size, CANTONS_MAX, problems)
        prefs.cantons.forEachIndexed { i, canton ->
            if (!cantonCode.matches(canton)) problems["preferences.cantons[$i]"] = "must be two uppercase letters"
        }
        prefs.workloadMin?.let { if (it !in 10..100) problems["preferences.workloadMin"] = "must be between 10 and 100" }
        prefs.workloadMax?.let { if (it !in 10..100) problems["preferences.workloadMax"] = "must be between 10 and 100" }
        if (prefs.workloadMin != null && prefs.workloadMax != null && prefs.workloadMin > prefs.workloadMax) {
            problems["preferences.workloadMax"] = "must not be below the minimum"
        }
        prefs.minSalaryChf?.let { if (it < 0) problems["preferences.minSalaryChf"] = "must not be negative" }
    }

    private fun contact(contact: Contact, problems: MutableMap<String, String>) {
        optionalText("contact.fullName", contact.fullName, CONTACT_TEXT_MAX, problems)
        optionalText("contact.headline", contact.headline, CONTACT_TEXT_MAX, problems)
        contact.email?.let { email ->
            optionalText("contact.email", email, CONTACT_TEXT_MAX, problems)
            if (problems["contact.email"] == null && !emailShape.matches(email)) {
                problems["contact.email"] = "must be an email address without spaces"
            }
        }
        contact.phone?.let { phone ->
            optionalText("contact.phone", phone, PHONE_MAX, problems)
            if (problems["contact.phone"] == null && !phoneCharacters.matches(phone)) {
                problems["contact.phone"] = "may contain only digits, spaces, +, -, (, ) and /"
            }
        }
        contact.website?.let { httpUrl("contact.website", it, problems, setOf("https")) }
        contact.linkedin?.let { httpUrl("contact.linkedin", it, problems, setOf("https")) }
    }

    private fun analysis(at: String, analysis: ProjectAnalysis, problems: MutableMap<String, String>) {
        val path = "$at.analysis"
        requiredText("$path.summary", analysis.summary, ANALYSIS_SUMMARY_MAX, problems)
        if (analysis.architecture.length > ANALYSIS_ARCHITECTURE_MAX) {
            problems["$path.architecture"] = "must be at most $ANALYSIS_ARCHITECTURE_MAX characters"
        }
        limit("$path.techStack", analysis.techStack.size, TECH_STACK_MAX, problems)
        analysis.techStack.forEachIndexed { i, technology ->
            val tech = "$path.techStack[$i]"
            requiredText("$tech.name", technology.name, TECHNOLOGY_NAME_MAX, problems)
            if (technology.evidencePath.length > EVIDENCE_PATH_MAX) {
                problems["$tech.evidencePath"] = "must be at most $EVIDENCE_PATH_MAX characters"
            }
            if (technology.evidence.length > TECHNOLOGY_EVIDENCE_MAX) {
                problems["$tech.evidence"] = "must be at most $TECHNOLOGY_EVIDENCE_MAX characters"
            }
        }
        limit("$path.files", analysis.files.size, ANALYSIS_FILES_MAX, problems)
        analysis.files.forEachIndexed { i, file ->
            if (file.length > ANALYSIS_FILE_PATH_MAX) problems["$path.files[$i]"] = "must be at most $ANALYSIS_FILE_PATH_MAX characters"
        }
    }

    private fun requiredText(path: String, value: String, max: Int, problems: MutableMap<String, String>) {
        when {
            value.isBlank() -> problems[path] = "must not be blank"
            value.length > max -> problems[path] = "must be at most $max characters"
        }
    }

    private fun optionalText(path: String, value: String?, max: Int, problems: MutableMap<String, String>) {
        if (value == null) return
        when {
            value.isBlank() -> problems[path] = "must not be blank when given"
            value.length > max -> problems[path] = "must be at most $max characters"
        }
    }

    private fun tags(path: String, values: List<String>, problems: MutableMap<String, String>) {
        limit(path, values.size, TAGS_MAX, problems)
        values.forEachIndexed { i, tag -> requiredText("$path[$i]", tag, TAG_MAX, problems) }
    }

    private fun httpUrl(path: String, value: String, problems: MutableMap<String, String>, schemes: Set<String> = httpSchemes) {
        when {
            value.length > URL_MAX -> problems[path] = "must be at most $URL_MAX characters"
            !isAbsoluteUrl(value, schemes) -> problems[path] = "must be an absolute ${schemes.joinToString(" or ")} address"
        }
    }

    private fun isAbsoluteUrl(value: String, schemes: Set<String>): Boolean =
        try {
            val uri = URI(value)
            uri.isAbsolute && uri.scheme.lowercase() in schemes && !uri.host.isNullOrEmpty()
        } catch (_: URISyntaxException) {
            false
        }

    private fun limit(path: String, size: Int, max: Int, problems: MutableMap<String, String>) {
        if (size > max) problems[path] = "must have at most $max entries"
    }

    private fun order(path: String, start: YearMonth?, end: YearMonth?, problems: MutableMap<String, String>) {
        if (start != null && end != null && end.isBefore(start)) problems[path] = "must not be before the start"
    }
}
