package net.s4v3.backend.applications.internal

import net.s4v3.backend.applications.ApplicationStage
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One `application` row. */
internal data class ApplicationRow(
    val id: UUID,
    val postingId: UUID?,
    val stage: ApplicationStage,
    val title: String,
    val company: String,
    val url: String,
    val description: String,
    val jobLanguage: String,
    val capturedAt: Instant,
    val postingLastSeen: Instant?,
    val contentHash: String,
    val notes: String,
    val appliedOn: LocalDate?,
    val stageChangedAt: Instant,
)

/** `application`. Every statement is scoped by the owner's `sub`. */
@Repository
internal class ApplicationRepository(private val jdbc: JdbcClient) {

    /** Serialises the writes of one user (the limit check and the insert) until the transaction ends. */
    fun lockUser(sub: String) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:key))").param("key", "applications:$sub").query().singleRow()
    }

    fun count(sub: String): Int =
        jdbc.sql("SELECT count(*) FROM application WHERE owner_sub = :sub").param("sub", sub).query(Int::class.java).single()

    fun findByPosting(sub: String, postingId: UUID): ApplicationRow? =
        jdbc.sql("$SELECT WHERE owner_sub = :sub AND posting_id = :posting").param("sub", sub).param("posting", postingId)
            .query { rs, _ -> row(rs) }.optional().orElse(null)

    fun find(sub: String, id: UUID): ApplicationRow? =
        jdbc.sql("$SELECT WHERE owner_sub = :sub AND id = :id").param("sub", sub).param("id", id)
            .query { rs, _ -> row(rs) }.optional().orElse(null)

    /** Newest stage change first. */
    fun list(sub: String, stage: ApplicationStage?): List<ApplicationRow> {
        val filter = if (stage == null) "" else " AND stage = :stage"
        var statement = jdbc.sql("$SELECT WHERE owner_sub = :sub$filter ORDER BY stage_changed_at DESC, id").param("sub", sub)
        if (stage != null) statement = statement.param("stage", stage.name)
        return statement.query { rs, _ -> row(rs) }.list()
    }

    fun insert(sub: String, row: ApplicationRow, now: Instant) {
        jdbc.sql(
            """
            INSERT INTO application (id, owner_sub, posting_id, stage, title, company, url, description, job_language,
                captured_at, posting_last_seen, content_hash, notes, applied_on, stage_changed_at, created_at, updated_at)
            VALUES (:id, :sub, :posting, :stage, :title, :company, :url, :description, :language,
                :captured, :lastSeen, :hash, :notes, :appliedOn, :changed, :now, :now)
            """.trimIndent(),
        )
            .param("id", row.id).param("sub", sub).param("posting", row.postingId).param("stage", row.stage.name)
            .param("title", row.title).param("company", row.company).param("url", row.url)
            .param("description", row.description).param("language", row.jobLanguage)
            .param("captured", ts(row.capturedAt)).param("lastSeen", row.postingLastSeen?.let(::ts), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
            .param("hash", row.contentHash).param("notes", row.notes)
            .param("appliedOn", row.appliedOn?.let { java.sql.Date.valueOf(it) }, java.sql.Types.DATE)
            .param("changed", ts(row.stageChangedAt)).param("now", ts(now))
            .update()
    }

    /** Writes the changeable fields of [row]; false when the owner has no such application. */
    fun save(sub: String, row: ApplicationRow, now: Instant): Boolean =
        jdbc.sql(
            """
            UPDATE application SET stage = :stage, notes = :notes, applied_on = :appliedOn,
                stage_changed_at = :changed, updated_at = :now
            WHERE owner_sub = :sub AND id = :id
            """.trimIndent(),
        )
            .param("stage", row.stage.name).param("notes", row.notes)
            .param("appliedOn", row.appliedOn?.let { java.sql.Date.valueOf(it) }, java.sql.Types.DATE)
            .param("changed", ts(row.stageChangedAt)).param("now", ts(now)).param("sub", sub).param("id", row.id)
            .update() == 1

    fun delete(sub: String, id: UUID): Boolean =
        jdbc.sql("DELETE FROM application WHERE owner_sub = :sub AND id = :id").param("sub", sub).param("id", id).update() == 1

    fun letterCount(sub: String, id: UUID): Int =
        jdbc.sql("SELECT count(*) FROM motivation_letter WHERE owner_sub = :sub AND application_id = :id")
            .param("sub", sub).param("id", id).query(Int::class.java).single()

    fun byPosting(sub: String, postingIds: Collection<UUID>): List<Pair<UUID, Pair<UUID, ApplicationStage>>> {
        if (postingIds.isEmpty()) return emptyList()
        return jdbc.sql("SELECT id, posting_id, stage FROM application WHERE owner_sub = :sub AND posting_id IN (:ids)")
            .param("sub", sub).param("ids", postingIds.toList())
            .query { rs, _ ->
                rs.getObject("posting_id", UUID::class.java) to
                    (rs.getObject("id", UUID::class.java) to ApplicationStage.valueOf(rs.getString("stage")))
            }.list()
    }

    private fun ts(instant: Instant) = Timestamp.from(instant)

    private fun row(rs: ResultSet) = ApplicationRow(
        id = rs.getObject("id", UUID::class.java),
        postingId = rs.getObject("posting_id", UUID::class.java),
        stage = ApplicationStage.valueOf(rs.getString("stage")),
        title = rs.getString("title"),
        company = rs.getString("company"),
        url = rs.getString("url"),
        description = rs.getString("description"),
        jobLanguage = rs.getString("job_language"),
        capturedAt = rs.getTimestamp("captured_at").toInstant(),
        postingLastSeen = rs.getTimestamp("posting_last_seen")?.toInstant(),
        contentHash = rs.getString("content_hash"),
        notes = rs.getString("notes"),
        appliedOn = rs.getDate("applied_on")?.toLocalDate(),
        stageChangedAt = rs.getTimestamp("stage_changed_at").toInstant(),
    )

    private companion object {
        const val SELECT = """
            SELECT id, posting_id, stage, title, company, url, description, job_language, captured_at, posting_last_seen,
                   content_hash, notes, applied_on, stage_changed_at
            FROM application
        """
    }
}
