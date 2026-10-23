package net.s4v3.backend.files.internal

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Moves each CV of the earlier single-CV feature (`cv_document`) into the file cabinet as a file of kind `CV` and sets it as
 * the owner's master, unless the owner already chose another one. One user per transaction: the new row and the removal of the old one happen together, and the bytes are
 * written before that, so a failure leaves the old row in place and at most an orphan file, which the daily sweep removes.
 * Running it again finds nothing left to move. It never stops the application from starting: a failing user is counted,
 * logged by exception class only and skipped. Only counts are logged, never names, content or ids.
 */
@Component
internal class LegacyCvMover(
    private val jdbc: JdbcTemplate,
    private val storage: FileStorage,
    transactions: PlatformTransactionManager,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(LegacyCvMover::class.java)
    private val tx = TransactionTemplate(transactions)

    override fun run(args: ApplicationArguments) {
        try {
            moveAll()
        } catch (e: Exception) {
            log.warn("Legacy CV move could not run: {}", e.javaClass.simpleName)
        }
    }

    /** Moves every remaining legacy CV. Returns how many moved; the failed ones are counted in the log. */
    fun moveAll(): Int {
        val owners = jdbc.queryForList("select owner_sub from cv_document", String::class.java).filterNotNull()
        if (owners.isEmpty()) return 0
        var moved = 0
        var failed = 0
        for (sub in owners) {
            try {
                moveOne(sub)
                moved++
            } catch (e: Exception) {
                failed++
                log.warn("A legacy CV could not be moved and stays in the old store: {}", e.javaClass.simpleName)
            }
        }
        log.info("Legacy CV move finished: {} moved, {} failed", moved, failed)
        return moved
    }

    private fun moveOne(sub: String) {
        val old = jdbc.query(
            "select file_name, content_type, uploaded_at, content from cv_document where owner_sub = ?",
            { rs, _ ->
                OldCv(
                    fileName = rs.getString("file_name"),
                    contentType = rs.getString("content_type"),
                    uploadedAt = rs.getTimestamp("uploaded_at").toInstant(),
                    content = rs.getBytes("content"),
                )
            },
            sub,
        ).firstOrNull() ?: return

        val id = UUID.randomUUID()
        var stored: Stored? = null
        try {
            val temp = storage.newTempPath()
            try {
                Files.write(temp, old.content)
                stored = storage.moveIn(temp, id)
            } finally {
                Files.deleteIfExists(temp)
            }
            val moved = checkNotNull(stored)
            tx.executeWithoutResult {
                // The legacy CV becomes the master unless the user has chosen one since: an explicit choice wins.
                val hasMaster = jdbc.queryForObject(
                    "select count(*) from user_file where owner_sub = ? and master", Int::class.java, sub,
                )!! > 0
                jdbc.update(
                    """
                    insert into user_file (id, owner_sub, name, kind, content_type, size_bytes, sha256, storage_key, master, uploaded_at)
                    values (?, ?, ?, 'CV', ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    id, sub, old.fileName, old.contentType, moved.sizeBytes, moved.sha256, moved.key,
                    !hasMaster, Timestamp.from(old.uploadedAt),
                )
                jdbc.update("delete from cv_document where owner_sub = ?", sub)
            }
        } catch (e: Exception) {
            // The row was not written, so the bytes would be an orphan: remove them now rather than wait for the sweep.
            stored?.let { storage.delete(it.key) }
            throw e
        }
    }

    private class OldCv(val fileName: String, val contentType: String, val uploadedAt: Instant, val content: ByteArray)
}
