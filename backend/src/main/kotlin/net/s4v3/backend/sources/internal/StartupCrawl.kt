package net.s4v3.backend.sources.internal

import net.s4v3.backend.sources.AtsCrawl
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Stopgap until a scheduler exists: starts one crawl after startup, only when
 * `jobhunter.sources.greenhouse.run-on-startup` is true. It runs on its own thread so readiness is not held back.
 */
@Component
@ConditionalOnProperty("jobhunter.sources.greenhouse.run-on-startup", havingValue = "true")
internal class StartupCrawl(private val crawl: AtsCrawl) {

    private val log = LoggerFactory.getLogger(StartupCrawl::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        Thread.ofVirtual().name("startup-crawl").start {
            try {
                crawl.run()
            } catch (e: Exception) {
                log.warn("Startup crawl failed: {}", e.javaClass.simpleName)
            }
        }
    }
}
