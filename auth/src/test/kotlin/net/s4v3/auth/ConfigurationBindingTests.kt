package net.s4v3.auth

import net.s4v3.auth.config.AuthProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@IntegrationTest
class ConfigurationBindingTests(
    @Autowired private val properties: AuthProperties,
    @Autowired private val environment: Environment,
) {

    @Test
    fun `settings bind from configuration`() {
        assertEquals("http://localhost/auth", properties.issuerUri)
        assertEquals("http://ui.test", properties.uiBaseUrl)
        assertEquals(listOf("http://localhost:5173/callback"), properties.spa.redirectUris)
        assertEquals(listOf("http://localhost:5173/"), properties.spa.postLogoutRedirectUris)
        assertFalse(properties.google.configured)
    }

    @Test
    fun `server is mounted under auth on port 9090 with framework forwarded headers`() {
        assertEquals("/auth", environment.getProperty("server.servlet.context-path"))
        assertEquals("9090", environment.getProperty("server.port"))
        assertEquals("framework", environment.getProperty("server.forward-headers-strategy"))
    }
}

class ConfigurationValidationTests {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesOnly::class.java)

    @EnableConfigurationProperties(AuthProperties::class)
    class PropertiesOnly

    private val complete = arrayOf(
        "app.issuer-uri=http://localhost/auth",
        "app.ui-base-url=http://ui.test",
        "app.spa.redirect-uris=http://localhost/cb",
        "app.spa.post-logout-redirect-uris=http://localhost/",
    )

    @Test
    fun `complete settings are accepted and google is optional`() {
        runner.withPropertyValues(*complete).run { ctx: AssertableApplicationContext ->
            assertTrue(ctx.startupFailure == null, ctx.startupFailure?.toString())
        }
    }

    @Test
    fun `a missing issuer stops startup and names the setting`() {
        runner.withPropertyValues(*complete.filterNot { it.startsWith("app.issuer-uri") }.toTypedArray())
            .run { ctx ->
                val failure = ctx.startupFailure
                assertTrue(failure != null)
                val text = generateSequence<Throwable>(failure) { it.cause }.joinToString { it.message.orEmpty() }
                assertTrue("app" in text && ("issuerUri" in text || "issuer-uri" in text), text)
            }
    }
}
