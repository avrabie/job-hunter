package net.s4v3.backend.cv.internal

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal enum class CvRejection { WRONG_TYPE, TOO_LARGE }

internal class CvRejected(val reason: CvRejection) : RuntimeException(reason.name)

/** A file that passed the checks: the type decided from its content, and the name that is safe to show. */
internal data class AcceptedCv(val displayName: String, val contentType: String, val bytes: ByteArray)

/**
 * The only accepted CV files: PDF, Markdown and plain text, up to 5 MB. The type comes from the content, not from the
 * declared content type: a PDF starts with `%PDF-`, a text file is valid UTF-8 without NUL bytes. The file extension only
 * chooses between Markdown and plain text, and has to agree with the content.
 */
internal object CvContent {

    const val MAX_BYTES = 5 * 1024 * 1024

    private val PDF_MAGIC = "%PDF-".toByteArray(StandardCharsets.US_ASCII)

    fun accept(originalName: String?, bytes: ByteArray): AcceptedCv {
        if (bytes.size > MAX_BYTES) throw CvRejected(CvRejection.TOO_LARGE)
        if (bytes.isEmpty()) throw CvRejected(CvRejection.WRONG_TYPE)
        val name = displayName(originalName)
        val extension = name.substringAfterLast('.', "").lowercase()
        val contentType = when (extension) {
            "pdf" -> if (isPdf(bytes)) "application/pdf" else null
            "md" -> if (isText(bytes)) "text/markdown" else null
            "txt" -> if (isText(bytes)) "text/plain" else null
            else -> null
        } ?: throw CvRejected(CvRejection.WRONG_TYPE)
        return AcceptedCv(name, contentType, bytes)
    }

    /**
     * A name for display only: the part after the last path separator, control characters removed, anything other than
     * letters, digits, space, dot, dash, underscore and brackets replaced by an underscore, at most 100 characters.
     */
    fun displayName(original: String?): String {
        val base = original.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.filter { !it.isISOControl() }
            .map { if (it.isLetterOrDigit() || it in " ._-()") it else '_' }
            .joinToString("")
            .trim()
        return cleaned.take(MAX_NAME).ifBlank { "cv" }
    }

    private fun isPdf(bytes: ByteArray) = bytes.size >= PDF_MAGIC.size && PDF_MAGIC.indices.all { bytes[it] == PDF_MAGIC[it] }

    private fun isText(bytes: ByteArray): Boolean {
        if (bytes.any { it == 0.toByte() }) return false
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }

    private const val MAX_NAME = 100
}
