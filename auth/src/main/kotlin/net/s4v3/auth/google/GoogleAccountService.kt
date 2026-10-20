package net.s4v3.auth.google

import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.User
import net.s4v3.auth.user.UserRegistrationService
import net.s4v3.auth.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

class GoogleSignInRejectedException(message: String) : RuntimeException(message)

/**
 * Maps a verified Google identity to a local account:
 * 1. the Google subject is already known: refresh the names and use that account;
 * 2. the email belongs to a local account: link it and DISCARD its password, so whoever registered that email
 *    first without owning it loses access (the real owner can set a password again through password reset);
 * 3. otherwise create a new account without a password.
 * The caller must already have checked that Google verified the email.
 */
@Service
class GoogleAccountService(
    private val users: UserRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    /** Runs the mapping in a transaction; if a parallel first sign-in won the race, runs it once more. */
    fun signIn(googleSubject: String, email: String, givenName: String?, familyName: String?): User {
        val normalized = UserRegistrationService.normalizeEmail(email)
        fun attempt() = transaction.execute { map(googleSubject, normalized, givenName, familyName) }!!
        return try {
            attempt()
        } catch (_: DataIntegrityViolationException) {
            attempt()
        }
    }

    private fun map(googleSubject: String, email: String, givenName: String?, familyName: String?): User {
        val known = users.findByProvider(Provider.GOOGLE.name, googleSubject)
        if (known != null) return enabled(refreshNames(known, givenName, familyName))

        val byEmail = users.findByEmail(email)
        if (byEmail != null) {
            if (byEmail.provider == Provider.GOOGLE) {
                // Same subject: a parallel sign-in created it between our two lookups. Another subject: never hand over.
                if (byEmail.providerId == googleSubject) return enabled(refreshNames(byEmail, givenName, familyName))
                throw GoogleSignInRejectedException("This email is linked to another Google account")
            }
            return enabled(
                users.save(
                    byEmail.copy(
                        provider = Provider.GOOGLE,
                        providerId = googleSubject,
                        password = null,
                        firstName = byEmail.firstName ?: givenName,
                        lastName = byEmail.lastName ?: familyName,
                        updatedAt = Instant.now(),
                    ),
                ),
            )
        }

        return users.save(
            User(
                id = UUID.randomUUID(),
                email = email,
                password = null,
                firstName = givenName,
                lastName = familyName,
                provider = Provider.GOOGLE,
                providerId = googleSubject,
            ),
        )
    }

    private fun refreshNames(user: User, givenName: String?, familyName: String?): User {
        val first = givenName?.takeIf { it.isNotBlank() } ?: user.firstName
        val last = familyName?.takeIf { it.isNotBlank() } ?: user.lastName
        if (first == user.firstName && last == user.lastName) return user
        return users.save(user.copy(firstName = first, lastName = last, updatedAt = Instant.now()))
    }

    private fun enabled(user: User): User {
        if (!user.enabled) throw GoogleSignInRejectedException("This account is disabled")
        return user
    }
}
