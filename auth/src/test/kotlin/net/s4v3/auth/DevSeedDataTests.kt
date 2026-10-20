package net.s4v3.auth

import net.s4v3.auth.config.DevSeedData
import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals

@WebIntegrationTest
class DevSeedDataTests(
    @LocalServerPort private val port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
    @Autowired private val context: org.springframework.context.ApplicationContext,
) {
    @Test
    fun `the seed is not part of the application outside the dev profile`() {
        assertEquals(0, context.getBeanNamesForType(DevSeedData::class.java).size)
    }

    @Test
    fun `seeding creates a USER and an ADMIN, can sign in with the tokens carrying the roles, and is repeatable`() {
        val seeder = DevSeedData(users, encoder)
        seeder.run(DefaultApplicationArguments())
        seeder.run(DefaultApplicationArguments())

        val flow = { AuthFlow(port) }
        val user = AuthFlow.claims(flow().tokens("user@example.com", "user")["access_token"].asString())
        val admin = AuthFlow.claims(flow().tokens("admin@example.com", "admin")["access_token"].asString())

        assertEquals(listOf("USER"), user.getStringListClaim("roles"))
        assertEquals(listOf("ADMIN", "USER"), admin.getStringListClaim("roles"))
        assertEquals(1, users.findAll().count { it.email == "admin@example.com" })
    }
}
