package net.s4v3.backend.applications.internal

import net.s4v3.backend.llm.AskedEvidence
import net.s4v3.backend.llm.EvidenceKind
import net.s4v3.backend.llm.SkillExtractor
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.OpenSourceProject
import net.s4v3.backend.profile.Profile

/** An evidence claim that passed [EvidenceCheck]: [source] and [quote] come from the profile entry, not from the model. */
internal data class EvidenceHit(val kind: EvidenceKind, val index: Int, val source: String, val quote: String)

/**
 * Checks the model's evidence claims against the CURRENT profile, in the style of ContactGrounding. A claim passes only if
 * the cited entry exists, the quote (at most 200 characters, no HTML) occurs in the entry's text after lowercasing and
 * collapsing whitespace, and the entry's text names the skill or an alias of it as a whole word. The model never decides.
 */
internal class EvidenceCheck(private val experience: List<Experience>, private val projects: List<OpenSourceProject>) {

    constructor(profile: Profile) : this(profile.experience, profile.openSourceProjects)

    fun check(skill: String, claim: AskedEvidence?): EvidenceHit? {
        claim ?: return null
        val quote = claim.quote.trim()
        if (quote.isEmpty() || quote.length > SkillExtractor.MAX_QUOTE || HTML.containsMatchIn(quote)) return null
        val (text, source) = entry(claim.kind, claim.index) ?: return null
        val haystack = normalise(text)
        if (normalise(quote) !in haystack) return null
        if (!namesSkill(haystack, skill)) return null
        return EvidenceHit(claim.kind, claim.index, source, quote)
    }

    /** The searchable text of an entry and the label shown for it, or null when the index is stale. */
    private fun entry(kind: EvidenceKind, index: Int): Pair<String, String>? = when (kind) {
        EvidenceKind.EXPERIENCE -> experience.getOrNull(index)?.let { e ->
            (listOf("${e.title} at ${e.employer}") + e.achievements + e.technologies.joinToString(", ")).joinToString("\n") to "${e.title} at ${e.employer}"
        }
        EvidenceKind.PROJECT -> projects.getOrNull(index)?.let { p ->
            listOfNotNull(p.name, p.description, p.analysis?.summary).joinToString("\n") to p.name
        }
    }

    companion object {
        val NONE = EvidenceCheck(emptyList(), emptyList())
        private val HTML = Regex("<\\s*/?\\s*[A-Za-z!]")
        private val WS = Regex("\\s+")

        /** Words the check treats as the same skill beyond [SkillMatcher]'s table (evidence only; they do not make a skill LISTED). */
        private val EXTRA_GROUPS = listOf(
            setOf("ai", "artificialintelligence", "llm", "llms", "genai", "generativeai", "largelanguagemodel", "largelanguagemodels"),
        )

        fun normalise(s: String): String = s.lowercase().replace(WS, " ").trim()

        /** [skill] or an alias occurs in [haystack] (already normalised) with no letter, digit, `+` or `#` touching it. */
        fun namesSkill(haystack: String, skill: String): Boolean {
            val group = SkillMatcher.aliasGroup(skill)
            val extra = EXTRA_GROUPS.firstOrNull { g -> group.any { it in g } }.orEmpty()
            val terms = HashSet<String>()
            terms += normalise(skill)
            terms += group
            terms += extra
            return terms.filter { it.isNotEmpty() }.any { wholeWord(haystack, it) }
        }

        private fun wholeWord(haystack: String, term: String): Boolean {
            // Long terms may be written with a separator between letters ("spring boot", "node.js"); short ones are exact.
            val body = if (term.length >= 5 && term.none { it.isWhitespace() }) {
                term.map { Regex.escape(it.toString()) }.joinToString("[\\s\\-./_]?")
            } else Regex.escape(term)
            return Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}+#])").containsMatchIn(haystack)
        }
    }
}
