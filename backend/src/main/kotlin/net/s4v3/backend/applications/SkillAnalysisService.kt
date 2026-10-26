package net.s4v3.backend.applications

import net.s4v3.backend.llm.EvidenceKind
import net.s4v3.backend.llm.SkillImportance
import java.time.Instant
import java.util.UUID

/** A skill the job asks for and the profile has; [profileIndex] is its place in the saved profile's skill list. */
data class IncludedSkill(val name: String, val importance: SkillImportance, val profileIndex: Int, val profileName: String)

/** A saved skill of the profile. */
data class SavedSkillRef(val name: String, val profileIndex: Int)

/** A skill the job asks for, not in the skills list, that a position or project of the current profile supports ([quote] is the user's own text). */
data class EvidencedSkill(
    val name: String,
    val importance: SkillImportance,
    val kind: EvidenceKind,
    val index: Int,
    val source: String,
    val quote: String,
)

/** A skill the job asks for and the profile lacks, with related saved skills and the candidate's note. */
data class GapSkill(val name: String, val importance: SkillImportance, val related: List<SavedSkillRef>, val note: String?)

/** What the caller sees of an application's skill analysis. */
sealed interface SkillsState {
    val analysed: Boolean

    data object NotAnalysed : SkillsState {
        override val analysed: Boolean = false
    }

    /** The lists are derived from the caller's current saved profile. */
    data class Analysed(
        val createdAt: Instant,
        val include: List<IncludedSkill>,
        val evidenced: List<EvidencedSkill>,
        val gaps: List<GapSkill>,
        val other: List<SavedSkillRef>,
    ) : SkillsState {
        override val analysed: Boolean = true
    }
}

/** A note the caller sends for a gap skill. */
data class SkillNoteInput(val skill: String, val text: String)

/** The notes break a rule (too many, too long, HTML, blank, or not a current gap). */
class SkillNotesInvalidException : RuntimeException("notes_invalid")

/** The user used up the hourly skill analyses. */
class SkillsRateLimitedException : RuntimeException("Skill analysis limit reached")

/**
 * Skill analysis of the caller's applications. Null results mean "no such application (or analysis) of the caller's".
 * [analyse] throws [net.s4v3.backend.llm.LlmFailure] when the model cannot be used.
 */
interface SkillAnalysisService {
    /** Reads the stored analysis; never calls the model. Null when the application is not the caller's. */
    fun get(sub: String, applicationId: UUID): SkillsState?

    /** Asks the model, replaces the stored analysis and keeps the notes whose skill is still a gap. Counts against the skills limit. */
    fun analyse(sub: String, applicationId: UUID): SkillsState.Analysed?

    /** Replaces all notes. Null when there is no such application or no analysis. Throws [SkillNotesInvalidException]. */
    fun saveNotes(sub: String, applicationId: UUID, notes: List<SkillNoteInput>): SkillsState.Analysed?

    /** The skills currently EVIDENCED by the caller's profile for this application, for the letter. Empty without analysis. */
    fun evidenced(sub: String, applicationId: UUID): List<EvidencedSkill>

    /** The notes of the application's current gaps, for the letter writer. Empty without analysis. */
    fun gapNotes(sub: String, applicationId: UUID): List<SkillNoteInput>

    companion object {
        const val MAX_NOTES = 20
        const val MAX_NOTE = 500
    }
}
