package net.s4v3.backend.sources.internal

import net.s4v3.backend.postings.IngestOutcome
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingRejectedException
import net.s4v3.backend.sources.AtsCrawl
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.BoardResult
import net.s4v3.backend.sources.CrawlResult
import net.s4v3.backend.sources.internal.greenhouse.GreenhouseMapping
import net.s4v3.backend.sources.internal.greenhouse.MalformedResponse
import net.s4v3.backend.sources.internal.greenhouse.RoleFilter
import net.s4v3.backend.sources.internal.greenhouse.SwissLocation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

/**
 * Crawls the configured Greenhouse boards one after the other: the light job list first, then the detail of each
 * Swiss IT job. Only board tokens, outcomes, counts and HTTP statuses are logged, never job text or response bodies.
 */
@Service
internal class GreenhouseCrawl(
    private val settings: SourcesSettings,
    private val http: PoliteHttp,
    private val state: FetchStateStore,
    private val ingest: PostingIngest,
    private val dataSource: DataSource,
) : AtsCrawl {

    private val log = LoggerFactory.getLogger(GreenhouseCrawl::class.java)
    private val config = settings.greenhouse
    private val roles = RoleFilter(config.itKeywords, config.excludedKeywords)

    private sealed interface Robots {
        data class Rules(val robots: RobotsTxt) : Robots
        data object Unreadable : Robots
    }

    private class Counts {
        var created = 0
        var updated = 0
        var unchanged = 0
        var skipped = 0
        var filteredOut = 0
        var retryLater = false
    }

    override fun run(): CrawlResult {
        val boards = config.boards.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val robots = mutableMapOf<String, Robots>()
        val results = boards.map { board ->
            val result = try {
                crawlBoard(board, robots)
            } catch (e: Exception) {
                BoardResult(board, BoardOutcome.FAILED, reason = "unexpected ${e.javaClass.simpleName}")
            }
            log.info(
                "Greenhouse board {}: {} (created={}, updated={}, unchanged={}, skipped={}, filteredOut={}{})",
                board, result.outcome, result.created, result.updated, result.unchanged, result.skipped,
                result.filteredOut, result.reason?.let { ", reason=$it" } ?: "",
            )
            result
        }
        return CrawlResult(results)
    }

    private fun crawlBoard(board: String, robots: MutableMap<String, Robots>): BoardResult {
        if (!Regex("[A-Za-z0-9_-]+").matches(board)) return failed(board, "invalid board token")
        val sourceId = "greenhouse-$board"
        dataSource.connection.use { lock ->
            val acquired = lock.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))").use {
                it.setString(1, "crawl:$sourceId")
                it.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
            if (!acquired) return BoardResult(board, BoardOutcome.ALREADY_RUNNING)
            try {
                return crawlLocked(board, sourceId, robots)
            } finally {
                lock.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))").use {
                    it.setString(1, "crawl:$sourceId")
                    it.executeQuery().close()
                }
            }
        }
    }

    private fun crawlLocked(board: String, sourceId: String, robots: MutableMap<String, Robots>): BoardResult {
        val now = Instant.now()
        val last = state.lastRunAt(sourceId)
        if (last != null && Duration.between(last, now) < settings.minInterval) {
            return BoardResult(board, BoardOutcome.TOO_SOON)
        }
        state.recordRunStart(sourceId, now)

        val base = URI(config.baseUrl)
        val rules = when (val r = robots.getOrPut(base.authority) { readRobots(base) }) {
            is Robots.Rules -> r.robots
            Robots.Unreadable -> return failed(board, "robots.txt unreadable")
        }
        val listPath = "/v1/boards/$board/jobs"
        if (!rules.allows(listPath)) return BoardResult(board, BoardOutcome.BLOCKED_BY_ROBOTS)

        val listed = when (val fetched = http.get(base.resolve(listPath), settings.maxListBytes, etag = state.etag(sourceId))) {
            is Fetched.Failed -> return failed(board, fetched.reason)
            Fetched.NotModified -> return BoardResult(board, BoardOutcome.NOT_MODIFIED)
            is Fetched.Body -> fetched
        }
        val jobs = try {
            GreenhouseMapping.parseList(listed.text)
        } catch (e: MalformedResponse) {
            return failed(board, "malformed list")
        }

        val counts = Counts()
        for (job in jobs) {
            if (SwissLocation.find(job.location) != null && roles.isIt(job.title)) {
                crawlJob(board, job.id, base, rules, counts)
            } else {
                counts.filteredOut++
            }
        }
        // A job whose detail could not be fetched is retried on the next run, so the list validator is only kept
        // when every detail was read; otherwise the next run would get a 304 and never see that job.
        if (!counts.retryLater) state.saveEtag(sourceId, listed.etag)
        return BoardResult(
            board, BoardOutcome.COMPLETED, counts.created, counts.updated, counts.unchanged, counts.skipped,
            counts.filteredOut,
        )
    }

    private fun crawlJob(board: String, id: String, base: URI, rules: RobotsTxt, counts: Counts) {
        val path = "/v1/boards/$board/jobs/$id"
        if (!rules.allows(path)) {
            counts.skipped++
            return
        }
        val fetched = http.get(base.resolve(path), settings.maxJobBytes)
        if (fetched !is Fetched.Body) {
            counts.skipped++
            counts.retryLater = true
            return
        }
        val candidate = try {
            GreenhouseMapping.toCandidate(board, GreenhouseMapping.parseJob(fetched.text))
        } catch (e: MalformedResponse) {
            counts.retryLater = true
            null
        }
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

    private fun readRobots(base: URI): Robots =
        when (val fetched = http.get(base.resolve("/robots.txt"), 512 * 1024, requireJson = false)) {
            is Fetched.Body -> Robots.Rules(RobotsTxt.parse(fetched.text))
            Fetched.NotModified -> Robots.Unreadable
            is Fetched.Failed -> if (fetched.status == 404) Robots.Rules(RobotsTxt.ALLOW_ALL) else Robots.Unreadable
        }

    private fun failed(board: String, reason: String) = BoardResult(board, BoardOutcome.FAILED, reason = reason)
}
