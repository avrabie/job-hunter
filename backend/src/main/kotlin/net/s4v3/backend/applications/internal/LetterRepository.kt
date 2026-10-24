package net.s4v3.backend.applications.internal

import net.s4v3.backend.applications.LetterHow
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

/** One `motivation_letter` row; [selection] and [comments] are the stored JSON text. */
internal data class LetterRow(
    val version: Int,
    val how: LetterHow,
    val language: String,
    val text: String,
    val selection: String?,
    val instructions: String?,
    val comments: String?,
    val basedOnVersion: Int?,
    val createdAt: Instant,
)

/** `motivation_letter`. Every statement is scoped by the owner's `sub`. */
@Repository
internal class LetterRepository(private val jdbc: JdbcClient) {

    fun lock(sub: String, applicationId: UUID) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:key))").param("key", "letters:$sub:$applicationId").query().singleRow()
    }

    fun count(sub: String, applicationId: UUID): Int =
        jdbc.sql("SELECT count(*) FROM motivation_letter WHERE owner_sub = :sub AND application_id = :app")
            .param("sub", sub).param("app", applicationId).query(Int::class.java).single()

    /** 0 when there is no letter yet. */
    fun latestVersion(sub: String, applicationId: UUID): Int =
        jdbc.sql("SELECT coalesce(max(version), 0) FROM motivation_letter WHERE owner_sub = :sub AND application_id = :app")
            .param("sub", sub).param("app", applicationId).query(Int::class.java).single()

    fun list(sub: String, applicationId: UUID): List<LetterRow> =
        jdbc.sql("$SELECT WHERE owner_sub = :sub AND application_id = :app ORDER BY version")
            .param("sub", sub).param("app", applicationId).query { rs, _ -> row(rs) }.list()

    fun find(sub: String, applicationId: UUID, version: Int): LetterRow? =
        jdbc.sql("$SELECT WHERE owner_sub = :sub AND application_id = :app AND version = :version")
            .param("sub", sub).param("app", applicationId).param("version", version)
            .query { rs, _ -> row(rs) }.optional().orElse(null)

    fun insert(sub: String, applicationId: UUID, row: LetterRow) {
        jdbc.sql(
            """
            INSERT INTO motivation_letter (id, application_id, owner_sub, version, language, text, how, selection, instructions,
                comments, based_on_version, created_at)
            VALUES (:id, :app, :sub, :version, :language, :text, :how, CAST(:selection AS jsonb), :instructions,
                CAST(:comments AS jsonb), :based, :created)
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID()).param("app", applicationId).param("sub", sub).param("version", row.version)
            .param("language", row.language).param("text", row.text).param("how", row.how.name)
            .param("selection", row.selection, Types.VARCHAR).param("instructions", row.instructions, Types.VARCHAR)
            .param("comments", row.comments, Types.VARCHAR).param("based", row.basedOnVersion, Types.INTEGER)
            .param("created", Timestamp.from(row.createdAt))
            .update()
    }

    fun delete(sub: String, applicationId: UUID, version: Int): Boolean =
        jdbc.sql("DELETE FROM motivation_letter WHERE owner_sub = :sub AND application_id = :app AND version = :version")
            .param("sub", sub).param("app", applicationId).param("version", version).update() == 1

    private fun row(rs: ResultSet) = LetterRow(
        version = rs.getInt("version"),
        how = LetterHow.valueOf(rs.getString("how")),
        language = rs.getString("language"),
        text = rs.getString("text"),
        selection = rs.getString("selection"),
        instructions = rs.getString("instructions"),
        comments = rs.getString("comments"),
        basedOnVersion = rs.getObject("based_on_version") as Int?,
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )

    private companion object {
        const val SELECT = "SELECT version, how, language, text, selection::text AS selection, instructions, comments::text AS comments, based_on_version, created_at FROM motivation_letter"
    }
}
