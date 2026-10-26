package net.s4v3.backend.applications.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.applications.SkillAnalysisService
import net.s4v3.backend.applications.SkillNoteInput
import net.s4v3.backend.applications.SkillNotesInvalidException
import net.s4v3.backend.applications.SkillsRateLimitedException
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** One note in the body of `PUT /api/applications/{id}/skills/notes`. */
internal data class SkillNoteBody(val skill: String? = null, val text: String? = null)

/** The body of `PUT /api/applications/{id}/skills/notes`. */
internal data class SkillNotesRequest(val notes: List<SkillNoteBody>? = null)

/** Skill analysis and gap notes of the caller's applications. The owner is always the token's `sub`. */
@RestController
@Tag(name = "Letters", description = "Motivation letters for the caller's tracked jobs: versions, review comments, regeneration, download")
internal class SkillsController(
    private val skills: SkillAnalysisService,
    private val currentUser: CurrentUser,
) {

    @GetMapping("/api/applications/{id}/skills")
    @Operation(
        summary = "Read the stored skill analysis of an application",
        description = "Answers `{\"analysed\":false}` when there is none, otherwise `{analysed:true, createdAt, include, evidenced, gaps, other}`: " +
            "`include` are skills the job asks for and the saved skills list has (with `importance` REQUIRED or NICE_TO_HAVE, `profileIndex` " +
            "and `profileName`), `evidenced` those not in the skills list that a position or project supports (with `kind` EXPERIENCE or " +
            "PROJECT, `index` in the saved list, `source` \"<title> at <employer>\" or the project name, and the `quote` from the profile; " +
            "an analysis stored before evidence existed has none), `gaps` those nothing supports (with `related` saved skills and the candidate's `note` or null), `other` the saved " +
            "skills the job does not ask for. The lists are derived from the current saved profile on every read (a claim that no longer holds " +
            "becomes a gap); no model call, no limit.",
        responses = [
            ApiResponse(responseCode = "200", description = "The analysis, or `{\"analysed\":false}`"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
        ],
    )
    fun get(@PathVariable id: UUID): ResponseEntity<Any> =
        skills.get(currentUser.id(), id)?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()

    @PostMapping("/api/applications/{id}/skills/analyse")
    @Operation(
        summary = "Analyse the skills a job asks for",
        description = "One model call on the application's frozen description, the names of the saved skills and the text of positions " +
            "(title, employer, achievements, technologies) and projects (name, description, summary); never name, contact data, salary, " +
            "preferences or work authorisation. Listed and evidenced are decided by the server, which checks every quote against the profile. Replaces a stored analysis and keeps the notes whose skill is still a gap. " +
            "Counts against `jobhunter.letters.skills-per-hour`, separate from the letter limit. The answer is the same as the read.",
        responses = [
            ApiResponse(responseCode = "200", description = "The new analysis"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
            ApiResponse(responseCode = "429", description = "Hourly limit reached (`rate_limited`)"),
            ApiResponse(responseCode = "502", description = "The model gave no usable answer (`skills_failed`)"),
            ApiResponse(responseCode = "503", description = "No model key on the server (`skills_not_configured`)"),
        ],
    )
    fun analyse(@PathVariable id: UUID): ResponseEntity<Any> =
        try {
            skills.analyse(currentUser.id(), id)?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()
        } catch (_: SkillsRateLimitedException) {
            codeError(HttpStatus.TOO_MANY_REQUESTS, "rate_limited")
        } catch (e: LlmFailure) {
            when (e.kind) {
                LlmFailureKind.NOT_CONFIGURED -> codeError(HttpStatus.SERVICE_UNAVAILABLE, "skills_not_configured")
                LlmFailureKind.FAILED -> codeError(HttpStatus.BAD_GATEWAY, "skills_failed")
            }
        }

    @PutMapping("/api/applications/{id}/skills/notes")
    @Operation(
        summary = "Save the notes on the gap skills of an application",
        description = "Body `{\"notes\":[{\"skill\":\"...\",\"text\":\"...\"}]}`: at most 20 notes of at most 500 characters, plain text " +
            "without HTML, each for a skill that is a current gap. Replaces all notes of the application; the profile is not changed. " +
            "No model call, no limit. The answer is the same as the read.",
        responses = [
            ApiResponse(responseCode = "200", description = "The analysis with the notes"),
            ApiResponse(responseCode = "400", description = "Too many, too long, blank, containing HTML, or not for a current gap (`notes_invalid`)"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's, or it has no analysis"),
        ],
    )
    fun saveNotes(@PathVariable id: UUID, @RequestBody(required = false) request: SkillNotesRequest?): ResponseEntity<Any> {
        val notes = request?.notes ?: return codeError(HttpStatus.BAD_REQUEST, "notes_invalid")
        val input = notes.map { SkillNoteInput(it.skill ?: return codeError(HttpStatus.BAD_REQUEST, "notes_invalid"), it.text ?: return codeError(HttpStatus.BAD_REQUEST, "notes_invalid")) }
        return try {
            skills.saveNotes(currentUser.id(), id, input)?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()
        } catch (_: SkillNotesInvalidException) {
            codeError(HttpStatus.BAD_REQUEST, "notes_invalid")
        }
    }
}
