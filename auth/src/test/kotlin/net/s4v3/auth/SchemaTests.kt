package net.s4v3.auth

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Starts the application against one shared database, as separate (re)starts would. */
class SchemaTests {

    private fun start(): ConfigurableApplicationContext =
        SpringApplicationBuilder(AuthApplication::class.java)
            .web(WebApplicationType.NONE)
            .profiles("test")
            .properties(
                "spring.datasource.url=${postgres.jdbcUrl}",
                "spring.datasource.username=${postgres.username}",
                "spring.datasource.password=${postgres.password}",
            )
            .run()

    private fun JdbcTemplate.count(sql: String, vararg args: Any): Int = queryForObject(sql, Int::class.java, *args)!!

    private fun JdbcTemplate.insertUser(id: UUID, email: String, provider: String = "LOCAL", providerId: String? = null) =
        update(
            "insert into users (id, email, provider, provider_id) values (?, ?, ?, ?)",
            id, email, provider, providerId,
        )

    @Test
    fun `changelog applies to an empty database and a second start applies nothing`() {
        val first = start().use { it.getBean(JdbcTemplate::class.java).count("select count(*) from databasechangelog") }
        assertEquals(3, first)

        start().use { ctx ->
            val jdbc = ctx.getBean(JdbcTemplate::class.java)
            assertEquals(3, jdbc.count("select count(*) from databasechangelog"))
            assertEquals(
                setOf("password_reset_token", "user_role", "users"),
                jdbc.queryForList(
                    "select table_name from information_schema.tables where table_schema = 'public' and table_name in ('users','user_role','password_reset_token')",
                    String::class.java,
                ).toSet(),
            )
        }
    }

    @Test
    fun `emails differing only in case are rejected by the database`() {
        start().use { ctx ->
            val jdbc = ctx.getBean(JdbcTemplate::class.java)
            jdbc.insertUser(UUID.randomUUID(), "Case@Example.com")
            assertFailsWith<DataIntegrityViolationException> { jdbc.insertUser(UUID.randomUUID(), "case@example.COM") }
        }
    }

    @Test
    fun `the same provider subject twice is rejected but local users without a subject are not`() {
        start().use { ctx ->
            val jdbc = ctx.getBean(JdbcTemplate::class.java)
            jdbc.insertUser(UUID.randomUUID(), "g1@example.com", "GOOGLE", "sub-1")
            assertFailsWith<DataIntegrityViolationException> {
                jdbc.insertUser(UUID.randomUUID(), "g2@example.com", "GOOGLE", "sub-1")
            }
            jdbc.insertUser(UUID.randomUUID(), "l1@example.com")
            jdbc.insertUser(UUID.randomUUID(), "l2@example.com")
        }
    }

    @Test
    fun `deleting a user deletes its roles and reset tokens`() {
        start().use { ctx ->
            val jdbc = ctx.getBean(JdbcTemplate::class.java)
            val id = UUID.randomUUID()
            jdbc.insertUser(id, "cascade@example.com")
            jdbc.update("insert into user_role (user_id, role) values (?, 'USER')", id)
            jdbc.update(
                "insert into password_reset_token (id, user_id, token_hash, expires_at) values (?, ?, ?, now())",
                UUID.randomUUID(), id, "hash-$id",
            )

            jdbc.update("delete from users where id = ?", id)

            assertEquals(0, jdbc.count("select count(*) from user_role where user_id = ?", id))
            assertEquals(0, jdbc.count("select count(*) from password_reset_token where user_id = ?", id))
        }
    }

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).also { it.start() }

        @JvmStatic
        @AfterAll
        fun stopDatabase() = postgres.stop()
    }
}
