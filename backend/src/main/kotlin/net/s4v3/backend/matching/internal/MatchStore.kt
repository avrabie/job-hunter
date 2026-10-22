package net.s4v3.backend.matching.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** One passing match as the owner sees it. [reasons] are fixed codes. */
internal data class MatchItem(
    val postingId: UUID,
    val title: String,
    val company: String,
    val city: String?,
    val canton: String?,
    val url: String,
    val score: Int,
    val reasons: List<String>,
)

/** `posting_match`: one row per (user, posting); every read is scoped by the owner's `sub`. */
@Component
internal class MatchStore(private val jdbc: JdbcClient) {

    fun upsert(sub: String, postingId: UUID, result: MatchResult, profileVersion: Long, at: Instant = Instant.now()) {
        require(result.reasons.all { it.matches(Regex("[A-Z_0-9]+")) }) { "reason codes only" }
        jdbc.sql(
            """
            INSERT INTO posting_match (user_sub, posting_id, passed, score, reasons, profile_version, evaluated_at)
            VALUES (:sub, :posting, :passed, :score, CAST(:reasons AS text[]), :version, :at)
            ON CONFLICT (user_sub, posting_id) DO UPDATE SET passed = EXCLUDED.passed, score = EXCLUDED.score,
                reasons = EXCLUDED.reasons, profile_version = EXCLUDED.profile_version, evaluated_at = EXCLUDED.evaluated_at
            """.trimIndent(),
        )
            .param("sub", sub).param("posting", postingId).param("passed", result.passed).param("score", result.score)
            .param("reasons", "{" + result.reasons.joinToString(",") + "}").param("version", profileVersion)
            .param("at", Timestamp.from(at)).update()
    }

    /** Passing matches of [sub], best score first, ties by newest posting first. */
    fun passing(sub: String, page: Int, size: Int): List<MatchItem> =
        jdbc.sql(
            """
            SELECT p.id, p.title, p.company, p.city, p.canton, m.score, m.reasons,
                   (SELECT s.url FROM posting_source s WHERE s.posting_id = p.id ORDER BY s.is_primary DESC, s.last_seen DESC LIMIT 1) AS url
            FROM posting_match m JOIN posting p ON p.id = m.posting_id
            WHERE m.user_sub = :sub AND m.passed
            ORDER BY m.score DESC, p.first_seen DESC, p.id
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
        )
            .param("sub", sub).param("limit", size).param("offset", page.toLong() * size)
            .query { rs, _ ->
                @Suppress("UNCHECKED_CAST")
                MatchItem(
                    rs.getObject("id", UUID::class.java), rs.getString("title"), rs.getString("company"),
                    rs.getString("city"), rs.getString("canton"), rs.getString("url"), rs.getInt("score"),
                    (rs.getArray("reasons").array as Array<String>).toList(),
                )
            }.list()

    fun count(sub: String): Int =
        jdbc.sql("SELECT count(*) FROM posting_match WHERE user_sub = :sub").param("sub", sub).query(Int::class.java).single()
}
