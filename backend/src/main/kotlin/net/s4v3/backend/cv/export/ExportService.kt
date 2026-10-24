package net.s4v3.backend.cv.export

/** A rendered CV: the bytes, the content type and the ASCII file name for the download. */
class ExportedCv(val bytes: ByteArray, val contentType: String, val fileName: String)

/** The caller's saved profile has no full name, or was never saved. */
class NameRequiredException : RuntimeException("A full name is needed to export a CV")

/** Two PDF renders are already running and none finished in time. */
class ExportBusyException : RuntimeException("Too many PDF renders at the moment")

/** The renderer failed. The message never carries profile text. */
class ExportFailedException : RuntimeException("The CV could not be rendered")

/** Renders the caller's saved profile (read by the token's `sub`, never from a request) as a CV. */
interface ExportService {

    /**
     * @throws NameRequiredException if the profile has no full name or was never saved
     * @throws ExportBusyException if a PDF render cannot start within the wait time
     * @throws ExportFailedException if rendering fails
     */
    fun export(sub: String, options: ExportOptions): ExportedCv
}
