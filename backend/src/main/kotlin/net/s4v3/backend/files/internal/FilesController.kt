package net.s4v3.backend.files.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.accounts.NoCurrentUserException
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Fixed texts: an error body never repeats the file name or any part of the file. */
internal object FilesMessages {
    const val WRONG_TYPE = "The file must be a PDF, a Markdown file or a plain text file."
    const val TOO_LARGE_CV = "The file is larger than 5 MB."
    const val TOO_LARGE_OTHER = "The file is larger than 10 GB."
    const val NO_ROOM = "The server has no room for this file right now."
    const val BAD_KIND = "The kind must be CV or OTHER."
    const val NOT_A_CV = "Only a file of kind CV can be the master CV."
}

internal fun fileError(status: HttpStatus, message: String): ResponseEntity<Map<String, String>> =
    ResponseEntity.status(status).body(mapOf("error" to "invalid_file", "message" to message))

/** The details of one stored file. Never the content and never the storage key. */
internal data class FileDetails(
    val id: UUID,
    val name: String,
    val kind: FileKind,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: Instant,
    val master: Boolean,
)

internal fun FileRecord.details() = FileDetails(id, name, kind, contentType, sizeBytes, uploadedAt, master)

/** A file over the limit of its kind; the message is one of the fixed texts. */
internal class TooLargeForKind(message: String) : RuntimeException(message)

