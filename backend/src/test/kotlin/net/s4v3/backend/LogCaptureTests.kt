package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class LogCaptureTests {

    @Test
    fun `concurrent writers lose no event while a reader takes snapshots`() {
        val log = LoggerFactory.getLogger(LogCaptureTests::class.java) as Logger
        log.level = Level.INFO
        val writers = 8
        val perWriter = 200
        val pool = Executors.newFixedThreadPool(writers + 1)
        try {
            LogCapture(log).use { capture ->
                val written = (1..writers).map { w ->
                    pool.submit { repeat(perWriter) { i -> log.info("logcapture-$w-$i") } }
                }
                val reader = pool.submit {
                    while (written.any { !it.isDone }) capture.events().forEach { it.fullText() }
                }
                written.forEach { it.get() }
                reader.get()
                assertEquals(writers * perWriter, capture.events().count { it.formattedMessage.startsWith("logcapture-") })
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
