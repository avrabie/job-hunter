package net.s4v3.auth

import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.User
import net.s4v3.auth.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

/** Creates users directly in the database for tests that are not about registration. */
fun UserRepository.createUser(
    encoder: PasswordEncoder,
    email: String = "${UUID.randomUUID()}@example.com",
    password: String? = "correct-horse",
    firstName: String? = "Ann",
    lastName: String? = "Lee",
    provider: Provider = Provider.LOCAL,
    providerId: String? = null,
): User = save(
    User(
        id = UUID.randomUUID(),
        email = email,
        password = password?.let { encoder.encode(it) },
        firstName = firstName,
        lastName = lastName,
        provider = provider,
        providerId = providerId,
    ),
)
