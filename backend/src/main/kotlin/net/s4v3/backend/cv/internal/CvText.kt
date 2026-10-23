package net.s4v3.backend.cv.internal

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.IOException

/** Why a stored CV gave no text. */
internal class NoCvText : RuntimeException("no text")

/** Reads the plain text of a CV from its bytes: PDFBox for PDFs (no OCR), UTF-8 for Markdown and plain text. */
internal object CvText {

    fun of(bytes: ByteArray, contentType: String): String {
        val raw = when (contentType) {
            "application/pdf" -> pdfText(bytes)
            else -> String(bytes, Charsets.UTF_8)
        }
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) throw NoCvText()
        return text
    }

    private fun pdfText(bytes: ByteArray): String =
        try {
            Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
        } catch (_: IOException) {
            // Damaged or password-protected: nothing readable, and nothing from the file is echoed.
            throw NoCvText()
        }
}
