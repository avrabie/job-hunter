package net.s4v3.auth

import net.s4v3.auth.password.PasswordResetToken
import net.s4v3.auth.password.PasswordResetTokenRepository
import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.User
import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@IntegrationTest
class UserRepositoryTests(
    @Autowired private val users: UserRepository,
    @Autowired private val tokens: PasswordResetTokenRepository,
) {

    private fun user(email: String = "${UUID.randomUUID()}@example.com") =
        User(id = UUID.randomUUID(), email = email, password = "{bcrypt}x", firstName = "Ann", lastName = "Lee")

    @Test
    fun `a user with an assigned id is inserted with its roles and read back`() {
        val saved = users.save(user())

        val found = assertNotNull(users.findById(saved.id).orElse(null))
        assertEquals(setOf("USER"), found.roleNames)
        assertEquals("Ann Lee", found.displayName)
        assertNotNull(found.version)
    }

    @Test
    fun `an existing user is updated, not inserted again`() {
        val saved = users.save(user())

        users.save(saved.copy(firstName = "Anna"))

        assertEquals("Anna", users.findById(saved.id).get().firstName)
    }

    @Test
    fun `lookup by email ignores case`() {
        val saved = users.save(user("lookup@example.com"))

        assertEquals(saved.id, users.findByEmail("LOOKUP@Example.COM")?.id)
        assertNull(users.findByEmail("other@example.com"))
    }

    @Test
    fun `a duplicate email differing only in case is rejected`() {
        users.save(user("dup@example.com"))

        assertFailsWith<DataIntegrityViolationException> { users.save(user("DUP@example.com")) }
    }

    @Test
    fun `a duplicate provider subject is rejected`() {
        users.save(user().copy(provider = Provider.GOOGLE, providerId = "g-123"))

        assertFailsWith<DataIntegrityViolationException> {
            users.save(user().copy(provider = Provider.GOOGLE, providerId = "g-123"))
        }
        assertNotNull(users.findByProvider("GOOGLE", "g-123"))
    }

    @Test
    fun `reset tokens are stored and found by hash`() {
        val owner = users.save(user())
        val token = tokens.save(
            PasswordResetToken(UUID.randomUUID(), owner.id, "hash-${owner.id}", Instant.now().plusSeconds(1800)),
        )

        assertEquals(token.id, tokens.findByTokenHash("hash-${owner.id}")?.id)
    }
}
