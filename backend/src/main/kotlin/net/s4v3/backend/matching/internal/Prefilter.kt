package net.s4v3.backend.matching.internal

import net.s4v3.backend.postings.Posting
import net.s4v3.backend.profile.LanguageLevel
import net.s4v3.backend.profile.Profile

/** [reasons] are fixed codes only: no profile text and no posting text. */
internal data class MatchResult(val passed: Boolean, val score: Int, val reasons: List<String>)

/**
 * The deterministic prefilter: hard rules first, then a relevance score from the title, the skills and the language.
 * A rule whose posting value is unknown, or whose user preference is empty, never fails. Pure: no database, no logging.
 */
internal object Prefilter {

    const val ROLE_POINTS = 50
    const val SKILL_POINTS = 40
    const val SKILL_CAP = 5
    const val LANGUAGE_POINTS = 10

    private val stopWords = setOf("and", "the", "for", "with", "und", "der", "die", "das", "für", "mit", "bei", "von", "des")
    private val goodLevels = setOf(LanguageLevel.B2, LanguageLevel.C1, LanguageLevel.C2, LanguageLevel.NATIVE)

    fun evaluate(profile: Profile, posting: Posting, threshold: Int): MatchResult {
        val reasons = mutableListOf<String>()
        val rejected = hardRules(profile, posting, reasons)
        val role = roleScore(profile, posting.title).also { if (it > 0) reasons += "ROLE_MATCH" }
        val hits = skillHits(profile, posting)
        val skills = SKILL_POINTS * minOf(hits, SKILL_CAP) / SKILL_CAP
        if (hits > 0) reasons += "SKILL_MATCH_$hits"
        val language = if (speaks(profile, posting.language)) LANGUAGE_POINTS else 0
        if (language > 0) reasons += "LANGUAGE_MATCH"

        val score = role + skills + language
        if (!rejected && score < threshold) reasons += "BELOW_THRESHOLD"
        return MatchResult(passed = !rejected && score >= threshold, score = score, reasons = reasons)
    }

    /** Appends the reason codes of the hard rules and returns true if any of them failed. */
    private fun hardRules(profile: Profile, posting: Posting, reasons: MutableList<String>): Boolean {
        var rejected = false
        val prefs = profile.preferences
        if (posting.outOfScope) {
            reasons += "OUT_OF_SCOPE"
            rejected = true
        }
        if (prefs.cantons.isNotEmpty()) {
            val canton = posting.canton
            when {
                canton.isNullOrBlank() -> reasons += "UNKNOWN_CANTON"
                prefs.cantons.none { it.equals(canton, ignoreCase = true) } -> {
                    reasons += "CANTON_MISMATCH"
                    rejected = true
                }
            }
        }
        if (prefs.workloadMin != null || prefs.workloadMax != null) {
            if (posting.workloadMin == null && posting.workloadMax == null) {
                reasons += "UNKNOWN_WORKLOAD"
            } else {
                val userLow = prefs.workloadMin ?: 0
                val userHigh = prefs.workloadMax ?: 100
                val postLow = posting.workloadMin ?: 0
                val postHigh = posting.workloadMax ?: 100
                if (userLow > postHigh || postLow > userHigh) {
                    reasons += "WORKLOAD_MISMATCH"
                    rejected = true
                }
            }
        }
        if (prefs.remoteModes.isNotEmpty()) {
            val mode = posting.remoteMode
            when {
                mode == null -> reasons += "UNKNOWN_REMOTE"
                prefs.remoteModes.none { it.name == mode.name } -> {
                    reasons += "REMOTE_MISMATCH"
                    rejected = true
                }
            }
        }
        val minSalary = prefs.minSalaryChf
        if (minSalary != null) {
            val max = posting.salaryMax
            when {
                max == null -> reasons += "UNKNOWN_SALARY"
                max < minSalary -> {
                    reasons += "SALARY_BELOW_MINIMUM"
                    rejected = true
                }
            }
        }
        return rejected
    }

    /** Up to [ROLE_POINTS]: the share of a role's words found in the title, best over the target roles (or past titles). */
    private fun roleScore(profile: Profile, title: String): Int {
        val roles = profile.preferences.targetRoles.ifEmpty { profile.experience.map { it.title } }.distinct()
        return roles.maxOfOrNull { role ->
            val words = role.split(Regex("[^\\p{L}\\p{N}]+")).map { it.lowercase() }
                .filter { it.length >= 3 && it !in stopWords }.distinct()
            if (words.isEmpty()) 0 else ROLE_POINTS * words.count { containsWord(title, it) } / words.size
        } ?: 0
    }

    private fun skillHits(profile: Profile, posting: Posting): Int =
        profile.skills.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
            .count { containsWord(posting.title, it) || containsWord(posting.description, it) }

    private fun speaks(profile: Profile, language: String): Boolean =
        profile.languages.any { it.code.equals(language, ignoreCase = true) && it.level in goodLevels }

    /** Whole-word, case-insensitive match; symbols in [word] (`C++`, `.NET`) are literal. */
    private fun containsWord(text: String, word: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
}
