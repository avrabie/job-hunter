package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.ExportBusyException
import net.s4v3.backend.cv.export.ExportFailedException
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.ExportService
import net.s4v3.backend.cv.export.ExportedCv
import net.s4v3.backend.cv.export.InvalidPhotoException
import net.s4v3.backend.cv.export.NameRequiredException
import net.s4v3.backend.cv.export.PhotoProcessor
import net.s4v3.backend.cv.internal.ExportSettings
import net.s4v3.backend.files.FileCabinet
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** At most 2 PDF renders at a time; a third waits `render-wait` (5 seconds by default). Only PDF rendering is guarded. */
@Component
internal class RenderGuard(settings: ExportSettings) {
    val permits = Semaphore(MAX_PARALLEL)
    val wait: Duration = settings.renderWait

    private companion object {
        const val MAX_PARALLEL = 2
    }
}

/** Loads the caller's saved profile, builds the document and picks the renderer. Never logs profile content. */
@Service
internal class DefaultExportService(
    private val profiles: ProfileService,
    private val guard: RenderGuard,
    private val cabinet: FileCabinet,
) : ExportService {

    private val log = LoggerFactory.getLogger(DefaultExportService::class.java)
    private val markdown = MarkdownRenderer()
    private val html = HtmlRenderer()
    private val pdf = PdfRenderer(html)
    private val photos = PhotoProcessor()

    override fun export(sub: String, options: ExportOptions): ExportedCv {
        val profile = profiles.get(sub).profile
        if (profile.contact.fullName.isNullOrBlank()) throw NameRequiredException()
        val started = System.nanoTime()
        val wantsPhoto = options.template == CvTemplate.PROFESSIONAL && options.format != CvFormat.MARKDOWN &&
            options.photoFileId != null
        val bytes = try {
            when (options.format) {
                CvFormat.MARKDOWN -> markdown.render(CvBuilder.build(profile, options), options.template)
                CvFormat.HTML ->
                    if (wantsPhoto) guarded { html.render(documentWithPhoto(sub, profile, options), options.template) }
                    else html.render(CvBuilder.build(profile, options), options.template)
                CvFormat.PDF -> guarded { pdf.render(documentWithPhoto(sub, profile, options, wantsPhoto), options.template) }
            }
        } catch (e: ExportBusyException) {
            throw e
        } catch (e: InvalidPhotoException) {
            throw e
        } catch (e: Throwable) {
            // Errors too (for example a missing native library in the image). The cause is dropped on purpose: library
            // messages may quote the document. Only the class name is logged, to find the reason.
            if (e is VirtualMachineError) throw e
            log.warn("CV export failed: {}", e::class.simpleName)
            throw ExportFailedException()
        }
        if (bytes.size > MAX_BYTES) throw ExportFailedException()
        log.info(
            "CV exported: format {}, template {}, {} bytes, {} ms",
            options.format, options.template, bytes.size, (System.nanoTime() - started) / 1_000_000,
        )
        return ExportedCv(bytes, options.format.contentType, fileName(profile.contact.fullName.orEmpty(), options))
    }

    /**
     * Builds the document; for a requested photo it reads the caller's own JPEG and re-encodes it. A missing, foreign or
     * invalid file is one [InvalidPhotoException]. Neither the file id nor the photo is ever logged.
     */
    private fun documentWithPhoto(sub: String, profile: Profile, options: ExportOptions, wantsPhoto: Boolean = true): CvDocument {
        val id = options.photoFileId
        if (!wantsPhoto || id == null) return CvBuilder.build(profile, options)
        val file = cabinet.image(sub, id) ?: throw InvalidPhotoException()
        return CvBuilder.build(profile, options, photos.process(file))
    }

    /** Runs [render] under one of the two shared permits (photo decoding and PDF rendering are the heavy steps). */
    private fun <T> guarded(render: () -> T): T {
        if (!guard.permits.tryAcquire(guard.wait.toMillis(), TimeUnit.MILLISECONDS)) throw ExportBusyException()
        try {
            return render()
        } finally {
            guard.permits.release()
        }
    }

    private fun fileName(name: String, options: ExportOptions): String =
        "${slug(name)}-cv-${options.language.name.lowercase()}.${options.format.extension}"

    /** ASCII only: accents are stripped, `ß` becomes `ss`, every other run of characters becomes one dash. */
    private fun slug(name: String): String {
        val plain = Normalizer.normalize(name.replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).trim('-').ifEmpty { "cv" }
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
