package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileUpdate
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
    const val SUMMARY_MAX = 4000
    const val ACHIEVEMENT_MAX = 600
    const val EXPERIENCE_MAX = 30
    const val ACHIEVEMENTS_PER_ENTRY_MAX = 20
    const val EDUCATION_MAX = 20
    const val SKILLS_MAX = 100
    const val LANGUAGES_MAX = 10
    const val TARGET_ROLES_MAX = 10
    const val CANTONS_MAX = 30
    const val PUBLICATIONS_MAX = 100
    const val URL_MAX = 500
    const val AUTHORS_MAX = 500

    private val languageCode = Regex("[a-z]{2}")
    private val cantonCode = Regex("[A-Z]{2}")

    fun validate(update: ProfileUpdate) {
        val problems = linkedMapOf<String, String>()
        if (update.version < 0) problems["version"] = "must not be negative"
        check(update.profile, problems)
        if (problems.isNotEmpty()) throw ProfileInvalidException(problems)
    }

    private fun check(profile: Profile, problems: MutableMap<String, String>) {
        profile.location?.let {
            optionalText("location.city", it.city, NAME_MAX, problems)
            it.canton?.let { canton -> if (!cantonCode.matches(canton)) problems["location.canton"] = "must be two uppercase letters" }
        }
        optionalText("summary", profile.summary, SUMMARY_MAX, problems)

        limit("languages", profile.languages.size, LANGUAGES_MAX, problems)
        profile.languages.forEachIndexed { i, language ->
            if (!languageCode.matches(language.code)) problems["languages[$i].code"] = "must be two lowercase letters"
        }

        limit("experience", profile.experience.size, EXPERIENCE_MAX, problems)
        profile.experience.forEachIndexed { i, entry ->
            val at = "experience[$i]"
            requiredText("$at.employer", entry.employer, NAME_MAX, problems)
            requiredText("$at.title", entry.title, NAME_MAX, problems)
            optionalText("$at.location", entry.location, NAME_MAX, problems)
            order("$at.end", entry.start, entry.end, problems)
            limit("$at.achievements", entry.achievements.size, ACHIEVEMENTS_PER_ENTRY_MAX, problems)
            entry.achievements.forEachIndexed { j, text -> requiredText("$at.achievements[$j]", text, ACHIEVEMENT_MAX, problems) }
        }

        limit("education", profile.education.size, EDUCATION_MAX, problems)
        profile.education.forEachIndexed { i, entry ->
            val at = "education[$i]"
            requiredText("$at.institution", entry.institution, NAME_MAX, problems)
            requiredText("$at.degree", entry.degree, NAME_MAX, problems)
            optionalText("$at.field", entry.field, NAME_MAX, problems)
            order("$at.end", entry.start, entry.end, problems)
        }

        limit("publications", profile.publications.size, PUBLICATIONS_MAX, problems)
        profile.publications.forEachIndexed { i, publication ->
            val at = "publications[$i]"
            requiredText("$at.title", publication.title, NAME_MAX, problems)
            optionalText("$at.venue", publication.venue, NAME_MAX, problems)
            publication.year?.let { if (it !in 1900..LocalDate.now().year + 1) problems["$at.year"] = "must be between 1900 and next year" }
            optionalText("$at.authors", publication.authors, AUTHORS_MAX, problems)
            publication.url?.let { httpUrl("$at.url", it, problems) }
        }

        limit("skills", profile.skills.size, SKILLS_MAX, problems)
        profile.skills.forEachIndexed { i, skill -> requiredText("skills[$i].name", skill.name, NAME_MAX, problems) }

        val prefs = profile.preferences
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

    private fun httpUrl(path: String, value: String, problems: MutableMap<String, String>) {
        when {
            value.length > URL_MAX -> problems[path] = "must be at most $URL_MAX characters"
            !isHttpUrl(value) -> problems[path] = "must be an absolute http or https address"
        }
    }

    private fun isHttpUrl(value: String): Boolean =
        try {
            val uri = URI(value)
            uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
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
