package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.ExportBusyException
import net.s4v3.backend.cv.export.ExportFailedException
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.ExportService
import net.s4v3.backend.cv.export.ExportedCv
import net.s4v3.backend.cv.export.NameRequiredException
import net.s4v3.backend.profile.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** At most 2 PDF renders at a time; a third waits up to 5 seconds. Only PDF rendering is guarded. */
@Component
internal class RenderGuard {
    val permits = Semaphore(MAX_PARALLEL)
    val wait: Duration = Duration.ofSeconds(5)

    private companion object {
        const val MAX_PARALLEL = 2
    }
}

/** Loads the caller's saved profile, builds the document and picks the renderer. Never logs profile content. */
@Service
internal class DefaultExportService(
    private val profiles: ProfileService,
    private val guard: RenderGuard,
) : ExportService {

    private val log = LoggerFactory.getLogger(DefaultExportService::class.java)
    private val markdown = MarkdownRenderer()
    private val html = HtmlRenderer()
    private val pdf = PdfRenderer(html)

    override fun export(sub: String, options: ExportOptions): ExportedCv {
        val profile = profiles.get(sub).profile
        if (profile.contact.fullName.isNullOrBlank()) throw NameRequiredException()
        val started = System.nanoTime()
        val document = CvBuilder.build(profile, options)
        val bytes = try {
            when (options.format) {
                CvFormat.MARKDOWN -> markdown.render(document, options.template)
                CvFormat.HTML -> html.render(document, options.template)
                CvFormat.PDF -> renderPdf { pdf.render(document, options.template) }
            }
        } catch (e: ExportBusyException) {
            throw e
        } catch (_: Exception) {
            // The cause is dropped on purpose: library messages may quote the document.
            throw ExportFailedException()
        }
        if (bytes.size > MAX_BYTES) throw ExportFailedException()
        log.info(
            "CV exported: format {}, template {}, {} bytes, {} ms",
            options.format, options.template, bytes.size, (System.nanoTime() - started) / 1_000_000,
        )
        return ExportedCv(bytes, contentType(options.format), fileName(document.name, options))
    }

    private fun renderPdf(render: () -> ByteArray): ByteArray {
        if (!guard.permits.tryAcquire(guard.wait.toMillis(), TimeUnit.MILLISECONDS)) throw ExportBusyException()
        try {
            return render()
        } finally {
            guard.permits.release()
        }
    }

    private fun contentType(format: CvFormat) = when (format) {
        CvFormat.MARKDOWN -> "text/markdown; charset=utf-8"
        CvFormat.HTML -> "text/html; charset=utf-8"
        CvFormat.PDF -> "application/pdf"
    }

    private fun fileName(name: String, options: ExportOptions): String {
        val extension = when (options.format) {
            CvFormat.MARKDOWN -> "md"
            CvFormat.HTML -> "html"
            CvFormat.PDF -> "pdf"
        }
        return "${slug(name)}-cv-${options.language.name.lowercase()}.$extension"
    }

    /** ASCII only: accents are stripped, `ß` becomes `ss`, every other run of characters becomes one dash. */
    private fun slug(name: String): String {
        val plain = Normalizer.normalize(name.replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).trim('-').ifEmpty { "cv" }
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
