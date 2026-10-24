package net.s4v3.backend.cv.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.files.CabinetFile
import net.s4v3.backend.files.FileCabinet
import net.s4v3.backend.llm.CvReader
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.llm.ProfileSection
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files

/** The most bytes of a CV that are read for extraction. A master CV is always a file of kind CV, so this is never reached. */
private const val MAX_CV_BYTES = 5L * 1024 * 1024

internal fun cvError(status: HttpStatus, message: String): ResponseEntity<Map<String, String>> =
    ResponseEntity.status(status).body(mapOf("error" to "invalid_cv", "message" to message))

/** Builds a profile draft from the caller's master CV in the file cabinet. The draft is returned, never stored. */
@RestController
@Tag(name = "CV", description = "Drafts a profile from the master CV in the file cabinet (PDF, Markdown or plain text, up to 5 MB)")
internal class CvExtractController(
    private val cabinet: FileCabinet,
    private val reader: CvReader,
    private val limiter: ExtractionLimiter,
    private val settings: CvSettings,
    private val currentUser: CurrentUser,
) {

    private val log = LoggerFactory.getLogger(CvExtractController::class.java)

    @PostMapping("/api/cv/extract")
    @Operation(
        summary = "Draft a profile from the caller's master CV",
        description = "Reads the master CV with a language model and returns a profile in the shape of the profile's content. " +
            "With `section` (experience, education, publications or projects) the model is asked only for that section, every entry of " +
            "it, and the answer fills only that field; every other list is empty. " +
            "Nothing is saved: the client shows the draft, and the user saves it with PUT /api/profile. " +
            "At most a few calls per hour, and a section request counts the same as a whole-profile request.",
        responses = [
            ApiResponse(responseCode = "200", description = "The profile draft"),
            ApiResponse(responseCode = "400", description = "The section is not experience, education, publications or projects"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "409", description = "The caller has not chosen a master CV"),
            ApiResponse(responseCode = "413", description = "The CV text is longer than the configured limit"),
            ApiResponse(responseCode = "422", description = "No text could be read from the CV (for example a scanned PDF)"),
            ApiResponse(responseCode = "429", description = "Too many extractions in the last hour"),
            ApiResponse(responseCode = "502", description = "The language model failed"),
            ApiResponse(responseCode = "503", description = "The language model is not set up"),
        ],
    )
    fun extract(
        @Parameter(description = "Only this section: experience, education, publications or projects (any letter case). Omit for the whole profile.")
        @RequestParam(name = "section", required = false) section: String?,
    ): ResponseEntity<*> {
        val wanted = if (section == null) null else ProfileSection.entries.firstOrNull { it.name.equals(section, ignoreCase = true) }
        if (section != null && wanted == null) return error(HttpStatus.BAD_REQUEST, "That section cannot be read.")
        val sub = currentUser.id()
        val master = cabinet.masterCv(sub)
            ?: return cvError(HttpStatus.CONFLICT, "Choose a master CV in your files first.")
        val text = try {
            CvText.of(readMaster(master), master.contentType)
        } catch (_: NoCvText) {
            return error(HttpStatus.UNPROCESSABLE_CONTENT, "No text could be read from the CV. Use a text-based PDF, a Markdown file or a plain text file.")
        }
        if (text.length > settings.maxTextChars) return error(HttpStatus.CONTENT_TOO_LARGE, "The CV text is too long to read.")
        if (!limiter.tryAcquire(sub)) return error(HttpStatus.TOO_MANY_REQUESTS, "Too many extractions. Please try again later.")
        return try {
            log.info("CV extraction requested: {} characters", text.length)
            ResponseEntity.ok(if (wanted == null) reader.profileFrom(text) else reader.sectionFrom(text, wanted))
        } catch (e: LlmFailure) {
            when (e.kind) {
                LlmFailureKind.NOT_CONFIGURED -> error(HttpStatus.SERVICE_UNAVAILABLE, "Reading CVs is not set up on this server yet.")
                LlmFailureKind.FAILED -> error(HttpStatus.BAD_GATEWAY, "The CV could not be read right now. Please try again.")
            }
        }
    }

    private fun readMaster(master: CabinetFile): ByteArray {
        if (master.sizeBytes > MAX_CV_BYTES) throw NoCvText()
        return Files.readAllBytes(master.path)
    }

    private fun error(status: HttpStatus, message: String) = cvError(status, message)
}
