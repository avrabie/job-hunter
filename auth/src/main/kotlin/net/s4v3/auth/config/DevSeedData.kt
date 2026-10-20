package net.s4v3.auth.config

import net.s4v3.auth.user.Roles
import net.s4v3.auth.user.User
import net.s4v3.auth.user.UserRepository
import net.s4v3.auth.user.UserRole
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Local development only (profile `dev`): two accounts with trivial passwords so the login page can be tried
 * without registering. Never active in any other profile; the passwords would not pass registration rules.
 * Sign in with the EMAIL: user@example.com / user (role USER), admin@example.com / admin (roles USER and ADMIN).
 */
@Component
@Profile("dev")
class DevSeedData(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        seed("user@example.com", "user", "Sample", "User", Roles.USER)
        seed("admin@example.com", "admin", "Sample", "Admin", Roles.USER, Roles.ADMIN)
    }

    private fun seed(email: String, password: String, first: String, last: String, vararg roles: String) {
        if (users.findByEmail(email) != null) return
        users.save(
            User(
                id = UUID.randomUUID(),
                email = email,
                password = encoder.encode(password),
                firstName = first,
                lastName = last,
                roles = roles.map { UserRole(it) }.toSet(),
            ),
        )
        log.info("Seeded development account {} (roles {})", email, roles.joinToString())
    }

    private companion object {
        val log = LoggerFactory.getLogger(DevSeedData::class.java)
    }
}
