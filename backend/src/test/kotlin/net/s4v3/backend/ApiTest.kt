package net.s4v3.backend

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration

/** One mock issuer for the whole test JVM, so every [ApiTest] shares a single cached application context. */
object SharedMockIssuer {
    val instance: MockIssuer by lazy { MockIssuer() }
}

class MockIssuerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        TestPropertyValues.of("spring.security.oauth2.resourceserver.jwt.issuer-uri=${SharedMockIssuer.instance.issuer}")
            .applyTo(context)
    }
}

/** HTTP-level test of the secured API: real token validation against [SharedMockIssuer], test endpoints, MockMvc. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestEndpoints::class, TestNotesEndpoints::class)
@ContextConfiguration(initializers = [MockIssuerInitializer::class])
annotation class ApiTest
