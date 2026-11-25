package net.s4v3.backend.admin.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/**
 * Read-only queries for administrators. They read the `app_user`, `profile` and `application` tables directly, so
 * nothing here writes and no other module's internal types are used.
 */
@Repository
internal class AdminUserRepository(private val jdbc: JdbcClient) {

    /** All users, or the one with [sub]; newest sign-up first. */
    fun summaries(sub: String?, limit: Int): List<AdminUserSummary> =
        jdbc.sql(
            """
            SELECT u.sub, u.email, u.display_name, u.created_at,
                   p.document -> 'contact' ->> 'fullName' AS full_name,
                   p.owner_sub IS NOT NULL AS has_profile,
                   count(a.id) FILTER (WHERE a.stage = 'INTERESTED') AS saved,
                   count(a.id) FILTER (WHERE a.stage = 'APPLYING') AS applying,
                   count(a.id) FILTER (WHERE a.stage = 'APPLIED') AS applied,
                   count(a.id) FILTER (WHERE a.stage = 'REJECTED') AS rejected,
                   max(a.stage_changed_at) AS last_activity
            FROM app_user u
            LEFT JOIN profile p ON p.owner_sub = u.sub
            LEFT JOIN application a ON a.owner_sub = u.sub
            WHERE CAST(:sub AS text) IS NULL OR u.sub = :sub
            GROUP BY u.sub, p.owner_sub
            ORDER BY u.created_at DESC
            LIMIT :limit
            """.trimIndent(),
        )
            .param("sub", sub)
            .param("limit", limit)
            .query { rs, _ -> summary(rs) }
            .list()

    fun profile(sub: String): AdminProfile? =
        jdbc.sql(
            """
            SELECT p.document -> 'contact' ->> 'fullName' AS full_name,
                   p.document -> 'contact' ->> 'headline' AS headline,
                   p.document -> 'contact' ->> 'phone' AS phone,
                   p.document -> 'contact' ->> 'website' AS website,
                   p.document -> 'contact' ->> 'linkedin' AS linkedin,
                   p.document -> 'location' ->> 'city' AS city,
                   p.document -> 'location' ->> 'canton' AS canton
            FROM profile p
            WHERE p.owner_sub = :sub
            """.trimIndent(),
        )
            .param("sub", sub)
            .query { rs, _ ->
                AdminProfile(
                    fullName = rs.getString("full_name"),
                    headline = rs.getString("headline"),
                    phone = rs.getString("phone"),
                    website = rs.getString("website"),
                    linkedin = rs.getString("linkedin"),
                    city = rs.getString("city"),
                    canton = rs.getString("canton"),
                )
            }
            .optional()
            .orElse(null)

    /** Every tracked job of the user, most recent stage change first. */
    fun applications(sub: String, limit: Int): List<AdminApplication> =
        jdbc.sql(
            """
            SELECT id, stage, title, company, url, source, captured_at, stage_changed_at, applied_on
            FROM application
            WHERE owner_sub = :sub
            ORDER BY stage_changed_at DESC
            LIMIT :limit
            """.trimIndent(),
        )
            .param("sub", sub)
            .param("limit", limit)
            .query { rs, _ ->
                AdminApplication(
                    id = rs.getString("id"),
                    stage = rs.getString("stage"),
                    title = rs.getString("title"),
                    company = rs.getString("company"),
                    url = rs.getString("url"),
                    source = rs.getString("source"),
                    capturedAt = rs.getTimestamp("captured_at").toInstant(),
                    stageChangedAt = rs.getTimestamp("stage_changed_at").toInstant(),
                    appliedOn = rs.getDate("applied_on")?.toLocalDate(),
                )
            }
            .list()

    private fun summary(rs: ResultSet) = AdminUserSummary(
        sub = rs.getString("sub"),
        email = rs.getString("email"),
        displayName = rs.getString("display_name"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        fullName = rs.getString("full_name"),
        hasProfile = rs.getBoolean("has_profile"),
        saved = rs.getInt("saved"),
        applying = rs.getInt("applying"),
        applied = rs.getInt("applied"),
        rejected = rs.getInt("rejected"),
        lastActivity = rs.getTimestamp("last_activity")?.toInstant(),
    )
}
