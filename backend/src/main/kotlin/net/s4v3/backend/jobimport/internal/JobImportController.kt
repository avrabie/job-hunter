package net.s4v3.backend.jobimport.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.jobimport.ImportEdits
import net.s4v3.backend.jobimport.ImportError
import net.s4v3.backend.jobimport.ImportRequest
import net.s4v3.backend.jobimport.JobImportException
import net.s4v3.backend.jobimport.JobImportService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import kotlin.math.max

/** The body of `POST /api/job-imports`. The URL is never taken from here, only from the draft. */
internal data class ConfirmImportRequest(
    val draftId: String? = null,
    val title: String? = null,
    val company: String? = null,
    val location: String? = null,
    val description: String? = null,
)

/** Imports a job from a link. The owner is always the token's `sub`. */
@RestController
@Tag(name = "Job imports", description = "Add a job from a link or pasted text: preview the extracted fields, then confirm to track it")
internal class JobImportController(
    private val imports: JobImportService,
    private val currentUser: CurrentUser,
) {

    @PostMapping("/api/job-imports/preview")
    @Operation(
        summary = "Preview a job import",
        description = "Body `{\"url\": \"https://...\"}`, or `{\"url\", \"text\"}` with the page's text pasted by the user (then nothing is " +
            "fetched). Fetches the https page, extracts title, company, location, description and language with the language model " +
            "and stores a draft for one hour. Nothing is created yet. Counts against `jobhunter.import.previews-per-hour`. " +
            "Errors are `{\"error\": code, \"message\": ...}`.",
        responses = [
            ApiResponse(responseCode = "200", description = "`{draftId, title, company, location, description, language}`"),
            ApiResponse(responseCode = "400", description = "`invalid_url`, `unsupported_site` or `blocked_address`"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "422", description = "`not_html`, `too_large`, `not_a_job` or `unreadable` (paste the description)"),
            ApiResponse(responseCode = "429", description = "Hourly limit reached (`rate_limited`), with `Retry-After`"),
            ApiResponse(responseCode = "502", description = "`extraction_failed`, `unreachable` or `llm_unavailable`"),
        ],
    )
    fun preview(@RequestBody(required = false) request: ImportRequest?): ResponseEntity<Any> =
        respond(HttpStatus.OK) { imports.preview(currentUser.id(), request ?: ImportRequest(null)) }

    @PostMapping("/api/job-imports")
    @Operation(
        summary = "Confirm a job import",
        description = "Body `{draftId, title?, company?, location?, description?}`. Turns the caller's own unexpired draft into a shared " +
            "posting (source `user-import`, or the existing posting when the same URL or company, title and city is known) and an " +
            "INTERESTED application. Edits replace the draft's fields within their length limits. Counts against " +
            "`jobhunter.import.confirms-per-day`.",
        responses = [
            ApiResponse(responseCode = "201", description = "`{applicationId, postingId, created, postingExisted}`"),
            ApiResponse(responseCode = "400", description = "An edit is blank, too long or contains HTML (`invalid_edit`)"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such draft of the caller's, or it expired (`draft_not_found`)"),
            ApiResponse(responseCode = "409", description = "The caller already tracks 500 jobs (`application_limit`)"),
            ApiResponse(responseCode = "429", description = "Daily limit reached (`rate_limited`), with `Retry-After`"),
        ],
    )
    fun confirm(@RequestBody(required = false) request: ConfirmImportRequest?): ResponseEntity<Any> {
        val draftId = try {
            UUID.fromString(request?.draftId.orEmpty())
        } catch (_: IllegalArgumentException) {
            return error(JobImportException(ImportError.DRAFT_NOT_FOUND))
        }
        val edits = ImportEdits(request?.title, request?.company, request?.location, request?.description)
        return respond(HttpStatus.CREATED) { imports.confirm(currentUser.id(), draftId, edits) }
    }

    private fun respond(ok: HttpStatus, call: () -> Any): ResponseEntity<Any> =
        try {
            ResponseEntity.status(ok).body(call())
        } catch (e: JobImportException) {
            error(e)
        }

    private fun error(e: JobImportException): ResponseEntity<Any> {
        val builder = ResponseEntity.status(statusOf(e.error))
        e.retryAfter?.let { builder.header("Retry-After", max(1L, it.toSeconds()).toString()) }
        return builder.body(mapOf("error" to e.error.code, "message" to messageOf(e.error)))
    }

    private fun statusOf(error: ImportError) = when (error) {
        ImportError.INVALID_URL, ImportError.UNSUPPORTED_SITE, ImportError.BLOCKED_ADDRESS, ImportError.INVALID_EDIT -> HttpStatus.BAD_REQUEST
        ImportError.NOT_HTML, ImportError.TOO_LARGE, ImportError.NOT_A_JOB, ImportError.UNREADABLE -> HttpStatus.UNPROCESSABLE_CONTENT
        ImportError.EXTRACTION_FAILED, ImportError.UNREACHABLE, ImportError.LLM_UNAVAILABLE -> HttpStatus.BAD_GATEWAY
        ImportError.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS
        ImportError.DRAFT_NOT_FOUND -> HttpStatus.NOT_FOUND
        ImportError.APPLICATION_LIMIT -> HttpStatus.CONFLICT
    }

    private fun messageOf(error: ImportError) = when (error) {
        ImportError.INVALID_URL -> "The link must be a plain https address of a website."
        ImportError.UNSUPPORTED_SITE -> "This site is not supported. Paste the job description instead."
        ImportError.BLOCKED_ADDRESS -> "This address cannot be fetched."
        ImportError.UNREACHABLE -> "The page could not be fetched. Paste the job description instead."
        ImportError.NOT_HTML -> "The link does not point to a web page."
        ImportError.TOO_LARGE -> "The page is too large."
        ImportError.UNREADABLE -> "The page has too little readable text (it may need JavaScript or a login). Paste the job description instead."
        ImportError.NOT_A_JOB -> "The page does not look like a single job ad."
        ImportError.EXTRACTION_FAILED -> "The job could not be read reliably from the page. Paste the job description instead."
        ImportError.LLM_UNAVAILABLE -> "The language model is not available."
        ImportError.RATE_LIMITED -> "Too many imports. Try again later."
        ImportError.DRAFT_NOT_FOUND -> "The draft does not exist or has expired."
        ImportError.INVALID_EDIT -> "An edited field is empty, too long or contains HTML."
        ImportError.APPLICATION_LIMIT -> "The limit of tracked jobs is reached."
    }
}
