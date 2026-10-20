package net.s4v3.auth.user

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

enum class Provider { LOCAL, GOOGLE }

@Table("user_role")
data class UserRole(val role: String)

/**
 * An identity. The UUID [id] is assigned in code and is the token `sub`; [version] is null until first saved,
 * which is how Spring Data JDBC tells an insert from an update for an assigned id.
 * A null [password] means the account has no usable local password.
 */
@Table("users")
data class User(
    @Id val id: UUID,
    val email: String,
    val password: String?,
    val firstName: String?,
    val lastName: String?,
    val enabled: Boolean = true,
    val provider: Provider = Provider.LOCAL,
    val providerId: String? = null,
    @MappedCollection(idColumn = "user_id") val roles: Set<UserRole> = setOf(UserRole(Roles.USER)),
    @Version val version: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    val roleNames: Set<String> get() = roles.mapTo(sortedSetOf()) { it.role }

    /** Full name for the `name` claim, or null when neither part is known. */
    val displayName: String?
        get() = listOfNotNull(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
}

object Roles {
    const val USER = "USER"
    const val ADMIN = "ADMIN"
}
