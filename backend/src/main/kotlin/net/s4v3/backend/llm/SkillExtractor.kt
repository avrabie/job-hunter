package net.s4v3.backend.llm

enum class SkillImportance { REQUIRED, NICE_TO_HAVE }

enum class EvidenceKind { EXPERIENCE, PROJECT }

/** A claim of the model that a position or project of the profile supports a skill. Only a claim: the caller must check it. */
data class AskedEvidence(val kind: EvidenceKind = EvidenceKind.EXPERIENCE, val index: Int = -1, val quote: String = "")

/** One skill the job asks for. [related] holds at most 3 names the model took from the saved skill names it was given. */
data class AskedSkill(
    val name: String,
    val importance: SkillImportance,
    val related: List<String> = emptyList(),
    val evidence: AskedEvidence? = null,
)

/** A position of the profile as the model sees it; [index] is its place in the saved profile. */
data class EvidencePosition(
    val index: Int,
    val title: String,
    val employer: String,
    val achievements: List<String> = emptyList(),
    val technologies: List<String> = emptyList(),
)

/** An open source project of the profile as the model sees it; [index] is its place in the saved profile. */
data class EvidenceProject(val index: Int, val name: String, val description: String? = null, val summary: String? = null)

/**
 * The only profile data the skill extractor may receive: skill names, positions and projects. Never the name, contact data,
 * salary, preferences or work authorisation.
 */
data class SkillEvidenceInput(
    val savedSkillNames: List<String> = emptyList(),
    val positions: List<EvidencePosition> = emptyList(),
    val projects: List<EvidenceProject> = emptyList(),
)

/**
 * Reads the skills a job description asks for. The description is untrusted data; the saved skill names and the profile's
 * positions and projects are sent along as data (to name related skills and to cite evidence). Callers pass plain types and never see Koog, prompts or model answers.
 */
interface SkillExtractor {
    /** At most [MAX_SKILLS] skills of at most [MAX_NAME] characters. Throws [LlmFailure] when the model cannot be used or its answer is not usable. */
    fun extract(job: JobText, evidenceInput: SkillEvidenceInput): List<AskedSkill>

    companion object {
        const val MAX_SKILLS = 40
        const val MAX_NAME = 60
        const val MAX_RELATED = 3
        const val MAX_QUOTE = 200
        const val MAX_ACHIEVEMENT = 300
        const val MAX_POSITIONS = 12
        const val MAX_PROJECTS = 10
        const val MAX_EVIDENCE_CHARS = 15_000
    }
}
