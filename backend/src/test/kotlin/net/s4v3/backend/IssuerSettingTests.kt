package net.s4v3.backend

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("integration")
class IssuerSettingTests {

    @Test
    fun `startup fails and names the setting when the issuer is missing outside the test profile`() {
        val failure = assertFailsWith<IllegalStateException> {
            SpringApplicationBuilder(BackendApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties("spring.security.oauth2.resourceserver.jwt.issuer-uri=")
                .run()
                .close()
        }

        assertTrue("spring.security.oauth2.resourceserver.jwt.issuer-uri" in failure.message.orEmpty(), failure.message)
    }
}
