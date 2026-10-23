package net.s4v3.backend.cv.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.cv.CvInfo
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

/** Fixed texts: an error body never repeats the file name or any part of the file. */
internal object CvMessages {
    const val WRONG_TYPE = "The file must be a PDF, a Markdown file or a plain text file."
    const val TOO_LARGE = "The file is larger than 5 MB."
}

internal fun cvError(status: HttpStatus, message: String): ResponseEntity<Map<String, String>> =
    ResponseEntity.status(status).body(mapOf("error" to "invalid_cv", "message" to message))

/** The caller's own CV. The user is always the token's `sub`; no parameter names a user. */
@RestController
@Tag(name = "CV", description = "The caller's single stored CV (PDF, Markdown or plain text, up to 5 MB)")
internal class CvController(
    private val store: JdbcCvStore,
    private val currentUser: CurrentUser,
) {

    @PostMapping("/api/cv", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload or replace the caller's CV",
        description = "Multipart field `file`. The type is checked from the content: PDF, Markdown (.md) or plain text (.txt), at most 5 MB.",
        responses = [
            ApiResponse(responseCode = "200", description = "The stored file's details"),
            ApiResponse(responseCode = "400", description = "Not a PDF, Markdown or plain text file"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "413", description = "The file is larger than 5 MB"),
        ],
    )
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<*> {
        val accepted = try {
            if (file.size > CvContent.MAX_BYTES) throw CvRejected(CvRejection.TOO_LARGE)
            CvContent.accept(file.originalFilename, file.bytes)
        } catch (e: CvRejected) {
            return when (e.reason) {
                CvRejection.TOO_LARGE -> cvError(HttpStatus.PAYLOAD_TOO_LARGE, CvMessages.TOO_LARGE)
                CvRejection.WRONG_TYPE -> cvError(HttpStatus.BAD_REQUEST, CvMessages.WRONG_TYPE)
            }
        }
        return ResponseEntity.ok(store.save(currentUser.id(), accepted))
    }

    @GetMapping("/api/cv")
    @Operation(
        summary = "Read the details of the caller's CV",
        description = "File name, content type, size and upload time; never the content.",
        responses = [
            ApiResponse(responseCode = "200", description = "The stored file's details"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "The caller has no CV"),
        ],
    )
    fun info(): ResponseEntity<CvInfo> =
        store.info(currentUser.id())?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/api/cv/file")
    @Operation(
        summary = "Download the caller's CV",
        description = "The stored bytes as an attachment.",
        responses = [
            ApiResponse(responseCode = "200", description = "The file"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "The caller has no CV"),
        ],
    )
    fun download(): ResponseEntity<ByteArray> {
        val stored = store.file(currentUser.id()) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(stored.info.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(stored.info.fileName, StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .body(stored.content)
    }

    @DeleteMapping("/api/cv")
    @Operation(
        summary = "Delete the caller's CV",
        description = "Removes the stored file completely. Answers 204 also when there is none.",
        responses = [
            ApiResponse(responseCode = "204", description = "Deleted, or nothing to delete"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun delete(): ResponseEntity<Void> {
        store.delete(currentUser.id())
        return ResponseEntity.noContent().build()
    }
}

/** The multipart limit is enforced before the controller runs, so its error is turned into the same fixed answer here. */
@RestControllerAdvice
internal class CvUploadErrors {
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(): ResponseEntity<Map<String, String>> = cvError(HttpStatus.PAYLOAD_TOO_LARGE, CvMessages.TOO_LARGE)
}
