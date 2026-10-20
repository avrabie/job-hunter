package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@WebIntegrationTest
class RegistrationTests(
    @LocalServerPort private val port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val flow = AuthFlow(port)

    private fun register(email: String, password: String = "correct-horse", name: String? = "Ann") = flow.postJson(
        "${flow.base}/api/register",
        """{"email":"$email","password":"$password"${name?.let { ""","firstName":"$it","lastName":"Lee"""" } ?: ""}}""",
    )

    private fun unique() = "reg-${UUID.randomUUID()}@example.com"

    @Test
    fun `a valid registration creates an enabled account that can sign in`() {
        val email = unique()

        val response = register(email)

        assertEquals(201, response.status, response.body)
        assertEquals(email, response.json()["email"].asString())
        val claims = AuthFlow.claims(AuthFlow(port).tokens(email, "correct-horse")["access_token"].asString())
        assertEquals(listOf("USER"), claims.getStringListClaim("roles"))
        assertEquals("Ann Lee", claims.getStringClaim("name"))
    }

    @Test
    fun `the email is stored lowercase and a case variant is a duplicate`() {
        val local = "Anna-${UUID.randomUUID()}"

        assertEquals(201, register("$local@Example.COM").status)

        assertEquals("${local.lowercase()}@example.com", users.findByEmail("$local@example.com")!!.email)
        assertEquals(409, register("${local.lowercase()}@example.com").status)
    }

    @Test
    fun `a duplicate email is a 409 and creates nothing`() {
        val email = unique()
        register(email)

        val response = register(email)

        assertEquals(409, response.status)
        assertEquals(1, jdbc.queryForObject("select count(*) from users where lower(email) = ?", Int::class.java, email))
    }

    @Test
    fun `invalid input is a 400 naming the offending fields`() {
        val shortPassword = register(unique(), password = "short")
        val badEmail = register("not-an-email")
        val longPassword = register(unique(), password = "x".repeat(73))
        val multibyte = register(unique(), password = "é".repeat(40)) // 40 chars but 80 bytes

        assertEquals(400, shortPassword.status)
        assertTrue("password" in shortPassword.json()["fields"].propertyNames(), shortPassword.body)
        assertEquals(400, badEmail.status)
        assertTrue("email" in badEmail.json()["fields"].propertyNames(), badEmail.body)
        assertEquals(400, longPassword.status)
        assertEquals(400, multibyte.status)
        assertEquals(400, flow.postJson("${flow.base}/api/register", "not json").status)
    }

    @Test
    fun `the password is stored as a hash and never returned`() {
        val email = unique()

        val response = register(email, password = "plain-text-pw")

        val stored = jdbc.queryForObject("select password from users where email = ?", String::class.java, email)!!
        assertNotEquals("plain-text-pw", stored)
        assertTrue(stored.startsWith("{bcrypt}"), stored)
        assertTrue(encoder.matches("plain-text-pw", stored))
        assertTrue("plain-text-pw" !in response.body && "bcrypt" !in response.body)
    }

    @Test
    fun `two parallel registrations of one email leave one account and one conflict`() {
        val email = unique()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        val results = (1..2).map {
            pool.submit<Int> { start.await(); register(email).status }
        }
        start.countDown()
        val statuses = results.map { it.get() }.sorted()
        pool.shutdown()

        assertEquals(listOf(201, 409), statuses)
        assertEquals(1, jdbc.queryForObject("select count(*) from users where lower(email) = ?", Int::class.java, email))
    }
}
