package net.s4v3.backend.files.internal

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

/**
 * Removes stored files that no row refers to and that were last written more than a day ago. A file is written before its
 * row exists, and a delete removes the row before the bytes, so a crash can leave an orphan; the age limit keeps a file of an
 * upload in progress safe. Only keys of the storage shape are considered (never `tmp/`). Only counts are logged.
 */
@Component
internal class OrphanSweep(
    private val jdbc: JdbcTemplate,
    private val storage: FileStorage,
) {

    private val log = LoggerFactory.getLogger(OrphanSweep::class.java)

    @Scheduled(cron = "0 30 3 * * *")
    fun runDaily() {
        try {
            log.info("Orphan sweep removed {} file(s)", sweep(Instant.now()))
        } catch (e: Exception) {
            log.warn("Orphan sweep failed: {}", e.javaClass.simpleName)
        }
    }

    /** Removes the orphans older than a day before [now]. Returns how many were removed. */
    fun sweep(now: Instant): Int {
        val referenced = jdbc.queryForList("select storage_key from user_file", String::class.java).filterNotNull().toHashSet()
        val cutoff = now.minus(Duration.ofHours(24))
        var removed = 0
        for (key in storage.keys()) {
            if (key in referenced) continue
            try {
                if (!Files.getLastModifiedTime(storage.open(key)).toInstant().isBefore(cutoff)) continue
                storage.delete(key)
                removed++
            } catch (_: IOException) {
                // Gone or unreadable: nothing to remove here, the next run looks again.
            }
        }
        return removed
    }
}