/** The caller's file cabinet. The user is always the token's `sub`; no parameter names a user. */
@RestController
@Tag(name = "Files", description = "The caller's file cabinet: any number of files, and one master CV")
internal class FilesController(
    private val files: JdbcFileRepository,
    private val storage: FileStorage,
    private val settings: FilesSettings,
    private val currentUser: CurrentUser,
    private val tickets: DownloadTickets,
) {

    private val log = LoggerFactory.getLogger(FilesController::class.java)

    @PostMapping("/api/files", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload a file",
        description = "Multipart field `file`, optional field `kind` (`CV` or `OTHER`, default `OTHER`). A CV is a PDF, Markdown or plain text file of at most 5 MB; any other file may be up to 10 GB and is streamed to disk.",
        responses = [
            ApiResponse(responseCode = "200", description = "The stored file's details"),
            ApiResponse(responseCode = "400", description = "Unknown kind, or a CV that is not a PDF, Markdown or plain text file"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "413", description = "The file is larger than its kind allows"),
            ApiResponse(responseCode = "507", description = "The server has no room for the file"),
        ],
    )
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(name = "kind", required = false) kindField: String?,
    ): ResponseEntity<*> {
        val kind = if (kindField == null) FileKind.OTHER else kindOf(kindField) ?: return badKind()
        return try {
            val details = if (kind == FileKind.CV) uploadCv(file) else uploadOther(file)
            ResponseEntity.ok(details)
        } catch (e: CvRejected) {
            when (e.reason) {
                CvRejection.TOO_LARGE -> fileError(HttpStatus.CONTENT_TOO_LARGE, FilesMessages.TOO_LARGE_CV)
                CvRejection.WRONG_TYPE -> fileError(HttpStatus.BAD_REQUEST, FilesMessages.WRONG_TYPE)
            }
        } catch (e: TooLargeForKind) {
            fileError(HttpStatus.CONTENT_TOO_LARGE, e.message.orEmpty())
        } catch (e: IOException) {
            if (!noSpace(e)) throw e
            fileError(HttpStatus.INSUFFICIENT_STORAGE, FilesMessages.NO_ROOM)
        }
    }

    @GetMapping("/api/files")
    @Operation(
        summary = "List the caller's files",
        description = "Newest first. The optional `kind` filter answers only the files of that kind.",
        responses = [
            ApiResponse(responseCode = "200", description = "The files' details"),
            ApiResponse(responseCode = "400", description = "Unknown kind"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun list(@RequestParam(name = "kind", required = false) kindField: String?): ResponseEntity<*> {
        val kind = if (kindField == null) null else kindOf(kindField) ?: return badKind()
        return ResponseEntity.ok(files.list(currentUser.id(), kind).map { it.details() })
    }

    // The literal `/master` paths are matched before `/{id}`, so `master` is never read as an id.
    @GetMapping("/api/files/{id}")
    @Operation(
        summary = "Read the details of one of the caller's files",
        responses = [
            ApiResponse(responseCode = "200", description = "The file's details"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such file of the caller's"),
        ],
    )
    fun details(@PathVariable id: UUID): ResponseEntity<FileDetails> =
        files.find(currentUser.id(), id)?.let { ResponseEntity.ok(it.details()) } ?: ResponseEntity.notFound().build()

    @GetMapping("/api/files/master")
    @Operation(
        summary = "Read the caller's master CV",
        responses = [
            ApiResponse(responseCode = "200", description = "The master CV's details"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "The caller has no master CV"),
        ],
    )
    fun master(): ResponseEntity<FileDetails> =
        files.master(currentUser.id())?.let { ResponseEntity.ok(it.details()) } ?: ResponseEntity.notFound().build()

    @PutMapping("/api/files/master", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Choose the master CV",
        description = "Body `{\"fileId\": \"<id>\"}`. Replaces the earlier master. Only a file of kind CV can be the master.",
        responses = [
            ApiResponse(responseCode = "200", description = "The file's details, with master true"),
            ApiResponse(responseCode = "400", description = "The file is not of kind CV"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such file of the caller's"),
        ],
    )
    fun choose(@RequestBody request: MasterRequest): ResponseEntity<*> {
        val sub = currentUser.id()
        val file = files.find(sub, request.fileId) ?: return ResponseEntity.notFound().build<Any>()
        if (file.kind != FileKind.CV) return fileError(HttpStatus.BAD_REQUEST, FilesMessages.NOT_A_CV)
        if (!files.setMaster(sub, request.fileId)) return ResponseEntity.notFound().build<Any>()
        return ResponseEntity.ok(file.copy(master = true).details())
    }

    @DeleteMapping("/api/files/master")
    @Operation(
        summary = "Clear the master CV",
        description = "The files stay. Answers 204 also when there is no master CV.",
        responses = [
            ApiResponse(responseCode = "204", description = "Cleared, or there was none"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun clearMaster(): ResponseEntity<Void> {
        files.clearMaster(currentUser.id())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/api/files/{id}/download-ticket")
    @Operation(
        summary = "Get a download ticket for one of the caller's files",
        description = "Answers a path with a ticket that is valid for 60 seconds, for this file only, and for one use.",
        responses = [
            ApiResponse(responseCode = "200", description = "The path to request, with the ticket"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No such file of the caller's"),
        ],
    )
    fun downloadTicket(@PathVariable id: UUID): ResponseEntity<DownloadPath> {
        val sub = currentUser.id()
        files.find(sub, id) ?: return ResponseEntity.notFound().build()
        val ticket = tickets.issue(sub, id)
        return ResponseEntity.ok(DownloadPath("/api/files/$id/content?ticket=$ticket"))
    }

    /**
     * Allowed without a token only with a download ticket for this very file (the security chain lets the request through,
     * and this method decides). The id is read as text so that a request without any credential always answers 401.
     */
    @GetMapping("/api/files/{id}/content")
    @Operation(
        summary = "Download one of the caller's files",
        description = "The stored bytes as an attachment, streamed from disk. Supports Range requests. Either the access token or a download ticket from `download-ticket` (single use, 60 seconds, this file only).",
        responses = [
            ApiResponse(responseCode = "200", description = "The file"),
            ApiResponse(responseCode = "206", description = "The requested range of the file"),
            ApiResponse(responseCode = "401", description = "No valid access token and no valid ticket for this file"),
            ApiResponse(responseCode = "404", description = "No such file of the caller's"),
        ],
    )
    fun content(
        @PathVariable id: String,
        @RequestParam(name = "ticket", required = false) ticket: String?,
    ): ResponseEntity<Resource> {
        val fileId = runCatching { UUID.fromString(id) }.getOrNull()
        val sub = authenticatedSub() ?: redeemed(ticket, fileId) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val record = fileId?.let { files.find(sub, it) } ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(record.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(record.name, StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .body(FileSystemResource(storage.open(record.storageKey)))
    }

    /** Deletes the row first, then the bytes. Answers 204 also for an unknown id or another user's id. */
    @DeleteMapping("/api/files/{id}")
    @Operation(
        summary = "Delete one of the caller's files",
        description = "Removes the row, then the bytes. A master CV that is deleted is no longer the master. Answers 204 also when the id is unknown.",
        responses = [
            ApiResponse(responseCode = "204", description = "Deleted, or nothing to delete"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        files.delete(currentUser.id(), id)?.let { storage.delete(it) }
        return ResponseEntity.noContent().build()
    }

    /** A CV is read into memory (at most the CV limit, 5 MB) so that its type can be checked from its content. */
    private fun uploadCv(file: MultipartFile): FileDetails {
        if (file.size > settings.maxCvBytes) throw TooLargeForKind(FilesMessages.TOO_LARGE_CV)
        val accepted = CvContent.accept(file.originalFilename, file.bytes)
        return store(FileKind.CV, accepted.displayName, accepted.contentType) { Files.write(it, accepted.bytes) }
    }

    /** Any other file is never read into memory: Tomcat has spooled it to disk and it is moved into place. */
    private fun uploadOther(file: MultipartFile): FileDetails {
        if (file.size > settings.maxOtherBytes) throw TooLargeForKind(FilesMessages.TOO_LARGE_OTHER)
        val contentType = declaredType(file.contentType)
        // transferTo(File) hands the spooled part to Tomcat, which renames it; transferTo(Path) would copy all of the bytes again.
        return store(FileKind.OTHER, CvContent.displayNameOnly(file.originalFilename), contentType) { file.transferTo(it.toFile()) }
    }

    /**
     * Writes the bytes to a temporary file, moves it into place, then inserts the row. If the insert fails the stored file
     * is deleted again, so a failed upload leaves neither a row nor a file.
     */
    private fun store(kind: FileKind, name: String, contentType: String, write: (Path) -> Unit): FileDetails {
        val sub = currentUser.id()
        val id = UUID.randomUUID()
        val temp = storage.newTempPath()
        val stored = try {
            write(temp)
            storage.moveIn(temp, id)
        } catch (e: Exception) {
            Files.deleteIfExists(temp)
            throw e
        }
        val record = FileRecord(
            id = id,
            ownerSub = sub,
            name = name,
            kind = kind,
            contentType = contentType,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256,
            storageKey = stored.key,
            master = false,
            uploadedAt = Instant.now(),
        )
        try {
            files.insert(record)
        } catch (e: RuntimeException) {
            storage.delete(stored.key)
            throw e
        }
        // Outcome only: never the name, the content or the owner.
        log.info("File stored: kind={}, {} bytes", kind, stored.sizeBytes)
        return record.details()
    }

    /** The declared type when it is a valid media type, otherwise `application/octet-stream`. */
    private fun declaredType(declared: String?): String =
        declared?.takeIf { it.isNotBlank() && runCatching { MediaType.parseMediaType(it) }.isSuccess }
            ?: MediaType.APPLICATION_OCTET_STREAM_VALUE

    private fun noSpace(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is IOException && it.message?.contains("No space left", ignoreCase = true) == true }

    /** The caller's `sub` from a bearer token, or null when the request has none. */
    private fun authenticatedSub(): String? =
        try {
            currentUser.id()
        } catch (_: NoCurrentUserException) {
            null
        }

    private fun redeemed(ticket: String?, fileId: UUID?): String? =
        if (ticket == null || fileId == null) null else tickets.redeem(ticket, fileId)

    private fun kindOf(field: String): FileKind? = FileKind.entries.firstOrNull { it.name == field }

    private fun badKind(): ResponseEntity<Map<String, String>> = fileError(HttpStatus.BAD_REQUEST, FilesMessages.BAD_KIND)
}

/** The body of `PUT /api/files/master`. */
internal data class MasterRequest(val fileId: UUID)

/** The answer of `POST /api/files/{id}/download-ticket`: the path to request, with its ticket. */
internal data class DownloadPath(val path: String)
