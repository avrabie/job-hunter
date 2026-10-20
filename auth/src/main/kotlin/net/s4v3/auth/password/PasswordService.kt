package net.s4v3.auth.password

import net.s4v3.auth.config.AuthProperties
import net.s4v3.auth.mail.AfterCommit
import net.s4v3.auth.mail.MailSender
import net.s4v3.auth.mail.Mails
import net.s4v3.auth.user.UserRegistrationService
import net.s4v3.auth.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

class InvalidResetTokenException : RuntimeException("The reset link is invalid or has expired")
class WrongPasswordException : RuntimeException("The current password is incorrect")
class SamePasswordException : RuntimeException("The new password must differ from the current one")
class NoPasswordException : RuntimeException("This account has no password to change")

@Service
class PasswordService(
    private val users: UserRepository,
    private val tokens: PasswordResetTokenRepository,
    private val encoder: PasswordEncoder,
    private val mailSender: MailSender,
    private val mails: Mails,
    private val properties: AuthProperties,
) {
    private val random = SecureRandom()

    /**
     * Starts a reset for an email. Always returns normally, whether or not the account exists, so the caller cannot
     * tell. For an enabled account it stores the hash of a fresh token and hands the link to the mail sender after
     * commit. Google-created accounts are included: the reset is how they add a password.
     */
    @Transactional
    fun requestReset(email: String) {
        val user = users.findByEmail(UserRegistrationService.normalizeEmail(email))?.takeIf { it.enabled } ?: return
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))
        tokens.save(
            PasswordResetToken(
                id = UUID.randomUUID(),
                userId = user.id,
                tokenHash = hash(token),
                expiresAt = Instant.now().plus(TOKEN_TTL),
            ),
        )
        val link = "${properties.uiBaseUrl.trimEnd('/')}/reset-password?token=$token"
        AfterCommit.run { mailSender.send(mails.passwordReset(user.email, link)) }
    }

    /** Sets a new password for a valid token. Unknown, used and expired tokens all fail the same way. */
    @Transactional
    fun confirmReset(token: String, newPassword: String) {
        val stored = tokens.findByTokenHash(hash(token)) ?: throw InvalidResetTokenException()
        val now = Instant.now()
        if (!tokens.consume(stored.id, now)) throw InvalidResetTokenException()
        val user = users.findById(stored.userId).orElse(null)?.takeIf { it.enabled } ?: throw InvalidResetTokenException()
        tokens.consumeAll(user.id, now)
        users.save(user.copy(password = encoder.encode(newPassword), updatedAt = now))
        AfterCommit.run { mailSender.send(mails.passwordChanged(user.email)) }
    }

    /** Changes the password of [userId] (always the caller's own id, taken from the token) after checking the current one. */
    @Transactional
    fun change(userId: UUID, currentPassword: String, newPassword: String) {
        val user = users.findById(userId).orElseThrow { WrongPasswordException() }
        val stored = user.password ?: throw NoPasswordException()
        if (!encoder.matches(currentPassword, stored)) throw WrongPasswordException()
        if (currentPassword == newPassword) throw SamePasswordException()
        users.save(user.copy(password = encoder.encode(newPassword), updatedAt = Instant.now()))
        AfterCommit.run { mailSender.send(mails.passwordChanged(user.email)) }
    }

    private fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))

    companion object {
        const val TOKEN_BYTES = 32
        val TOKEN_TTL: Duration = Duration.ofMinutes(30)
    }
}
