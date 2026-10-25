package net.s4v3.backend.applications.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** One `letter_skill_analysis` row; [analysis] and [notes] are the stored JSON text. */
internal data class SkillAnalysisRow(val analysis: String, val notes: String, val createdAt: Instant)

/** `letter_skill_analysis`. Every statement is scoped by the owner's `sub`. */
@Repository
internal class SkillAnalysisRepository(private val jdbc: JdbcClient) {

    fun find(sub: String, applicationId: UUID): SkillAnalysisRow? =
        jdbc.sql("SELECT analysis::text AS analysis, notes::text AS notes, created_at FROM letter_skill_analysis WHERE owner_sub = :sub AND application_id = :app")
            .param("sub", sub).param("app", applicationId)
            .query { rs, _ -> SkillAnalysisRow(rs.getString("analysis"), rs.getString("notes"), rs.getTimestamp("created_at").toInstant()) }
            .optional().orElse(null)

    /** Inserts or replaces the row of the caller's application. */
    fun upsert(sub: String, applicationId: UUID, row: SkillAnalysisRow) {
        jdbc.sql(
            """
            INSERT INTO letter_skill_analysis (application_id, owner_sub, analysis, notes, created_at)
            VALUES (:app, :sub, CAST(:analysis AS jsonb), CAST(:notes AS jsonb), :created)
            ON CONFLICT (application_id) DO UPDATE
                SET analysis = EXCLUDED.analysis, notes = EXCLUDED.notes, created_at = EXCLUDED.created_at
                WHERE letter_skill_analysis.owner_sub = EXCLUDED.owner_sub
            """.trimIndent(),
        )
            .param("app", applicationId).param("sub", sub).param("analysis", row.analysis).param("notes", row.notes)
            .param("created", Timestamp.from(row.createdAt)).update()
    }

    fun updateNotes(sub: String, applicationId: UUID, notes: String): Boolean =
        jdbc.sql("UPDATE letter_skill_analysis SET notes = CAST(:notes AS jsonb) WHERE owner_sub = :sub AND application_id = :app")
            .param("notes", notes).param("sub", sub).param("app", applicationId).update() == 1
}
