package net.s4v3.auth

import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IssuerSettingTests {

    @Test
    fun `startup without an issuer fails and the message names the setting`() {
        val failure = assertFailsWith<Exception> {
            SpringApplicationBuilder(AuthApplication::class.java, TestcontainersConfiguration::class.java)
                .web(WebApplicationType.NONE)
                // no "test" profile: no issuer is configured
                .run(
                    "--app.ui-base-url=http://ui.test",
                    "--app.spa.redirect-uris=http://localhost/cb",
                    "--app.spa.post-logout-redirect-uris=http://localhost/",
                )
                .close()
        }

        val text = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.joinToString("\n")
        assertTrue("app.issuer-uri" in text || "app.issuerUri" in text || "issuerUri" in text, text)
    }
}
