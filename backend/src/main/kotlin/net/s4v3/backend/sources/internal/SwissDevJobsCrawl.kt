package net.s4v3.backend.sources.internal

import net.s4v3.backend.postings.IngestOutcome
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingRejectedException
import net.s4v3.backend.sources.AtsCrawl
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.BoardResult
import net.s4v3.backend.sources.CrawlResult
import net.s4v3.backend.sources.internal.greenhouse.MalformedResponse
import net.s4v3.backend.sources.internal.swissdevjobs.SWISSDEVJOBS_SOURCE_ID
import net.s4v3.backend.sources.internal.swissdevjobs.SwissDevJobsMapping
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

/**
 * Crawls SwissDevJobs: the JSON job list (`/api/jobsLight`) for the structured fields, then the RSS feed (`/rss`) for
 * the descriptions, joined by the job's slug. Only outcomes, counts and HTTP statuses are logged, never job text, URLs
 * or response bodies. Its result list is empty when the adapter is switched off.
 */
@Service
internal class SwissDevJobsCrawl(
    private val settings: SourcesSettings,
    private val http: PoliteHttp,
    private val state: FetchStateStore,
    private val ingest: PostingIngest,
    private val dataSource: DataSource,
) : AtsCrawl {

    private val log = LoggerFactory.getLogger(SwissDevJobsCrawl::class.java)
    private val config = settings.swissdevjobs

    private class Counts {
        var created = 0
        var updated = 0
        var unchanged = 0
        var skipped = 0
        var filteredOut = 0
        var retryLater = false
    }

    override fun run(): CrawlResult {
        if (!config.enabled) return CrawlResult(emptyList())
        val result = try {
            crawlLocked()
        } catch (e: Exception) {
            BoardResult(BOARD, BoardOutcome.FAILED, reason = "unexpected ${e.javaClass.simpleName}")
        }
        log.info(
            "SwissDevJobs: {} (created={}, updated={}, unchanged={}, skipped={}, filteredOut={}{})",
            result.outcome, result.created, result.updated, result.unchanged, result.skipped, result.filteredOut,
            result.reason?.let { ", reason=$it" } ?: "",
        )
        return CrawlResult(listOf(result))
    }

    private fun crawlLocked(): BoardResult {
        dataSource.connection.use { lock ->
            val acquired = lock.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))").use {
                it.setString(1, "crawl:$SWISSDEVJOBS_SOURCE_ID")
                it.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
            if (!acquired) return BoardResult(BOARD, BoardOutcome.ALREADY_RUNNING)
            try {
                return crawl()
            } finally {
                lock.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))").use {
                    it.setString(1, "crawl:$SWISSDEVJOBS_SOURCE_ID")
                    it.executeQuery().close()
                }
            }
        }
    }

    private fun crawl(): BoardResult {
        val now = Instant.now()
        val last = state.lastRunAt(SWISSDEVJOBS_SOURCE_ID)
        if (last != null && Duration.between(last, now) < settings.minInterval) {
            return BoardResult(BOARD, BoardOutcome.TOO_SOON)
        }
        state.recordRunStart(SWISSDEVJOBS_SOURCE_ID, now)

        val base = URI(config.baseUrl)
        val rules = when (val fetched = http.get(base.resolve("/robots.txt"), 512 * 1024, requireJson = false)) {
            is Fetched.Body -> RobotsTxt.parse(fetched.text)
            Fetched.NotModified -> return failed("robots.txt unreadable")
            is Fetched.Failed -> if (fetched.status == 404) RobotsTxt.ALLOW_ALL else return failed("robots.txt unreadable")
        }
        if (!rules.allows(LIST_PATH) || !rules.allows(FEED_PATH)) return BoardResult(BOARD, BoardOutcome.BLOCKED_BY_ROBOTS)

        val listed = when (val fetched = http.get(base.resolve(LIST_PATH), settings.maxListBytes, etag = state.etag(SWISSDEVJOBS_SOURCE_ID))) {
            is Fetched.Failed -> return failed(fetched.reason)
            Fetched.NotModified -> return BoardResult(BOARD, BoardOutcome.NOT_MODIFIED)
            is Fetched.Body -> fetched
        }
        val entries = try {
            SwissDevJobsMapping.parseList(listed.text)
        } catch (e: MalformedResponse) {
            return failed("malformed list")
        }
        val feed = when (val fetched = http.get(
            base.resolve(FEED_PATH), settings.maxListBytes, requireJson = false,
            accept = "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.5",
        )) {
            is Fetched.Failed -> return failed(fetched.reason)
            Fetched.NotModified -> return failed("feed not modified")
            is Fetched.Body -> try {
                SwissDevJobsMapping.parseFeed(fetched.text)
            } catch (e: MalformedResponse) {
                return failed("malformed feed")
            }
        }

        val counts = Counts()
        for (entry in entries) ingestEntry(entry, feed, counts)
        // A job without a description is retried on the next run, so the list validator is only kept when every job
        // had one; otherwise the next run would get a 304 and never see that job again.
        if (!counts.retryLater) state.saveEtag(SWISSDEVJOBS_SOURCE_ID, listed.etag)
        return BoardResult(
            BOARD, BoardOutcome.COMPLETED, counts.created, counts.updated, counts.unchanged, counts.skipped,
            counts.filteredOut,
        )
    }

    private fun ingestEntry(entry: tools.jackson.databind.JsonNode, feed: Map<String, String>, counts: Counts) {
        if (entry.path("isPaused").asBoolean(false)) {
            counts.filteredOut++
            return
        }
        val slug = SwissDevJobsMapping.slugOf(entry)
        val description = slug?.let { feed[it] }
        if (description.isNullOrBlank()) {
            counts.skipped++
            if (slug != null) counts.retryLater = true
            return
        }
        val candidate = SwissDevJobsMapping.toCandidate(entry, description)
        if (candidate == null) {
            counts.skipped++
            return
        }
        try {
            when (ingest.ingest(candidate).outcome) {
                IngestOutcome.CREATED -> counts.created++
                IngestOutcome.UPDATED -> counts.updated++
                IngestOutcome.UNCHANGED -> counts.unchanged++
            }
        } catch (e: PostingRejectedException) {
            counts.skipped++
        }
    }

    private fun failed(reason: String) = BoardResult(BOARD, BoardOutcome.FAILED, reason = reason)

    private companion object {
        const val BOARD = SWISSDEVJOBS_SOURCE_ID
        const val LIST_PATH = "/api/jobsLight"
        const val FEED_PATH = "/rss"
    }
}
