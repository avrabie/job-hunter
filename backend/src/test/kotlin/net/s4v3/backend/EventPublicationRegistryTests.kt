package net.s4v3.backend

import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

data class FailingProbe(val id: String)
data class SucceedingProbe(val id: String)

@TestConfiguration(proxyBeanMethods = false)
@EnableAsync
class ProbeListeners {

    @ApplicationModuleListener
    fun on(event: FailingProbe): Unit = throw IllegalStateException("listener fails on purpose")

    @ApplicationModuleListener
    fun on(event: SucceedingProbe) = Unit
}

/** Starts the application twice against the same database, as a restart would. */
@Tag("integration")
class EventPublicationRegistryTests {

    private fun start(): ConfigurableApplicationContext =
        SpringApplicationBuilder(BackendApplication::class.java, ProbeListeners::class.java)
            .web(WebApplicationType.NONE)
            .profiles("test")
            .properties(
                "spring.datasource.url=${postgres.jdbcUrl}",
                "spring.datasource.username=${postgres.username}",
                "spring.datasource.password=${postgres.password}",
            )
            .run()

    private fun ConfigurableApplicationContext.publish(event: Any) {
        getBean(TransactionTemplate::class.java).executeWithoutResult {
            publishEvent(event)
        }
    }

    private fun ConfigurableApplicationContext.completionDates(id: String): List<Any?> =
        getBean(JdbcTemplate::class.java).queryForList(
            "select completion_date from event_publication where serialized_event like ?",
            "%$id%",
        ).map { it["completion_date"] }

    @Test
    fun `an event whose listener failed is still recorded as incomplete after a restart`() {
        val id = UUID.randomUUID().toString()

        start().use { it.publish(FailingProbe(id)) }

        start().use { restarted ->
            val rows = restarted.completionDates(id)
            assertEquals(1, rows.size)
            assertEquals(null, rows.single())
        }
    }

    @Test
    fun `an event is marked completed once all its listeners complete`() {
        val id = UUID.randomUUID().toString()

        start().use { context ->
            context.publish(SucceedingProbe(id))

            await().atMost(Duration.ofSeconds(10)).until {
                context.completionDates(id).singleOrNull() != null
            }
        }
    }

    private val postgres get() = SharedPostgres.instance
}
