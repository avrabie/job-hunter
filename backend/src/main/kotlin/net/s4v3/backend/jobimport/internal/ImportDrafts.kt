package net.s4v3.backend.jobimport.internal

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class Draft(
    val id: UUID,
    val url: String,
    val title: String,
    val company: String,
    val location: String?,
    val description: String,
    val language: String,
)

/** Drafts. Every read and delete of a draft is by id and owner, so another user's id is an unknown id. */
@Repository
internal class ImportDrafts(private val jdbc: JdbcTemplate) {

    fun insert(owner: String, draft: Draft, now: Instant) {
        jdbc.update(
            "insert into job_import_draft (id, owner, url, title, company, location, description, language, created_at, expires_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            draft.id, owner, draft.url, draft.title, draft.company, draft.location, draft.description, draft.language,
            Timestamp.from(now), Timestamp.from(now.plus(TTL)),
        )
    }

    /** The owner's draft that has not expired at [now], or null. */
    fun find(owner: String, id: UUID, now: Instant): Draft? = jdbc.query(
        "select id, url, title, company, location, description, language from job_import_draft " +
            "where id = ? and owner = ? and expires_at > ?",
        { rs, _ ->
            Draft(
                rs.getObject("id", UUID::class.java), rs.getString("url"), rs.getString("title"), rs.getString("company"),
                rs.getString("location"), rs.getString("description"), rs.getString("language"),
            )
        },
        id, owner, Timestamp.from(now),
    ).firstOrNull()

    fun delete(owner: String, id: UUID) {
        jdbc.update("delete from job_import_draft where id = ? and owner = ?", id, owner)
    }

    fun purgeExpired(now: Instant): Int = jdbc.update("delete from job_import_draft where expires_at <= ?", Timestamp.from(now))

    fun deleteByOwner(owner: String) {
        jdbc.update("delete from job_import_draft where owner = ?", owner)
    }

    companion object {
        val TTL: Duration = Duration.ofHours(1)
    }
}

/** Removes expired drafts; a confirm never sees them anyway, this only keeps the table small. */
@Component
internal class DraftPurge(private val drafts: ImportDrafts) {

    private val log = LoggerFactory.getLogger(DraftPurge::class.java)

    @Scheduled(cron = "0 15 * * * *")
    fun runHourly() {
        try {
            log.info("Import draft purge removed {} draft(s)", drafts.purgeExpired(Instant.now()))
        } catch (e: Exception) {
            log.warn("Import draft purge failed: {}", e.javaClass.simpleName)
        }
    }
}
