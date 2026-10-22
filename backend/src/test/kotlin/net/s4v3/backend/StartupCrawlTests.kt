package net.s4v3.backend

import net.s4v3.backend.FakeGreenhouse.Job
import net.s4v3.backend.sources.internal.StartupCrawl
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IntegrationTest
internal class StartupCrawlDisabledTests(@Autowired private val context: ApplicationContext) {

    @Test
    fun `by default there is no startup crawl`() {
        assertTrue(context.getBeanNamesForType(StartupCrawl::class.java).isEmpty())
    }
}

@IntegrationTest
internal class StartupCrawlEnabledTests {

    @Test
    fun `an enabled start makes exactly one crawl`() {
        val deadline = System.currentTimeMillis() + 15_000
        while (server.listRequests(BOARD) == 0 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        Thread.sleep(500)

        assertEquals(1, server.listRequests(BOARD), server.requests.toString())
    }

    companion object {
        private const val BOARD = "startupboard"
        private val server = FakeGreenhouse().also {
            it.boards[BOARD] = listOf(Job(1, "Backend Engineer", "Zurich", company = "Startupco"))
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("jobhunter.sources.greenhouse.run-on-startup") { "true" }
            registry.add("jobhunter.sources.greenhouse.base-url") { server.baseUrl }
            registry.add("jobhunter.sources.greenhouse.boards") { BOARD }
            registry.add("jobhunter.sources.pause") { "PT0S" }
        }
    }
}
