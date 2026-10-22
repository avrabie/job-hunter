package net.s4v3.backend.sources.internal

import net.s4v3.backend.sources.AtsCrawl
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.CrawlResult
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Configuration
@EnableScheduling
internal class SchedulingConfig

/**
 * Starts a crawl when the stored schedule says it is due. A tick every 30 seconds reads the schedule, so a change made
 * through the API applies without a restart. The next fire time is always computed from "now", so a time missed while
 * the application was down, or before a schedule change, is never made up. Only counts and outcomes are logged.
 */
@Component
internal class CrawlScheduler(
    private val store: ScheduleSource,
    private val crawl: AtsCrawl,
    private val dispatch: (Runnable) -> Unit = { Thread.ofVirtual().name("scheduled-crawl").start(it) },
) {

    private val log = LoggerFactory.getLogger(CrawlScheduler::class.java)
    private val running = AtomicBoolean(false)
    private var seen: CrawlSchedule? = null
    private var nextFire: Instant? = null

    @Scheduled(
        fixedDelayString = "\${jobhunter.sources.schedule.tick:PT30S}",
        initialDelayString = "\${jobhunter.sources.schedule.tick:PT30S}",
    )
    fun tick() = tick(Instant.now())

    @Synchronized
    fun tick(now: Instant) {
        val schedule = try {
            store.current()
        } catch (e: Exception) {
            log.warn("Reading the crawl schedule failed: {}", e.javaClass.simpleName)
            return
        }
        if (schedule != seen) {
            seen = schedule
            nextFire = compute(schedule, now)
            return
        }
        val due = nextFire ?: return
        if (now < due) return
        nextFire = compute(schedule, now)
        start()
    }

    private fun compute(schedule: CrawlSchedule, now: Instant): Instant? =
        if (schedule.enabled) ScheduleRules.nextFire(schedule.cron, schedule.zone, now) else null

    private fun start() {
        if (!running.compareAndSet(false, true)) {
            log.info("Scheduled crawl skipped: the previous crawl is still running")
            return
        }
        dispatch(Runnable {
            try {
                log.info("Scheduled crawl started")
                log.info("Scheduled crawl finished: {}", summary(crawl.run()))
            } catch (e: Exception) {
                log.warn("Scheduled crawl failed: {}", e.javaClass.simpleName)
            } finally {
                running.set(false)
            }
        })
    }

    companion object {
        /** Board counts per outcome and job totals; numbers and enum names only. */
        fun summary(result: CrawlResult): String {
            val boards = BoardOutcome.entries.joinToString(", ") { o ->
                "${o.name.lowercase()}=${result.boards.count { it.outcome == o }}"
            }
            val done = result.boards.filter { it.outcome == BoardOutcome.COMPLETED }
            return "boards[$boards] jobs[created=${done.sumOf { it.created }}, updated=${done.sumOf { it.updated }}, " +
                "unchanged=${done.sumOf { it.unchanged }}, skipped=${done.sumOf { it.skipped }}, " +
                "filteredOut=${done.sumOf { it.filteredOut }}]"
        }
    }
}
