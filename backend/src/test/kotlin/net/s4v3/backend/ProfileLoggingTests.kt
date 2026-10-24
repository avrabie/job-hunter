package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertTrue

@ApiTest
class ProfileLoggingTests(@Autowired private val mvc: MockMvc) {

    private val marker = "PERSONAL-MARKER-7f3a"

    private fun fields(summary: String) = """
        {"location":{"city":"$marker","canton":"ZH"},"summary":"$summary",
         "experience":[{"employer":"$marker","title":"$marker","start":"2020-01","achievements":["$marker"]}],
         "education":[{"institution":"$marker","degree":"$marker"}],
         "skills":[{"name":"$marker"}],"preferences":{"targetRoles":["$marker"]}}
    """.trimIndent()

    @Test
    fun `profile text never reaches the logs on save, validation error or conflict`() {
        val token = "Bearer " + SharedMockIssuer.instance.token("log-user-" + UUID.randomUUID())
        fun send(body: String) =
            mvc.perform(put("/api/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))

        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val capture = LogCapture(root)
        val previousLevel = root.level
        root.level = Level.TRACE
        try {
            send("""{"version":0,"profile":${fields("ok")}}""").andExpect(status().isOk)
            send("""{"version":0,"profile":${fields("stale")}}""").andExpect(status().isConflict)
            send("""{"version":1,"profile":${fields("s".repeat(5000) + marker)}}""").andExpect(status().isBadRequest)
            send("""{"version":1,"profile":{"workAuthorisation":"$marker"}}""").andExpect(status().isBadRequest)
            send("""{"version":1,"profile":$marker""").andExpect(status().isBadRequest)
        } finally {
            capture.close()
            root.level = previousLevel
        }

        // Spring MVC prints the response object at DEBUG, Spring JDBC and the PostgreSQL driver print every bound SQL parameter
        // at TRACE. All are off at the INFO level the application runs at; the rule is never to enable them. They are left out so the
        // test covers everything else (our code, security, Modulith, Hikari, Tomcat) at the most verbose level.
        val frameworkBodyPrinters = listOf("org.springframework.web.", "org.springframework.jdbc.core.", "org.postgresql.")
        val relevant = capture.events().filter { event -> frameworkBodyPrinters.none { event.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { event ->
            event.fullText()
        }
        assertTrue(relevant.isNotEmpty(), "the capture saw no log events at all, so it proves nothing")
        assertTrue(marker !in logged, "profile text appears in the logs")
    }
}
