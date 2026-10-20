package net.s4v3.backend

import net.s4v3.backend.accounts.UserRegistered
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

class RegisteredEvents {
    val received = CopyOnWriteArrayList<UserRegistered>()

    @EventListener
    fun on(event: UserRegistered) {
        received += event
    }
}

@TestConfiguration(proxyBeanMethods = false)
class RegisteredEventsConfig {
    @Bean
    fun registeredEvents() = RegisteredEvents()
}

@ApiTest
@Import(RegisteredEventsConfig::class)
class UserRegisteredEventTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val events: RegisteredEvents,
    @Autowired private val json: JsonMapper,
) {

    private val issuer get() = SharedMockIssuer.instance

    private fun call(sub: String, claims: Map<String, Any> = emptyMap()) {
        mvc.perform(get("/api/test/ping").header("Authorization", "Bearer ${issuer.token(sub, extraClaims = claims)}"))
            .andExpect(status().isOk)
    }

    private fun eventsFor(sub: String) = events.received.filter { it.userId == sub }

    @Test
    fun `the first request publishes exactly one event for the new user`() {
        val sub = "user-" + UUID.randomUUID()

        call(sub)

        assertEquals(1, eventsFor(sub).size)
    }

    @Test
    fun `a repeat request publishes no further event`() {
        val sub = "user-" + UUID.randomUUID()
        call(sub)

        call(sub)
        call(sub)

        assertEquals(1, eventsFor(sub).size)
    }

    @Test
    fun `the payload has the user id and the time but no email or name`() {
        val sub = "user-" + UUID.randomUUID()

        call(sub, mapOf("email" to "anna@example.com", "name" to "Anna Muster"))

        val serialized = json.writeValueAsString(eventsFor(sub).single())
        assertEquals(setOf("userId", "registeredAt"), json.readTree(serialized).propertyNames().toSet())
        assertEquals(false, "anna" in serialized.lowercase() || "muster" in serialized.lowercase(), serialized)
    }
}
