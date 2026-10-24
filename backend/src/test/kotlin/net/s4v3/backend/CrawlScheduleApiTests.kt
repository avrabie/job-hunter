package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import net.s4v3.backend.sources.AtsCrawl
import net.s4v3.backend.sources.CrawlResult
import net.s4v3.backend.sources.internal.CrawlScheduler
import net.s4v3.backend.sources.internal.ScheduleStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ApiTest
internal class CrawlScheduleApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val store: ScheduleStore,
) {

    private val path = "/api/admin/crawl-schedule"
    private val issuer get() = SharedMockIssuer.instance
    private fun admin(sub: String = "admin-" + UUID.randomUUID()) = "Bearer " + issuer.token(sub, roles = listOf("USER", "ADMIN"))
    private fun user() = "Bearer " + issuer.token("user-" + UUID.randomUUID())

    // A far-off cron, so nothing here can fire while a test runs.
    private val yearly = """{"enabled":true,"cron":"0 0 3 1 1 *","zone":"Europe/Zurich"}"""

    @BeforeEach
    @AfterEach
    fun clean() {
        jdbc.update("delete from crawl_schedule")
    }

    private fun postJson(body: String, auth: String? = admin()) =
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body).apply { auth?.let { header("Authorization", it) } })

    private fun rows() = jdbc.queryForObject("select count(*) from crawl_schedule", Int::class.java)!!

    @Test
    fun `no token gets 401 and nothing changes`() {
        mvc.perform(get(path)).andExpect(status().isUnauthorized)
        postJson(yearly, auth = null).andExpect(status().isUnauthorized)

        assertEquals(0, rows())
    }

    @Test
    fun `a user without the ADMIN role gets 403 and the schedule is unchanged`() {
        mvc.perform(get(path).header("Authorization", user())).andExpect(status().isForbidden)
        postJson(yearly, auth = user()).andExpect(status().isForbidden)

        assertEquals(0, rows())
    }

    @Test
    fun `an admin reads the default hourly schedule`() {
        mvc.perform(get(path).header("Authorization", admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.cron").value("0 0 * * * *"))
            .andExpect(jsonPath("$.zone").value("Europe/Zurich"))
            .andExpect(jsonPath("$.nextFire").doesNotExist())
    }

    @Test
    fun `an admin changes the schedule and reads it back`() {
        val sub = "admin-" + UUID.randomUUID()

        postJson(yearly, admin(sub))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.cron").value("0 0 3 1 1 *"))
            .andExpect(jsonPath("$.nextFire").exists())
        mvc.perform(get(path).header("Authorization", admin()))
            .andExpect(jsonPath("$.cron").value("0 0 3 1 1 *"))
            .andExpect(jsonPath("$.updatedAt").exists())

        assertEquals(sub, jdbc.queryForObject("select updated_by from crawl_schedule", String::class.java))
    }

    @Test
    fun `disabling removes the next fire time`() {
        postJson("""{"enabled":false,"cron":"0 0 3 1 1 *","zone":"UTC"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.nextFire").doesNotExist())
    }

    @Test
    fun `invalid input is refused with the field named and nothing stored`() {
        postJson("""{"enabled":true,"cron":"not a cron","zone":"UTC"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.field").value("cron"))
        postJson("""{"enabled":true,"cron":"0 0 * * * *","zone":"Mars/Base"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.field").value("zone"))
        postJson("""{"enabled":true,"cron":"0 * * * * *","zone":"UTC"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.field").value("cron"))
        postJson("""{"enabled":true,"zone":"UTC"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.field").value("cron"))
        postJson("""{"cron":"0 0 * * * *","zone":"UTC"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.field").value("enabled"))

        assertEquals(0, rows())
    }

    @Test
    fun `a posted schedule takes effect in a running scheduler without restart`() {
        var calls = 0
        val crawl = object : AtsCrawl {
            override fun run(): CrawlResult { calls++; return CrawlResult(emptyList()) }
        }
        val scheduler = CrawlScheduler(store, crawl) { it.run() }
        val t0 = Instant.parse("2026-10-22T10:20:00Z")
        scheduler.tick(t0)

        postJson("""{"enabled":true,"cron":"0 30 * * * *","zone":"UTC"}""").andExpect(status().isOk)
        scheduler.tick(t0.plusSeconds(60))
        scheduler.tick(Instant.parse("2026-10-22T10:30:05Z"))

        assertEquals(1, calls)
    }

    @Test
    fun `any path under api admin is closed to a USER token even without its own annotation`() {
        mvc.perform(get("/api/admin/test/unlisted").header("Authorization", user())).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/test/unlisted").header("Authorization", admin())).andExpect(status().isOk)
    }

    @Test
    fun `a change is logged with enabled and cron only, never an identity`() {
        val logger = LoggerFactory.getLogger("net.s4v3.backend") as Logger
        val capture = LogCapture(logger)
        val previous = logger.level
        logger.level = Level.TRACE
        val sub = "admin-SUBMARKER-" + UUID.randomUUID()
        try {
            postJson(yearly, admin(sub)).andExpect(status().isOk)
        } finally {
            capture.close()
            logger.level = previous
        }

        val logged = capture.events().joinToString("\n") { it.formattedMessage }
        assertTrue("enabled=true" in logged && "cron=0 0 3 1 1 *" in logged, logged)
        assertFalse("SUBMARKER" in logged, "the administrator's id appears in the logs")
    }
}
