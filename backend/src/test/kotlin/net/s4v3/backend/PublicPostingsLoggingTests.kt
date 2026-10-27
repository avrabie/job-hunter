package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ApiTest
class PublicPostingsLoggingTests(@Autowired private val mvc: MockMvc) {

    @Test
    fun `a limited request leaves no client address in the logs`() {
        val marker = "198.18.77.66"
        val events = captureAppLogs {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            repeat(60) {
                mvc.perform(get("/api/public/postings?size=1&canton=ZH").header("X-Forwarded-For", "$marker, 10.0.0.1"))
                    .andExpect(status().isOk)
            }
            mvc.perform(get("/api/public/postings?size=1").header("X-Forwarded-For", "$marker, 10.0.0.1"))
                .andExpect(status().isTooManyRequests)
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val logged = events.filter { e -> frameworkBodyPrinters.none { e.loggerName.startsWith(it) } }.joinToString("\n") { it.fullText() }
        assertTrue("capture alive" in logged, "the capture does not work, so it proves nothing")
        assertFalse(marker in logged, "the client address appears in the logs")
    }
}
