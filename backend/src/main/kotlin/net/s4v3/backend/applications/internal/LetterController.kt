package net.s4v3.backend.applications.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.applications.GenerateLetterCommand
import net.s4v3.backend.applications.LetterComments
import net.s4v3.backend.applications.LetterLimitException
import net.s4v3.backend.applications.LetterRateLimitedException
import net.s4v3.backend.applications.LetterSelection
import net.s4v3.backend.applications.LetterSelectionInvalidException
import net.s4v3.backend.applications.LetterService
import net.s4v3.backend.applications.LetterTextInvalidException
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The body of `POST /api/applications/{id}/letters`. Indexes refer to the saved profile's lists. */
internal data class GenerateLetterRequest(
    val language: String? = null,
    val experience: List<Int>? = null,
    val projects: List<Int>? = null,
    val skills: List<Int>? = null,
    val instructions: String? = null,
    val basedOnVersion: Int? = null,
    val comments: LetterComments? = null,
)

/** The body of `POST /api/applications/{id}/letters/edited`. */
internal data class EditedLetterRequest(val text: String? = null)

/** Motivation letters of the caller's applications. The owner is always the token's `sub`. */
@RestController
@Tag(name = "Letters", description = "Motivation letters for the caller's tracked jobs: versions, review comments, regeneration, download")
internal class LetterController(
    private val letters: LetterService,
    private val currentUser: CurrentUser,
) {

    @PostMapping("/api/applications/{id}/letters")
    @Operation(
        summary = "Generate or regenerate a motivation letter",
        description = "Writes a letter with the language model from the application's frozen description and only the chosen parts " +
            "of the saved profile (`experience`, `projects`, `skills` are indexes into the saved lists; the full name is the only " +
            "contact data sent). `language` is EN or DE (default: the posting's). With `basedOnVersion` the new version is " +
            "written from that version's text and the `comments` (`general` and `paragraphs` of `{n, text}`); the old version " +
            "stays unchanged. Counts against `jobhunter.letters.per-hour`. The answer is the new version.",
        responses = [
            ApiResponse(responseCode = "201", description = "The new version"),
            ApiResponse(responseCode = "400", description = "Unknown index, too many parts, bad language or over-long text (`selection_invalid`)"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application or `basedOnVersion` of the caller's"),
            ApiResponse(responseCode = "409", description = "The application already has 20 versions (`letter_limit`)"),
            ApiResponse(responseCode = "429", description = "Hourly limit reached (`rate_limited`)"),
            ApiResponse(responseCode = "502", description = "The model gave no usable letter (`letter_failed`)"),
            ApiResponse(responseCode = "503", description = "No model key on the server (`letter_not_configured`)"),
        ],
    )
    fun generate(@PathVariable id: UUID, @RequestBody(required = false) request: GenerateLetterRequest?): ResponseEntity<Any> {
        val r = request ?: GenerateLetterRequest()
        val command = GenerateLetterCommand(
            language = r.language,
            selection = LetterSelection(r.experience.orEmpty(), r.projects.orEmpty(), r.skills.orEmpty()),
            instructions = r.instructions,
            basedOnVersion = r.basedOnVersion,
            comments = r.comments,
        )
        return try {
            letters.generate(currentUser.id(), id, command)
                ?.let { ResponseEntity.status(HttpStatus.CREATED).body<Any>(it) } ?: ResponseEntity.notFound().build()
        } catch (_: LetterSelectionInvalidException) {
            codeError(HttpStatus.BAD_REQUEST, "selection_invalid")
        } catch (_: LetterLimitException) {
            codeError(HttpStatus.CONFLICT, "letter_limit")
        } catch (_: LetterRateLimitedException) {
            codeError(HttpStatus.TOO_MANY_REQUESTS, "rate_limited")
        } catch (e: LlmFailure) {
            when (e.kind) {
                LlmFailureKind.NOT_CONFIGURED -> codeError(HttpStatus.SERVICE_UNAVAILABLE, "letter_not_configured")
                LlmFailureKind.FAILED -> codeError(HttpStatus.BAD_GATEWAY, "letter_failed")
            }
        }
    }

    @PostMapping("/api/applications/{id}/letters/edited")
    @Operation(
        summary = "Save an edited letter as a new version",
        description = "Body `{\"text\": \"...\"}`: Markdown without HTML, at most 6 000 characters. No model call, no hourly limit.",
        responses = [
            ApiResponse(responseCode = "201", description = "The new version, marked EDITED"),
            ApiResponse(responseCode = "400", description = "Empty, too long or containing HTML (`letter_text_invalid`)"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
            ApiResponse(responseCode = "409", description = "The application already has 20 versions (`letter_limit`)"),
        ],
    )
    fun edited(@PathVariable id: UUID, @RequestBody(required = false) request: EditedLetterRequest?): ResponseEntity<Any> {
        val text = request?.text ?: return codeError(HttpStatus.BAD_REQUEST, "letter_text_invalid")
        return try {
            letters.saveEdited(currentUser.id(), id, text)
                ?.let { ResponseEntity.status(HttpStatus.CREATED).body<Any>(it) } ?: ResponseEntity.notFound().build()
        } catch (_: LetterTextInvalidException) {
            codeError(HttpStatus.BAD_REQUEST, "letter_text_invalid")
        } catch (_: LetterLimitException) {
            codeError(HttpStatus.CONFLICT, "letter_limit")
        }
    }

    @GetMapping("/api/applications/{id}/letters")
    @Operation(
        summary = "List the versions of an application's letter",
        description = "A bare array without texts, oldest first: version, how it was made, language, time and the version it was based on.",
        responses = [
            ApiResponse(responseCode = "200", description = "The versions"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application of the caller's"),
        ],
    )
    fun list(@PathVariable id: UUID): ResponseEntity<Any> =
        letters.list(currentUser.id(), id)?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/api/applications/{id}/letters/{version}")
    @Operation(
        summary = "Read one version of a letter",
        description = "With the text, the selection and instructions it was made from and the comments it used.",
        responses = [
            ApiResponse(responseCode = "200", description = "The version"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application or version of the caller's"),
        ],
    )
    fun get(@PathVariable id: UUID, @PathVariable version: Int): ResponseEntity<Any> =
        letters.get(currentUser.id(), id, version)?.let { ResponseEntity.ok<Any>(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/api/applications/{id}/letters/{version}/download")
    @Operation(
        summary = "Download one version of a letter",
        description = "The Markdown text as `text/markdown; charset=utf-8`, an attachment named `motivation-<company>-<title>.md`.",
        responses = [
            ApiResponse(responseCode = "200", description = "The Markdown bytes"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application or version of the caller's"),
        ],
    )
    fun download(@PathVariable id: UUID, @PathVariable version: Int): ResponseEntity<Any> {
        val file = letters.file(currentUser.id(), id, version) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown; charset=utf-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.fileName}\"")
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .body(file.bytes)
    }

    @DeleteMapping("/api/applications/{id}/letters/{version}")
    @Operation(
        summary = "Remove one version of a letter",
        description = "Other versions keep their numbers.",
        responses = [
            ApiResponse(responseCode = "204", description = "Removed"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such application or version of the caller's"),
        ],
    )
    fun delete(@PathVariable id: UUID, @PathVariable version: Int): ResponseEntity<Void> =
        if (letters.delete(currentUser.id(), id, version)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
}
