package net.s4v3.gateway

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ContextConfiguration
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.ServerSocket
import kotlin.test.assertEquals

class HealthTests : GatewayTest() {

    @Autowired
    lateinit var client: WebTestClient

    @AfterEach
    fun restore() {
        runCatching { Stubs.backend.restart() }
        runCatching { Stubs.auth.restart() }
        Stubs.reset()
    }

    @Test
    fun `liveness and readiness are 200 even when both services are down`() {
        Stubs.backend.stop()
        Stubs.auth.stop()

        for (probe in listOf("liveness", "readiness")) {
            client.get().uri("/actuator/health/$probe").exchange()
                .expectStatus().isOk
                .expectBody().jsonPath("$.status").isEqualTo("UP")
        }
    }

    @Test
    fun `no other actuator endpoint is exposed`() {
        for (path in listOf("env", "gateway/routes", "beans", "info", "metrics")) {
            client.get().uri("/actuator/$path").exchange().expectStatus().isNotFound
        }
    }

    @Test
    fun `an api path under api actuator goes to the backend, not to the gateway's own actuator`() {
        Stubs.reset()

        client.get().uri("/api/actuator/health").exchange().expectStatus().isOk

        assertEquals("/api/actuator/health", Stubs.backend.received.single().uri)
    }
}

/*
 * Closing a Spring context also disposes Reactor Netty's shared event loops, which would hang the other cached test
 * contexts. So tests that need their own port use a cached @SpringBootTest context (closed at JVM exit) and never
 * start and close an application by hand.
 */
class PortEnvironment : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        context.environment.propertySources.addFirst(
            SystemEnvironmentPropertySource("testEnvironment", mapOf("SERVER_PORT" to PORT.toString())),
        )
    }

    companion object {
        val PORT: Int = ServerSocket(0).use { it.localPort }
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = [PortEnvironment::class])
class ServerPortVariableTests {

    @Test
    fun `SERVER_PORT sets the port the gateway listens on`() {
        WebTestClient.bindToServer().baseUrl("http://localhost:${PortEnvironment.PORT}").build()
            .get().uri("/actuator/health/liveness").exchange().expectStatus().isOk
    }
}

class DefaultPortTests {

    @Test
    fun `the default port is 8080`() {
        val yaml = YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml"))
        assertEquals(8080, yaml.first().getProperty("server.port"))
    }
}
