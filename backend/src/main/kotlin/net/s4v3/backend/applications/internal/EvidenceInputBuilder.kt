package net.s4v3.backend.applications.internal

import net.s4v3.backend.llm.EvidencePosition
import net.s4v3.backend.llm.EvidenceProject
import net.s4v3.backend.llm.SkillEvidenceInput
import net.s4v3.backend.llm.SkillExtractor
import net.s4v3.backend.profile.Profile

/**
 * Builds what the skill extractor may see of a profile: skill names, positions and projects with their place in the saved
 * lists. Never the name, contact data, salary, preferences or work authorisation. Caps: an achievement 300 characters, 12
 * positions, 10 projects, 15 000 characters in all (positions first); what does not fit is left out.
 */
internal object EvidenceInputBuilder {

    fun build(profile: Profile): SkillEvidenceInput {
        var budget = SkillExtractor.MAX_EVIDENCE_CHARS
        fun take(text: String?): String? {
            val t = text?.trim().orEmpty()
            if (t.isEmpty() || t.length > budget) return null
            budget -= t.length
            return t
        }

        val positions = ArrayList<EvidencePosition>()
        for ((i, e) in profile.experience.withIndex()) {
            if (positions.size >= SkillExtractor.MAX_POSITIONS) break
            val title = take(e.title) ?: break
            val employer = take(e.employer) ?: break
            val achievements = e.achievements.mapNotNull { take(it.trim().take(SkillExtractor.MAX_ACHIEVEMENT)) }
            val technologies = e.technologies.mapNotNull { take(it) }
            positions += EvidencePosition(i, title, employer, achievements, technologies)
        }
        val projects = ArrayList<EvidenceProject>()
        for ((i, p) in profile.openSourceProjects.withIndex()) {
            if (projects.size >= SkillExtractor.MAX_PROJECTS) break
            val name = take(p.name) ?: break
            projects += EvidenceProject(i, name, take(p.description), take(p.analysis?.summary))
        }
        return SkillEvidenceInput(profile.skills.map { it.name }, positions, projects)
    }
}
