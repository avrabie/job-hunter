package net.s4v3.backend.postings.internal

import net.s4v3.backend.postings.IngestOutcome
import net.s4v3.backend.postings.IngestResult
import net.s4v3.backend.postings.JobIngested
import net.s4v3.backend.postings.Posting
import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.postings.PostingRejectedException
import net.s4v3.backend.postings.RemoteMode
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Ingest and read access over `posting` and `posting_source`. Each ingest holds a transaction-scoped advisory lock on
 * its (source, source posting id), so two calls for the same source posting run one after the other.
 *
 * Only ids, source ids and outcomes are logged, never posting text or payloads.
 */
@Service
internal class JdbcPostings(
    private val jdbc: JdbcClient,
    private val language: LanguageDetector,
    private val events: ApplicationEventPublisher,
) : PostingIngest, PostingQuery {

    private val log = LoggerFactory.getLogger(JdbcPostings::class.java)

    private data class Existing(val postingId: UUID, val primary: Boolean, val contentHash: String)

    @Transactional
    override fun ingest(candidate: PostingCandidate): IngestResult {
        validate(candidate)
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:key))")
            .param("key", candidate.sourceId + ":" + candidate.sourcePostingId)
            .query().singleRow()

        val now = Instant.now()
        val hash = contentHash(candidate)
        val existing = findExisting(candidate)
        val result = when {
            existing != null -> refresh(existing, candidate, hash, now)
            else -> insert(candidate, hash, now)
        }
        log.debug("Ingested posting {} from {}: {}", result.postingId, candidate.sourceId, result.outcome)
        return result
    }

    private fun validate(c: PostingCandidate) {
        val blank = listOf(
            "sourceId" to c.sourceId, "sourcePostingId" to c.sourcePostingId, "url" to c.url,
            "title" to c.title, "company" to c.company,
        ).firstOrNull { it.second.isBlank() }
        if (blank != null) throw PostingRejectedException(blank.first)
    }

    private fun contentHash(c: PostingCandidate) = Normalization.contentHash(
        c.title, c.company, c.description, c.city, c.canton, c.workloadText?.let { Normalization.workload(it) },
        c.remoteMode, c.salaryMin, c.salaryMax,
    )

    private fun findExisting(c: PostingCandidate): Existing? =
        jdbc.sql(
            """
            SELECT s.posting_id, s.is_primary, p.content_hash
            FROM posting_source s JOIN posting p ON p.id = s.posting_id
            WHERE s.source_id = :source AND s.source_posting_id = :id
            """.trimIndent(),
        )
            .param("source", c.sourceId).param("id", c.sourcePostingId)
            .query { rs, _ -> Existing(rs.getObject("posting_id", UUID::class.java), rs.getBoolean("is_primary"), rs.getString("content_hash")) }
            .optional().orElse(null)

    private fun refresh(existing: Existing, c: PostingCandidate, hash: String, now: Instant): IngestResult {
        jdbc.sql("UPDATE posting_source SET url = :url, last_seen = :now WHERE source_id = :source AND source_posting_id = :id")
            .param("url", c.url).param("now", ts(now)).param("source", c.sourceId).param("id", c.sourcePostingId).update()

        // Only the source that created the canonical posting may change it; others just keep it alive.
        if (!existing.primary) {
            touch(existing.postingId, now)
            return IngestResult(existing.postingId, IngestOutcome.UNCHANGED)
        }
        if (existing.contentHash == hash) {
            jdbc.sql("UPDATE posting SET raw_payload = :raw, last_seen = :now WHERE id = :id")
                .param("raw", c.rawPayload).param("now", ts(now)).param("id", existing.postingId).update()
            return IngestResult(existing.postingId, IngestOutcome.UNCHANGED)
        }

        val detected = language.detect(c.title, c.description)
        val workload = Normalization.workload(c.workloadText)
        jdbc.sql(
            """
            UPDATE posting SET title = :title, company = :company, description = :description, city = :city,
                canton = :canton, workload_min = :wmin, workload_max = :wmax, remote_mode = :remote,
                salary_min = :smin, salary_max = :smax, language = :language, out_of_scope = :oos,
                needs_review = :review, dedupe_key = :key, content_hash = :hash, raw_payload = :raw, last_seen = :now
            WHERE id = :id
            """.trimIndent(),
        )
            .param("title", c.title).param("company", c.company).param("description", c.description)
            .param("city", c.city).param("canton", c.canton)
            .param("wmin", workload?.first).param("wmax", workload?.second)
            .param("remote", c.remoteMode?.name).param("smin", c.salaryMin).param("smax", c.salaryMax)
            .param("language", detected.code).param("oos", detected.outOfScope).param("review", detected.needsReview)
            .param("key", Normalization.dedupeKey(c.company, c.title, c.city))
            .param("hash", hash).param("raw", c.rawPayload).param("now", ts(now)).param("id", existing.postingId)
            .update()
        return IngestResult(existing.postingId, IngestOutcome.UPDATED)
    }

    private fun insert(c: PostingCandidate, hash: String, now: Instant): IngestResult {
        val key = Normalization.dedupeKey(c.company, c.title, c.city)
        val canonical = key?.let(::findCanonical)
        if (canonical != null) {
            insertSource(canonical, c, primary = false, now)
            touch(canonical, now)
            return IngestResult(canonical, IngestOutcome.CREATED)
        }

        val id = UUID.randomUUID()
        val detected = language.detect(c.title, c.description)
        val workload = Normalization.workload(c.workloadText)
        jdbc.sql(
            """
            INSERT INTO posting (id, title, company, description, city, canton, workload_min, workload_max, remote_mode,
                salary_min, salary_max, language, out_of_scope, needs_review, dedupe_key, content_hash, raw_payload,
                first_seen, last_seen)
            VALUES (:id, :title, :company, :description, :city, :canton, :wmin, :wmax, :remote, :smin, :smax,
                :language, :oos, :review, :key, :hash, :raw, :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("title", c.title).param("company", c.company).param("description", c.description)
            .param("city", c.city).param("canton", c.canton)
            .param("wmin", workload?.first).param("wmax", workload?.second)
            .param("remote", c.remoteMode?.name).param("smin", c.salaryMin).param("smax", c.salaryMax)
            .param("language", detected.code).param("oos", detected.outOfScope).param("review", detected.needsReview)
            .param("key", key).param("hash", hash).param("raw", c.rawPayload).param("now", ts(now))
            .update()
        insertSource(id, c, primary = true, now)
        events.publishEvent(JobIngested(postingId = id, ingestedAt = now))
        return IngestResult(id, IngestOutcome.CREATED)
    }

    private fun findCanonical(key: String): UUID? =
        jdbc.sql("SELECT id FROM posting WHERE dedupe_key = :key ORDER BY first_seen DESC LIMIT 1")
            .param("key", key).query(UUID::class.java).optional().orElse(null)

    private fun insertSource(postingId: UUID, c: PostingCandidate, primary: Boolean, now: Instant) {
        jdbc.sql(
            """
            INSERT INTO posting_source (id, posting_id, source_id, source_posting_id, url, is_primary, last_seen)
            VALUES (:id, :posting, :source, :sourcePostingId, :url, :primary, :now)
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID()).param("posting", postingId).param("source", c.sourceId)
            .param("sourcePostingId", c.sourcePostingId).param("url", c.url).param("primary", primary)
            .param("now", ts(now)).update()
    }

    private fun touch(postingId: UUID, now: Instant) {
        jdbc.sql("UPDATE posting SET last_seen = :now WHERE id = :id").param("now", ts(now)).param("id", postingId).update()
    }

    @Transactional(readOnly = true)
    override fun find(id: UUID): Posting? =
        jdbc.sql("$SELECT_POSTING WHERE p.id = :id").param("id", id).query { rs, _ -> map(rs) }.optional().orElse(null)

    @Transactional(readOnly = true)
    override fun list(page: Int, size: Int): List<Posting> {
        val limit = size.coerceIn(1, PostingQuery.MAX_PAGE_SIZE)
        return jdbc.sql("$SELECT_POSTING ORDER BY p.first_seen DESC, p.id LIMIT :limit OFFSET :offset")
            .param("limit", limit).param("offset", page.coerceAtLeast(0).toLong() * limit)
            .query { rs, _ -> map(rs) }.list()
    }

    private fun map(rs: ResultSet) = Posting(
        id = rs.getObject("id", UUID::class.java),
        title = rs.getString("title"),
        company = rs.getString("company"),
        description = rs.getString("description"),
        url = rs.getString("url"),
        city = rs.getString("city"),
        canton = rs.getString("canton"),
        workloadMin = rs.getObject("workload_min") as Int?,
        workloadMax = rs.getObject("workload_max") as Int?,
        remoteMode = rs.getString("remote_mode")?.let(RemoteMode::valueOf),
        salaryMin = rs.getObject("salary_min") as Int?,
        salaryMax = rs.getObject("salary_max") as Int?,
        language = rs.getString("language"),
        outOfScope = rs.getBoolean("out_of_scope"),
        needsReview = rs.getBoolean("needs_review"),
        firstSeen = rs.getTimestamp("first_seen").toInstant(),
        lastSeen = rs.getTimestamp("last_seen").toInstant(),
    )

    private fun ts(instant: Instant) = Timestamp.from(instant)

    private companion object {
        // The primary source's URL is the posting's URL.
        const val SELECT_POSTING = """
            SELECT p.id, p.title, p.company, p.description, s.url, p.city, p.canton, p.workload_min, p.workload_max,
                   p.remote_mode, p.salary_min, p.salary_max, p.language, p.out_of_scope, p.needs_review,
                   p.first_seen, p.last_seen
            FROM posting p JOIN posting_source s ON s.posting_id = p.id AND s.is_primary
        """
    }
}
