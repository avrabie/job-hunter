package net.s4v3.auth.user

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

class DuplicateUserException : RuntimeException("An account with this email already exists")

@Service
class UserRegistrationService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    /**
     * Creates an enabled local account with the role USER. Emails are stored trimmed and lowercased; the unique index
     * on lower(email) settles races, so two parallel registrations leave one row and one [DuplicateUserException].
     */
    fun register(email: String, password: String, firstName: String?, lastName: String?): User {
        val normalized = normalizeEmail(email)
        if (users.findByEmail(normalized) != null) throw DuplicateUserException()
        val user = User(
            id = UUID.randomUUID(),
            email = normalized,
            password = passwordEncoder.encode(password),
            firstName = firstName?.trim()?.ifBlank { null },
            lastName = lastName?.trim()?.ifBlank { null },
        )
        try {
            return users.save(user)
        } catch (_: DataIntegrityViolationException) {
            throw DuplicateUserException()
        }
    }

    companion object {
        fun normalizeEmail(email: String): String = email.trim().lowercase()
    }
}
