package net.s4v3.gateway

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/** Full gateway on a random port, routed to the shared [Stubs]. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
abstract class GatewayTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            registry.add("microservices.backend.uri") { Stubs.backend.uri }
            registry.add("microservices.auth.uri") { Stubs.auth.uri }
        }
    }
}
