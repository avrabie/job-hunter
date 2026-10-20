package net.s4v3.auth.password

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PasswordResetTokenRepository : CrudRepository<PasswordResetToken, UUID> {

    fun findByTokenHash(tokenHash: String): PasswordResetToken?

    /** Marks a token used only if it is still unused and unexpired. Atomic, so a token works exactly once. */
    @Modifying
    @Query("UPDATE password_reset_token SET used_at = :now WHERE id = :id AND used_at IS NULL AND expires_at > :now")
    fun consume(@Param("id") id: UUID, @Param("now") now: Instant): Boolean

    /** Invalidates every other open token of a user once one of them was used. */
    @Modifying
    @Query("UPDATE password_reset_token SET used_at = :now WHERE user_id = :userId AND used_at IS NULL")
    fun consumeAll(@Param("userId") userId: UUID, @Param("now") now: Instant): Boolean
}
