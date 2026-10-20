package net.s4v3.auth.password

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/** A single-use reset token. Only the hash of the token is stored. */
@Table("password_reset_token")
data class PasswordResetToken(
    @Id val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
    @Version val version: Long? = null,
    val createdAt: Instant = Instant.now(),
)
