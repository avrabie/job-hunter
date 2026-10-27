package net.s4v3.backend.applications.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.applications.ApplicationLimitException
import net.s4v3.backend.applications.ApplicationNotAppliedException
import net.s4v3.backend.applications.ApplicationPatch
import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.applications.ApplicationStage
import net.s4v3.backend.applications.InvalidApplicationPatchException
import net.s4v3.backend.applications.PostingTracking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/** The body of `POST /api/applications`. */
internal data class TrackRequest(val postingId: UUID)

/** The body of `PATCH /api/applications/{id}`: an absent field is left as it is. `appliedOn` is `YYYY-MM-DD`. */
internal data class PatchRequest(val stage: String? = null, val notes: String? = null, val appliedOn: String? = null)

/** The caller's tracked jobs. The owner is always the token's `sub`; no field names a user. */
@RestController
@Tag(name = "Applications", description = "The caller's tracked jobs: stage, notes and a frozen copy of the job description")
internal class ApplicationController(
    private val applications: ApplicationService,
    private val currentUser: CurrentUser,
) {

    @PostMapping("/api/applications")
    @Operation(
        summary = "Track a posting",
        description = "Body `{\"postingId\": \"<id>\"}`. Creates the caller's application with the stage INTERESTED and a frozen copy of the posting. Tracking the same posting again answers the existing application with 200.",
        responses = [
            ApiResponse(responseCode = "200", description = "The posting was already tracked; the existing application"),
            ApiResponse(responseCode = "201", description = "The new application"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such posting"),
            ApiResponse(responseCode = "409", description = "The caller already has 500 applications (`application_limit`)"),
        ],
    )
    fun track(@RequestBody request: TrackRequest): ResponseEntity<Any> =
        try {
            val result = applications.track(currentUser.id(), request.postingId)
            when {
                result == null -> ResponseEntity.notFound().build()
                result.created -> ResponseEntity.status(HttpStatus.CREATED).body(result.application)
                else -> ResponseEntity.ok(result.application)
            }
        } catch (_: ApplicationLimitException) {
            codeError(HttpStatus.CONFLICT, "application_limit")
        }

    @GetMapping("/api/applications")
    @Operation(
        summary = "List the caller's applications",
        description = "Newest stage change first, without descriptions or notes. The optional `stage` filter is one of INTERESTED, APPLYING, APPLIED, REJECTED.",
        responses = [
            ApiResponse(responseCode = "200", description = "The applications"),
            ApiResponse(responseCode = "400", description = "Unknown stage (`stage_invalid`)"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun list(@RequestParam(name = "stage", required = false) stage: String?): ResponseEntity<Any> {
        val filter = if (stage == null) null else stageOf(stage) ?: return codeError(HttpStatus.BAD_REQUEST, "stage_invalid")
        return ResponseEntity.ok(applications.list(currentUser.id(), filter))
    }

    // The literal `by-posting` path is matched before `/{id}`.
    @GetMapping("/api/applications/by-posting")
    @Operation(
        summary = "Find the caller's applications for a page of postings",
        description = "Query `ids`: up to 100 posting ids, comma separated. Answers an object keyed by posting id with `applicationId` and `stage`; postings the caller does not track are absent.",
        responses = [
            ApiResponse(responseCode = "200", description = "The tracked postings among the ids"),
            ApiResponse(responseCode = "400", description = "Not a list of up to 100 ids (`ids_invalid`)"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun byPosting(@RequestParam(name = "ids", required = false) ids: String?): ResponseEntity<Any> {
        val parts = ids?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (parts.size > ApplicationService.MAX_LOOKUP) return codeError(HttpStatus.BAD_REQUEST, "ids_invalid")
        val parsed = parts.map { runCatching { UUID.fromString(it) }.getOrNull() ?: return codeError(HttpStatus.BAD_REQUEST, "ids_invalid") }
        val found: Map<UUID, PostingTracking> = applications.byPosting(currentUser.id(), parsed)
        return ResponseEntity.ok(found)
    }

    @GetMapping("/api/applications/{id}")
    @Operation(
        summary = "Read one of the caller's applications",
        description = "With the frozen description, the notes and whether the live posting still exists or changed.",
        responses = [
            ApiResponse(responseCode = "200", description = "The application"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
        ],
    )
    fun get(@PathVariable id: UUID): ResponseEntity<Any> =
        applications.get(currentUser.id(), id)?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()

    @PatchMapping("/api/applications/{id}")
    @Operation(
        summary = "Change the stage, the notes or the applied date",
        description = "Body fields `stage`, `notes` (at most 5 000 characters) and `appliedOn` (`YYYY-MM-DD`) are all optional. Entering APPLIED without a date records today.",
        responses = [
            ApiResponse(responseCode = "200", description = "The changed application"),
            ApiResponse(responseCode = "400", description = "Unknown stage, over-long notes or a bad date (`stage_invalid`, `notes_invalid`, `applied_on_invalid`)"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
        ],
    )
    fun patch(@PathVariable id: UUID, @RequestBody request: PatchRequest): ResponseEntity<Any> {
        val stage = if (request.stage == null) null else stageOf(request.stage) ?: return codeError(HttpStatus.BAD_REQUEST, "stage_invalid")
        val appliedOn = if (request.appliedOn == null) {
            null
        } else {
            try {
                LocalDate.parse(request.appliedOn)
            } catch (_: DateTimeParseException) {
                return codeError(HttpStatus.BAD_REQUEST, "applied_on_invalid")
            }
        }
        return try {
            applications.update(currentUser.id(), id, ApplicationPatch(stage, request.notes, appliedOn))
                ?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()
        } catch (e: InvalidApplicationPatchException) {
            codeError(HttpStatus.BAD_REQUEST, e.code)
        }
    }

    @GetMapping("/api/applications/{id}/rav")
    @Operation(
        summary = "The fields for the Swiss RAV job-search proof",
        description = "Only for an application in stage APPLIED. Answers the position (job title), the original posting link and the " +
            "fields the application does not store (company address, contact person, phone, e-mail, postal code): those are JSON null.",
        responses = [
            ApiResponse(responseCode = "200", description = "The proof fields"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
            ApiResponse(responseCode = "409", description = "The application is not in stage APPLIED (`not_applied`)"),
        ],
    )
    fun rav(@PathVariable id: UUID): ResponseEntity<Any> =
        try {
            applications.ravProof(currentUser.id(), id)?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()
        } catch (_: ApplicationNotAppliedException) {
            codeError(HttpStatus.CONFLICT, "not_applied")
        }

    @DeleteMapping("/api/applications/{id}")
    @Operation(
        summary = "Remove one of the caller's applications",
        description = "Deletes the application and its letters.",
        responses = [
            ApiResponse(responseCode = "204", description = "Removed"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
        ],
    )
    fun remove(@PathVariable id: UUID): ResponseEntity<Void> =
        if (applications.remove(currentUser.id(), id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private fun stageOf(value: String): ApplicationStage? = ApplicationStage.entries.firstOrNull { it.name == value }
}
