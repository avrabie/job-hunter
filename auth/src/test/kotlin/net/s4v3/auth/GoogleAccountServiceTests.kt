package net.s4v3.auth

import net.s4v3.auth.google.GoogleAccountService
import net.s4v3.auth.google.GoogleSignInRejectedException
import net.s4v3.auth.user.Provider
import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class GoogleAccountServiceTests(
    @Autowired private val accounts: GoogleAccountService,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private fun sub() = "g-${UUID.randomUUID()}"
    private fun email() = "google-${UUID.randomUUID()}@example.com"

    @Test
    fun `an unknown Google user gets a new passwordless account with role USER`() {
        val sub = sub()
        val email = email()

        val user = accounts.signIn(sub, email.uppercase(), "Gina", "Google")

        val stored = users.findById(user.id).get()
        assertEquals(email, stored.email)
        assertEquals(Provider.GOOGLE, stored.provider)
        assertEquals(sub, stored.providerId)
        assertNull(stored.password)
        assertEquals(setOf("USER"), stored.roleNames)
        assertEquals("Gina Google", stored.displayName)
        assertTrue(stored.enabled)
    }

    @Test
    fun `the same Google user again uses the same account and refreshes the names`() {
        val sub = sub()
        val first = accounts.signIn(sub, email(), "Gina", "Google")

        val second = accounts.signIn(sub, first.email, "Regina", "Googler")

        assertEquals(first.id, second.id)
        assertEquals("Regina Googler", users.findById(first.id).get().displayName)
        assertEquals(1, jdbc.queryForObject("select count(*) from users where provider_id = ?", Int::class.java, sub))
    }

    @Test
    fun `a local account with the same email is linked, keeps its id and loses its password`() {
        val local = users.createUser(encoder, password = "squatter-password")
        val sub = sub()

        val linked = accounts.signIn(sub, local.email, "Real", "Owner")

        assertEquals(local.id, linked.id)
        val stored = users.findById(local.id).get()
        assertEquals(Provider.GOOGLE, stored.provider)
        assertEquals(sub, stored.providerId)
        assertNull(stored.password, "the local password is discarded")
        assertEquals("Ann Lee", stored.displayName, "existing names are kept")
    }

    @Test
    fun `an email tied to another Google account is not handed over`() {
        val first = accounts.signIn(sub(), email(), "A", "B")

        assertFailsWith<GoogleSignInRejectedException> { accounts.signIn(sub(), first.email, "C", "D") }
    }

    @Test
    fun `a disabled account cannot sign in with Google`() {
        val user = accounts.signIn(sub(), email(), "A", "B")
        users.save(user.copy(enabled = false))

        assertFailsWith<GoogleSignInRejectedException> { accounts.signIn(user.providerId!!, user.email, "A", "B") }
    }

    @Test
    fun `parallel first sign-ins of one Google user create exactly one account`() {
        val sub = sub()
        val email = email()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)

        val results = (1..8).map { pool.submit<java.util.UUID> { start.await(); accounts.signIn(sub, email, "Gina", "Google").id } }
        start.countDown()
        val ids = results.map { it.get() }.toSet()
        pool.shutdown()

        assertEquals(1, ids.size)
        assertEquals(1, jdbc.queryForObject("select count(*) from users where lower(email) = ?", Int::class.java, email))
    }
}
