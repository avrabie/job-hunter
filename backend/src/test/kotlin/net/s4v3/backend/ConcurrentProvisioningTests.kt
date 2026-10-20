package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals

@ApiTest
@Import(RegisteredEventsConfig::class)
class ConcurrentProvisioningTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val events: RegisteredEvents,
) {

    @Test
    fun `parallel first requests for one subject create one record and one event and none fail`() {
        val sub = "user-" + UUID.randomUUID()
        val token = SharedMockIssuer.instance.token(sub)
        val parallel = 24
        val ready = CountDownLatch(parallel)
        val go = CountDownLatch(1)

        val statuses = Executors.newFixedThreadPool(parallel).use { pool ->
            val results = (1..parallel).map {
                pool.submit<Int> {
                    ready.countDown()
                    go.await()
                    mvc.perform(get("/api/test/ping").header("Authorization", "Bearer $token")).andReturn().response.status
                }
            }
            ready.await()
            go.countDown()
            results.map { it.get() }
        }

        assertEquals(List(parallel) { 200 }, statuses)
        assertEquals(1, jdbc.queryForObject("select count(*) from app_user where sub = ?", Int::class.java, sub))
        assertEquals(1, events.received.count { it.userId == sub })
    }
}
