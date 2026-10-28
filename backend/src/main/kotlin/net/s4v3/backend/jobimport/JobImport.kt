package net.s4v3.backend.jobimport

import java.time.Duration
import java.util.UUID

/**
 * The hiring company's address and the application contact, as the ad states them. A null field is unknown. Personal data of a
 * third party (the contact person): never logged. [ImportEdits] uses the same type, where a blank value clears a field.
 */
data class ImportContact(
    val street: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val person: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

/** The body of a preview. With [text] (the page's text, pasted by the user) nothing is fetched. */
data class ImportRequest(val url: String?, val text: String? = null)

/** The extracted job, held as a draft owned by the caller until [JobImportService.confirm]. */
data class ImportPreview(
    val draftId: UUID,
    val title: String,
    val company: String,
    val location: String?,
    val description: String,
    val language: String,
    val contact: ImportContact = ImportContact(),
)

/**
 * Fields the user corrected in the review step; null keeps the draft's value. The URL cannot be edited. In [contact] a null
 * field keeps the draft's value and a blank one clears it.
 */
data class ImportEdits(
    val title: String? = null,
    val company: String? = null,
    val location: String? = null,
    val description: String? = null,
    val contact: ImportContact? = null,
)

/** [created] is false when the caller had already imported the same link; [applicationId] is then the existing application. */
data class ImportConfirmation(val applicationId: UUID, val created: Boolean)

/** Fixed error codes of the import. The code is the only thing ever reported or logged about a failure. */
enum class ImportError(val code: String) {
    INVALID_URL("invalid_url"),
    UNSUPPORTED_SITE("unsupported_site"),
    BLOCKED_ADDRESS("blocked_address"),
    UNREACHABLE("unreachable"),
    NOT_HTML("not_html"),
    TOO_LARGE("too_large"),
    UNREADABLE("unreadable"),
    NOT_A_JOB("not_a_job"),
    EXTRACTION_FAILED("extraction_failed"),
    LLM_UNAVAILABLE("llm_unavailable"),
    RATE_LIMITED("rate_limited"),
    DRAFT_NOT_FOUND("draft_not_found"),
    INVALID_EDIT("invalid_edit"),
    APPLICATION_LIMIT("application_limit"),
}

/** [retryAfter] is set for [ImportError.RATE_LIMITED]. The message is the code, never input. */
class JobImportException(val error: ImportError, val retryAfter: Duration? = null) : RuntimeException(error.code)

/** Imports a job from a link or pasted text. Every method is scoped by [sub], the token's `sub`. */
interface JobImportService {

    /** Fetches (or takes the pasted text), extracts and stores a draft; nothing is created yet. Throws [JobImportException]. */
    fun preview(sub: String, request: ImportRequest): ImportPreview

    /** Turns the caller's unexpired draft into an application of the caller only (no posting is created). Throws [JobImportException]. */
    fun confirm(sub: String, draftId: UUID, edits: ImportEdits = ImportEdits()): ImportConfirmation

    /** Removes the user's drafts. For the account deletion flow (applications go with the user row). */
    fun removeUserData(sub: String)
}
