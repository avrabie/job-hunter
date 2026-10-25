package net.s4v3.backend.llm

import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.OpenSourceProject
import net.s4v3.backend.profile.Skill

/**
 * Writes a motivation letter (Markdown) from a job and the chosen parts of a profile. Callers pass plain request types and
 * get text back, and never see Koog, prompts or model answers.
 */
interface LetterWriter {
    /** Throws [LlmFailure] with a fixed message when the model cannot be used or its answer is not usable. */
    fun write(request: LetterRequest): String
}

enum class LetterLanguage { EN, DE }

/** The job as stored: [description] is untrusted text and is sent to the model as quoted data only. */
data class JobText(val title: String, val company: String, val description: String)

/** The only facts about the person that may reach the model: the name and the chosen parts. */
data class Candidate(
    val fullName: String,
    val experience: List<Experience> = emptyList(),
    val projects: List<OpenSourceProject> = emptyList(),
    val skills: List<Skill> = emptyList(),
)

/** A comment on one paragraph (numbered from 1, as the user sees them) of the previous letter. */
data class ParagraphComment(val n: Int, val text: String)

/** The earlier version to rewrite, with the user's review comments. */
data class PreviousLetter(
    val text: String,
    val generalComment: String? = null,
    val paragraphComments: List<ParagraphComment> = emptyList(),
)

/** A note the candidate wrote about a skill the job asks for and the profile lacks. [text] is the candidate's own words. */
data class GapNote(val skill: String, val text: String)

data class LetterRequest(
    val job: JobText,
    val candidate: Candidate,
    val language: LetterLanguage,
    val instructions: String? = null,
    val previous: PreviousLetter? = null,
    val gapNotes: List<GapNote> = emptyList(),
)
