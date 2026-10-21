package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileConflictException
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.ProfileUpdate
import net.s4v3.backend.profile.StoredProfile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Stores the profile as one JSON document per user. Every statement is scoped by `owner_sub`; the version check is part
 * of the statement itself, so two concurrent saves cannot both succeed.
 */
@Service
internal class JdbcProfileService(private val jdbc: JdbcClient) : ProfileService {

    @Transactional(readOnly = true)
    override fun get(sub: String): StoredProfile =
        jdbc.sql("SELECT version, document::text AS document FROM profile WHERE owner_sub = :sub")
            .param("sub", sub)
            .query { rs, _ -> StoredProfile(rs.getLong("version"), ProfileJson.read(rs.getString("document"))) }
            .optional()
            .orElseGet { StoredProfile(0, Profile()) }

    override fun parse(body: ByteArray): ProfileUpdate = ProfileJson.parseUpdate(body)

    @Transactional
    override fun replace(sub: String, update: ProfileUpdate): StoredProfile {
        ProfileValidator.validate(update)
        val document = ProfileJson.write(update.profile)

        val changed =
            if (update.version == 0L) {
                jdbc.sql(
                    """
                    INSERT INTO profile (owner_sub, document, schema_version, version)
                    VALUES (:sub, CAST(:document AS jsonb), :schema, 1)
                    ON CONFLICT (owner_sub) DO NOTHING
                    """.trimIndent(),
                )
                    .param("sub", sub).param("document", document).param("schema", ProfileJson.SCHEMA_VERSION)
                    .update()
            } else {
                jdbc.sql(
                    """
                    UPDATE profile
                    SET document = CAST(:document AS jsonb), schema_version = :schema, version = version + 1, updated_at = now()
                    WHERE owner_sub = :sub AND version = :version
                    """.trimIndent(),
                )
                    .param("sub", sub).param("document", document).param("schema", ProfileJson.SCHEMA_VERSION)
                    .param("version", update.version)
                    .update()
            }
        if (changed == 0) throw ProfileConflictException()
        return StoredProfile(update.version + 1, update.profile)
    }
}
