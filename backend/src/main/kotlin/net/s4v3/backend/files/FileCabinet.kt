package net.s4v3.backend.files

import java.nio.file.Path
import java.util.UUID

/** The user's file cabinet as other modules see it. Every call is for one user, named by the `sub` of their token. */
interface FileCabinet {

    /** The user's master CV, or null when no file is set as master. */
    fun masterCv(sub: String): CabinetFile?

    /** The user's own file [id] when it is a JPEG of at most 6 MB; null for anything else, without saying which. */
    fun image(sub: String, id: UUID): CabinetFile?
}

/** A file handed to another module: its details and the path of its bytes, which the caller reads and never writes. */
data class CabinetFile(
    val id: UUID,
    val name: String,
    val contentType: String,
    val sizeBytes: Long,
    val path: Path,
)
