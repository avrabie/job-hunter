package net.s4v3.backend.llm

enum class SkillImportance { REQUIRED, NICE_TO_HAVE }

/** One skill the job asks for. [related] holds at most 3 names the model took from the saved skill names it was given. */
data class AskedSkill(val name: String, val importance: SkillImportance, val related: List<String> = emptyList())

/**
 * Reads the skills a job description asks for. The description is untrusted data; only the skill NAMES of the saved profile
 * are sent along (to name related ones). Callers pass plain types and never see Koog, prompts or model answers.
 */
interface SkillExtractor {
    /** At most [MAX_SKILLS] skills of at most [MAX_NAME] characters. Throws [LlmFailure] when the model cannot be used or its answer is not usable. */
    fun extract(job: JobText, savedSkillNames: List<String>): List<AskedSkill>

    companion object {
        const val MAX_SKILLS = 40
        const val MAX_NAME = 60
        const val MAX_RELATED = 3
    }
}
