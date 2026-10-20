package net.s4v3.backend.accounts.internal

import net.s4v3.backend.accounts.UserRegistered
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Creates the user record for a `sub` the first time it is seen. */
@Service
internal class UserProvisioner(
    private val jdbc: JdbcClient,
    private val events: ApplicationEventPublisher,
) {

    /**
     * Inserts the user if absent. The insert is atomic, so concurrent first requests for the same `sub`
     * create one row; only the request that actually inserted publishes [UserRegistered].
     *
     * @return true if this call created the user
     */
    @Transactional
    fun ensureExists(sub: String, email: String?, displayName: String?): Boolean {
        val inserted = jdbc.sql(
            """
            INSERT INTO app_user (sub, email, display_name)
            VALUES (:sub, :email, :name)
            ON CONFLICT (sub) DO NOTHING
            """.trimIndent(),
        )
            .param("sub", sub)
            .param("email", email)
            .param("name", displayName)
            .update()

        if (inserted == 0) return false

        events.publishEvent(UserRegistered(userId = sub, registeredAt = Instant.now()))
        return true
    }
}
