package net.s4v3.backend.jobimport.internal

import net.s4v3.backend.applications.ApplicationLimitException
import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.applications.ContactDetails
import net.s4v3.backend.applications.JobData
import net.s4v3.backend.jobimport.ImportConfirmation
import net.s4v3.backend.jobimport.ImportContact
import net.s4v3.backend.jobimport.ImportEdits
import net.s4v3.backend.jobimport.ImportError
import net.s4v3.backend.jobimport.ImportPreview
import net.s4v3.backend.jobimport.ImportRequest
import net.s4v3.backend.jobimport.JobImportException
import net.s4v3.backend.jobimport.JobImportService
import net.s4v3.backend.llm.JobExtractor
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Configuration
internal class PageFetcherConfig {
    /** Identifies us to the sites we fetch from; the same text the crawlers send. */
    @Bean
    fun safeFetcher(@Value($$"${jobhunter.sources.user-agent:JobHunter/0.1 (+https://s4v3.net)}") userAgent: String): SafeFetcher =
        SafeFetcher(userAgent)
}

/**
 * Preview and confirm of a job import. Failures are [JobImportException] with a fixed code. The URL, the page text, the
 * extracted fields and the model's answer are never logged: only the code, the draft id and the duration.
 */
@Service
internal class DefaultJobImportService(
    private val fetcher: PageFetcher,
    private val extractor: JobExtractor,
    private val drafts: ImportDrafts,
    private val applications: ApplicationService,
    private val previewLimiter: PreviewLimiter,
    private val inFlight: InFlightLimiter,
    private val confirmLimiter: ConfirmLimiter,
) : JobImportService {

    private val log = LoggerFactory.getLogger(DefaultJobImportService::class.java)

    override fun preview(sub: String, request: ImportRequest): ImportPreview {
        val start = System.nanoTime()
        try {
            val preview = doPreview(sub, request)
            log.info("Import preview ok draft={} in {} ms", preview.draftId, millisSince(start))
            return preview
        } catch (e: JobImportException) {
            log.info("Import preview failed code={} in {} ms", e.error.code, millisSince(start))
            throw e
        }
    }

    private fun doPreview(sub: String, request: ImportRequest): ImportPreview {
        // Fetching and extracting hold a thread, memory and a paid model call: only a few at once, per user and in total.
        if (!inFlight.tryEnter(sub)) throw JobImportException(ImportError.RATE_LIMITED, BUSY_RETRY)
        try {
            return previewWithin(sub, request)
        } finally {
            inFlight.leave(sub)
        }
    }

    private fun previewWithin(sub: String, request: ImportRequest): ImportPreview {
        previewLimiter.tryAcquire(sub)?.let { throw JobImportException(ImportError.RATE_LIMITED, it) }
        val url = when (val check = UrlRules.check(UrlRules.normalizeInput(request.url.orEmpty()))) {
            is UrlCheck.Ok -> check.url
            is UrlCheck.Rejected -> throw JobImportException(check.error.toImportError())
        }
        val pageText = if (!request.text.isNullOrBlank()) {
            request.text.trim().takeIf { it.length >= PageText.MIN_CHARS }?.take(PageText.MAX_CHARS)
        } else {
            when (val fetched = fetcher.fetch(url.canonical)) {
                is FetchResult.Failed -> throw JobImportException(fetched.error.toImportError())
                is FetchResult.Page -> PageText.extract(fetched.html)
            }
        } ?: throw JobImportException(ImportError.UNREADABLE)

        val job = try {
            extractor.extract(pageText)
        } catch (e: LlmFailure) {
            throw JobImportException(if (e.kind == LlmFailureKind.NOT_CONFIGURED) ImportError.LLM_UNAVAILABLE else ImportError.EXTRACTION_FAILED)
        }
        if (!job.found) throw JobImportException(ImportError.NOT_A_JOB)
        if (!Grounding.isGrounded(job.description, pageText) || !Grounding.isGrounded(job.title, pageText) ||
            !Grounding.isGrounded(job.company, pageText)
        ) throw JobImportException(ImportError.EXTRACTION_FAILED)

        val draft = Draft(UUID.randomUUID(), url.canonical, job.title, job.company, job.location, job.description, job.language.name,
            JobContactGrounding.ground(job, pageText),
        )
        drafts.insert(sub, draft, Instant.now())
        return ImportPreview(draft.id, draft.title, draft.company, draft.location, draft.description, draft.language, draft.contact)
    }

    override fun confirm(sub: String, draftId: UUID, edits: ImportEdits): ImportConfirmation {
        val start = System.nanoTime()
        try {
            val result = doConfirm(sub, draftId, edits)
            log.info("Import confirm ok draft={} in {} ms", draftId, millisSince(start))
            return result
        } catch (e: JobImportException) {
            log.info("Import confirm failed code={} draft={} in {} ms", e.error.code, draftId, millisSince(start))
            throw e
        }
    }

    private fun doConfirm(sub: String, draftId: UUID, edits: ImportEdits): ImportConfirmation {
        val now = Instant.now()
        val draft = drafts.find(sub, draftId, now) ?: throw JobImportException(ImportError.DRAFT_NOT_FOUND)
        val title = field(edits.title, draft.title, MAX_TITLE, required = true)!!
        val company = field(edits.company, draft.company, MAX_COMPANY, required = true)!!
        field(edits.location, draft.location, MAX_LOCATION, required = false) // checked; a location is not kept on an application
        val description = field(edits.description, draft.description, MAX_DESCRIPTION, required = true)!!
        val contact = contact(edits.contact, draft.contact)
        confirmLimiter.tryAcquire(sub, now)?.let { throw JobImportException(ImportError.RATE_LIMITED, it) }

        val tracked = try {
            applications.trackImported(
                sub, JobData(
                    draft.url, title, company, description, draft.language.lowercase(),
                    ContactDetails(contact.street, contact.postalCode, contact.city, contact.person, contact.phone, contact.email),
                ),
            )
        } catch (_: ApplicationLimitException) {
            throw JobImportException(ImportError.APPLICATION_LIMIT)
        }
        drafts.delete(sub, draftId)
        return ImportConfirmation(tracked.application.id, tracked.created)
    }

    override fun removeUserData(sub: String) = drafts.deleteByOwner(sub)

    /** The edit when given, else [current]; blank location becomes null. A bad edit is [ImportError.INVALID_EDIT]. */
    private fun field(edit: String?, current: String?, max: Int, required: Boolean): String? {
        val value = (edit ?: current)?.trim().orEmpty()
        if (value.isEmpty()) return if (required) throw JobImportException(ImportError.INVALID_EDIT) else null
        if (value.length > max || HTML.containsMatchIn(value)) throw JobImportException(ImportError.INVALID_EDIT)
        return value
    }

    /** The contact after the edits: a null edit keeps the draft's value, a blank one clears it. Same limits as the application's. */
    private fun contact(edit: ImportContact?, current: ImportContact): ImportContact {
        if (edit == null) return current
        val email = field(edit.email, current.email, MAX_EMAIL, required = false)
        if (email != null && email != current.email && !EMAIL.matches(email)) throw JobImportException(ImportError.INVALID_EDIT)
        return ImportContact(
            street = field(edit.street, current.street, MAX_LOCATION, required = false),
            postalCode = field(edit.postalCode, current.postalCode, MAX_POSTAL_CODE, required = false),
            city = field(edit.city, current.city, MAX_LOCATION, required = false),
            person = field(edit.person, current.person, MAX_LOCATION, required = false),
            phone = field(edit.phone, current.phone, MAX_PHONE, required = false),
            email = email,
        )
    }

    private fun FetchError.toImportError() = when (this) {
        FetchError.INVALID_URL -> ImportError.INVALID_URL
        FetchError.UNSUPPORTED_SITE -> ImportError.UNSUPPORTED_SITE
        FetchError.BLOCKED_ADDRESS -> ImportError.BLOCKED_ADDRESS
        FetchError.UNREACHABLE -> ImportError.UNREACHABLE
        FetchError.NOT_HTML -> ImportError.NOT_HTML
        FetchError.TOO_LARGE -> ImportError.TOO_LARGE
    }

    private fun millisSince(start: Long) = (System.nanoTime() - start) / 1_000_000

    private companion object {
        val BUSY_RETRY: Duration = Duration.ofSeconds(5)
        const val MAX_TITLE = 200
        const val MAX_COMPANY = 200
        const val MAX_LOCATION = 200
        const val MAX_DESCRIPTION = 20_000
        const val MAX_POSTAL_CODE = 10
        const val MAX_PHONE = 40
        const val MAX_EMAIL = 254
        val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        val HTML = Regex("<\\s*[a-zA-Z/!]")
    }
}

/** The extracted description must come from the page: most of its words have to occur in the page text. */
internal object Grounding {
    private const val MIN_SHARE = 0.8
    private val words = Regex("[\\p{L}\\p{N}]+")

    fun isGrounded(description: String, pageText: String): Boolean {
        val wanted = words.findAll(description.lowercase()).map { it.value }.toList()
        if (wanted.isEmpty()) return false
        val present = words.findAll(pageText.lowercase()).map { it.value }.toHashSet()
        return wanted.count { it in present } >= MIN_SHARE * wanted.size
    }
}
