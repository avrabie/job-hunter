package net.s4v3.backend.jobimport.internal

import net.s4v3.backend.applications.ApplicationLimitException
import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.jobimport.ImportConfirmation
import net.s4v3.backend.jobimport.ImportEdits
import net.s4v3.backend.jobimport.ImportError
import net.s4v3.backend.jobimport.ImportPreview
import net.s4v3.backend.jobimport.ImportRequest
import net.s4v3.backend.jobimport.JobImportException
import net.s4v3.backend.jobimport.JobImportService
import net.s4v3.backend.llm.JobExtractor
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.postings.IngestOutcome
import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.security.MessageDigest
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
    private val ingest: PostingIngest,
    private val applications: ApplicationService,
    private val previewLimiter: PreviewLimiter,
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
        previewLimiter.tryAcquire(sub)?.let { throw JobImportException(ImportError.RATE_LIMITED, it) }
        val url = when (val check = UrlRules.check(request.url.orEmpty())) {
            is UrlCheck.Ok -> check.url
            is UrlCheck.Rejected -> throw JobImportException(check.error.toImportError())
        }
        val pageText = if (!request.text.isNullOrBlank()) {
            request.text.trim().takeIf { it.length >= PageText.MIN_CHARS }?.take(PageText.MAX_CHARS)
        } else {
            when (val fetched = fetcher.fetch(request.url.orEmpty())) {
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
        if (!Grounding.isGrounded(job.description, pageText)) throw JobImportException(ImportError.EXTRACTION_FAILED)

        val draft = Draft(UUID.randomUUID(), url.canonical, job.title, job.company, job.location, job.description, job.language.name)
        drafts.insert(sub, draft, Instant.now())
        return ImportPreview(draft.id, draft.title, draft.company, draft.location, draft.description, draft.language)
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
        val location = field(edits.location, draft.location, MAX_LOCATION, required = false)
        val description = field(edits.description, draft.description, MAX_DESCRIPTION, required = true)!!
        confirmLimiter.tryAcquire(sub, now)?.let { throw JobImportException(ImportError.RATE_LIMITED, it) }

        // A URL that was imported before keeps its posting as it is: a second user must not rewrite it through ingest.
        val sourcePostingId = sha256(draft.url)
        val known = drafts.findImported(SOURCE_ID, sourcePostingId)
        val postingId: UUID
        val existed: Boolean
        if (known != null) {
            postingId = known
            existed = true
        } else {
            val ingested = ingest.ingest(
                PostingCandidate(
                    sourceId = SOURCE_ID, sourcePostingId = sourcePostingId, url = draft.url, title = title, company = company,
                    description = description, rawPayload = description, city = location,
                ),
            )
            postingId = ingested.postingId
            existed = ingested.outcome != IngestOutcome.CREATED || drafts.hasOtherSource(postingId, SOURCE_ID, sourcePostingId)
        }
        val tracked = try {
            applications.track(sub, postingId)
        } catch (_: ApplicationLimitException) {
            throw JobImportException(ImportError.APPLICATION_LIMIT)
        } ?: throw JobImportException(ImportError.DRAFT_NOT_FOUND)
        drafts.recordImport(postingId, sub, draft.url, now)
        drafts.delete(sub, draftId)
        return ImportConfirmation(tracked.application.id, postingId, tracked.created, existed)
    }

    override fun removeUserData(sub: String) = drafts.deleteByOwner(sub)

    /** The edit when given, else [current]; blank location becomes null. A bad edit is [ImportError.INVALID_EDIT]. */
    private fun field(edit: String?, current: String?, max: Int, required: Boolean): String? {
        val value = (edit ?: current)?.trim().orEmpty()
        if (value.isEmpty()) return if (required) throw JobImportException(ImportError.INVALID_EDIT) else null
        if (value.length > max || HTML.containsMatchIn(value)) throw JobImportException(ImportError.INVALID_EDIT)
        return value
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

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE_ID = "user-import"
        const val MAX_TITLE = 200
        const val MAX_COMPANY = 200
        const val MAX_LOCATION = 200
        const val MAX_DESCRIPTION = 20_000
        val HTML = Regex("<[a-zA-Z/!]")
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
