package net.s4v3.backend

import net.s4v3.backend.FakeGreenhouse.Job
import net.s4v3.backend.sources.internal.CrawlScheduler
import net.s4v3.backend.sources.internal.ScheduleStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real scheduler, ticking every second, against a stored schedule and a local fake Greenhouse. */
@IntegrationTest
internal class CrawlSchedulerWiringTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val store: ScheduleStore,
    @Autowired private val context: ApplicationContext,
) {

    @AfterEach
    fun clean() {
        jdbc.update("delete from crawl_schedule")
    }

    @Test
    fun `the scheduler is wired and nothing crawls while the schedule is off`() {
        assertEquals(1, context.getBeanNamesForType(CrawlScheduler::class.java).size)
        assertEquals(false, store.current().enabled)
        // A crawl left over from the other test may still be arriving; only requests after this point count.
        Thread.sleep(2500)
        server.requests.clear()
        Thread.sleep(2500)

        assertEquals(0, server.listRequests(BOARD), server.requests.toString())
    }

    @Test
    fun `a stored schedule starts a crawl without a restart`() {
        // Written straight into the table: the API would refuse a cron this fast.
        jdbc.update(
            "insert into crawl_schedule (id, enabled, cron, zone, updated_at, updated_by) values (1, true, '*/3 * * * * *', 'UTC', now(), 'test')",
        )

        val deadline = System.currentTimeMillis() + 20_000
        while (server.listRequests(BOARD) == 0 && System.currentTimeMillis() < deadline) Thread.sleep(100)

        assertTrue(server.listRequests(BOARD) >= 1, "no crawl request arrived")
    }

    companion object {
        private const val BOARD = "schedboard"
        private val server = FakeGreenhouse().also {
            it.boards[BOARD] = listOf(Job(1, "Backend Engineer", "Zurich", company = "Schedco"))
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("jobhunter.sources.schedule.tick") { "PT1S" }
            registry.add("jobhunter.sources.greenhouse.base-url") { server.baseUrl }
            registry.add("jobhunter.sources.greenhouse.boards") { BOARD }
            registry.add("jobhunter.sources.pause") { "PT0S" }
            registry.add("jobhunter.sources.min-interval") { "PT0S" }
        }
    }
}
