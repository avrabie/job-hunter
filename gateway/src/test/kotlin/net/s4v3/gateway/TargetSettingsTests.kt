package net.s4v3.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteDefinitionLocator
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals

/** Without any setting the targets are the local defaults. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DefaultTargetsTests {

    @Autowired
    lateinit var routes: RouteDefinitionLocator

    @Test
    fun `the targets default to the local ports`() {
        val targets = routes.routeDefinitions.collectList().block()!!.associate { it.id!! to it.uri.toString() }

        assertEquals(mapOf("backend" to "http://localhost:8081", "auth" to "http://localhost:9090"), targets)
    }
}

/** Sets the variables the way an environment would, to prove the documented names bind. */
class EnvironmentVariables : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        context.environment.propertySources.addFirst(
            SystemEnvironmentPropertySource(
                "testEnvironment",
                mapOf(
                    "MICROSERVICES_BACKEND_URI" to Stubs.backend.uri,
                    "MICROSERVICES_AUTH_URI" to Stubs.auth.uri,
                ),
            ),
        )
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [EnvironmentVariables::class])
class EnvironmentTargetsTests {

    @Autowired
    lateinit var routes: RouteDefinitionLocator

    @Test
    fun `MICROSERVICES_BACKEND_URI and MICROSERVICES_AUTH_URI override the targets`() {
        val targets = routes.routeDefinitions.collectList().block()!!.associate { it.id!! to it.uri.toString() }

        assertEquals(mapOf("backend" to Stubs.backend.uri, "auth" to Stubs.auth.uri), targets)
    }
}
