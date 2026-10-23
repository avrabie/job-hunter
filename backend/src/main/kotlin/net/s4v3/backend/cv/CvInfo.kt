package net.s4v3.backend.cv

import java.time.Instant

/** What is known about a user's stored CV, without its content. */
data class CvInfo(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Int,
    val uploadedAt: Instant,
)
