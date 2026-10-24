package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import net.s4v3.backend.sources.AtsCrawl
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.BoardResult
import net.s4v3.backend.sources.CrawlResult
import net.s4v3.backend.sources.internal.CrawlSchedule
import net.s4v3.backend.sources.internal.CrawlScheduler
import net.s4v3.backend.sources.internal.ScheduleSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CrawlSchedulerTests {

    private class FakeCrawl : AtsCrawl {
        var calls = 0
        var failWith: Exception? = null
        var result = CrawlResult(emptyList())
        override fun run(): CrawlResult {
            calls++
            failWith?.let { throw it }
            return result
        }
    }

    private var schedule = CrawlSchedule(true, "0 0 * * * *", "UTC")
    private val source = ScheduleSource { schedule }
    private val crawl = FakeCrawl()
    private val queued = mutableListOf<Runnable>()

    // Work is queued, not run, so a test can decide when the "background" crawl finishes.
    private val scheduler = CrawlScheduler(source, crawl) { queued += it }

    private val logger = LoggerFactory.getLogger(CrawlScheduler::class.java) as Logger
    private val capture = LogCapture(logger)
    private fun logged() = capture.events().joinToString("\n") { it.formattedMessage }

    @BeforeEach
    fun setLevel() {
        logger.level = Level.INFO
    }

    @AfterEach
    fun release() { capture.close() }

    private fun at(time: String) = Instant.parse("2026-10-22T${time}Z")
    private fun runQueued() { queued.toList().forEach { it.run() }; queued.clear() }

    @Test
    fun `nothing fires before the next fire time and one crawl fires at it`() {
        scheduler.tick(at("10:20:00"))
        scheduler.tick(at("10:59:30"))
        runQueued()
        assertEquals(0, crawl.calls)

        scheduler.tick(at("11:00:10"))
        runQueued()
        assertEquals(1, crawl.calls)

        scheduler.tick(at("11:00:40"))
        runQueued()
        assertEquals(1, crawl.calls)
    }

    @Test
    fun `a fire while the previous crawl still runs is skipped and logged`() {
        scheduler.tick(at("10:20:00"))
        scheduler.tick(at("11:00:10"))
        scheduler.tick(at("12:00:10"))

        assertEquals(1, queued.size)
        assertTrue("skipped" in logged(), logged())
    }

    @Test
    fun `after a crawl finishes the next fire runs again`() {
        scheduler.tick(at("10:20:00"))
        scheduler.tick(at("11:00:10"))
        runQueued()
        scheduler.tick(at("12:00:10"))
        runQueued()

        assertEquals(2, crawl.calls)
    }

    @Test
    fun `a throwing crawl is logged by type only and the next fire still runs`() {
        crawl.failWith = IllegalStateException("response body SECRET-MARKER")
        scheduler.tick(at("10:20:00"))
        scheduler.tick(at("11:00:10"))
        runQueued()
        crawl.failWith = null
        scheduler.tick(at("12:00:10"))
        runQueued()

        assertEquals(2, crawl.calls)
        assertTrue("IllegalStateException" in logged())
        assertFalse("SECRET-MARKER" in logged())
    }

    @Test
    fun `a disabled schedule never fires`() {
        schedule = schedule.copy(enabled = false)
        scheduler.tick(at("10:20:00"))
        scheduler.tick(at("13:00:10"))
        runQueued()

        assertEquals(0, crawl.calls)
    }

    @Test
    fun `a start after a due time makes no catch-up run and waits for the next fire`() {
        // The scheduler first sees the schedule at 11:30, after the 11:00 fire time.
        scheduler.tick(at("11:30:00"))
        scheduler.tick(at("11:30:30"))
        runQueued()
        assertEquals(0, crawl.calls)

        scheduler.tick(at("12:00:05"))
        runQueued()
        assertEquals(1, crawl.calls)
    }

    @Test
    fun `a schedule change recomputes the next fire from now, without catch-up`() {
        scheduler.tick(at("10:20:00"))
        schedule = schedule.copy(cron = "0 30 * * * *", updatedAt = at("10:50:00"))

        scheduler.tick(at("10:50:10"))
        runQueued()
        assertEquals(0, crawl.calls, "the old 11:00 fire time is gone, the new one is 11:30")

        scheduler.tick(at("11:00:10"))
        runQueued()
        assertEquals(0, crawl.calls)

        scheduler.tick(at("11:30:10"))
        runQueued()
        assertEquals(1, crawl.calls)
    }

    @Test
    fun `the summary holds board counts and job totals only`() {
        val result = CrawlResult(
            (1..9).map { BoardResult("b$it", BoardOutcome.COMPLETED, created = 1, updated = 2, unchanged = 3, skipped = 4, filteredOut = 5) } +
                BoardResult("bad", BoardOutcome.FAILED, reason = "SECRET-MARKER"),
        )

        val line = CrawlScheduler.summary(result)

        assertTrue("completed=9" in line && "failed=1" in line, line)
        assertTrue("created=9" in line && "updated=18" in line && "unchanged=27" in line, line)
        assertTrue("skipped=36" in line && "filteredOut=45" in line, line)
        assertFalse("SECRET-MARKER" in line)
    }
}
