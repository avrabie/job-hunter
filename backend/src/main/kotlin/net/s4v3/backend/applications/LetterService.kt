package net.s4v3.backend.applications

import java.time.Instant
import java.util.UUID

enum class LetterHow { GENERATED, REGENERATED, EDITED }

/** One line of the version list: no text. */
data class LetterSummary(
    val version: Int,
    val how: LetterHow,
    val language: String,
    val createdAt: Instant,
    val basedOnVersion: Int?,
)

/** The indexes of the saved profile's lists that went into a letter. */
data class LetterSelection(
    val experience: List<Int> = emptyList(),
    val projects: List<Int> = emptyList(),
    val skills: List<Int> = emptyList(),
    /** Names of evidenced skills (see [SkillAnalysisService.evidenced]); resolved by the server, never carrying text. */
    val evidenced: List<String> = emptyList(),
)

/** A comment on paragraph [n] (from 1, see [paragraphs]). */
data class LetterParagraphComment(val n: Int, val text: String)

data class LetterComments(val general: String? = null, val paragraphs: List<LetterParagraphComment> = emptyList())

/** One version with its text, the selection and instructions it was made from and the comments it used. */
data class LetterDetail(
    val version: Int,
    val how: LetterHow,
    val language: String,
    val createdAt: Instant,
    val basedOnVersion: Int?,
    val text: String,
    val selection: LetterSelection,
    val instructions: String?,
    val comments: LetterComments?,
)

/** What the user asks for. Indexes refer to the saved profile; no profile text is accepted. [language] is `EN` or `DE`, null = the posting's. */
data class GenerateLetterCommand(
    val language: String? = null,
    val selection: LetterSelection = LetterSelection(),
    val instructions: String? = null,
    val basedOnVersion: Int? = null,
    val comments: LetterComments? = null,
)

/** The selection, instructions or comments break a rule. */
class LetterSelectionInvalidException : RuntimeException("selection_invalid")

/** The text is empty, too long or contains HTML. */
class LetterTextInvalidException : RuntimeException("letter_text_invalid")

/** The application already has [LetterService.MAX_VERSIONS] versions. */
class LetterLimitException : RuntimeException("Letter version limit reached")

/** The user used up the hourly model calls. */
class LetterRateLimitedException : RuntimeException("Letter generation limit reached")

/** A rendered file for download. */
class LetterFile(val bytes: ByteArray, val fileName: String)

/** All PDF renders are busy. */
class LetterPdfBusyException : RuntimeException("Too many PDF renders at the moment")

/** The PDF could not be rendered. */
class LetterPdfFailedException(cause: Throwable) : RuntimeException("The letter PDF could not be rendered", cause)

/**
 * Motivation letters of the caller's applications. Null results mean "no such application or version of the caller's".
 * [generate] throws [net.s4v3.backend.llm.LlmFailure] when the model cannot be used.
 */
interface LetterService {
    fun generate(sub: String, applicationId: UUID, command: GenerateLetterCommand): LetterDetail?

    fun saveEdited(sub: String, applicationId: UUID, text: String): LetterDetail?

    /** Oldest first. Null when the application is not the caller's. */
    fun list(sub: String, applicationId: UUID): List<LetterSummary>?

    fun get(sub: String, applicationId: UUID, version: Int): LetterDetail?

    fun file(sub: String, applicationId: UUID, version: Int): LetterFile?

    /** The version as an A4 PDF. Throws [LetterPdfBusyException] or [LetterPdfFailedException]. */
    fun pdf(sub: String, applicationId: UUID, version: Int): LetterFile?

    fun delete(sub: String, applicationId: UUID, version: Int): Boolean

    companion object {
        const val MAX_VERSIONS = 20
        const val MAX_TEXT = 6000
        const val MAX_EXPERIENCE = 8
        const val MAX_PROJECTS = 6
        const val MAX_SKILLS = 40
        const val MAX_INSTRUCTIONS = 1000
        const val MAX_COMMENTS = 20
        const val MAX_COMMENT = 500
    }
}
