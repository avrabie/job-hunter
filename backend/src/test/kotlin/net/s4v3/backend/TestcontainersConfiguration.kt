package net.s4v3.backend

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Duration

/** The one pgvector Postgres of the whole test JVM, started lazily by the first context that needs it. */
object SharedPostgres {
    private val image = DockerImageName.parse("pgvector/pgvector:pg16")

    /** `stop()` does nothing: a closing context must not stop the server the other cached contexts use (Ryuk removes it). */
    private class Shared : PostgreSQLContainer(image) {
        override fun stop() = Unit
    }

    val instance: PostgreSQLContainer by lazy {
        configure(Shared()).also { it.start(); awaitConnection(it) }
    }

    /** A container of its own, for the test that stops its database. */
    fun newContainer(): PostgreSQLContainer = configure(PostgreSQLContainer(image))

    private fun configure(container: PostgreSQLContainer): PostgreSQLContainer = container.apply {
        withReuse(false)
        withUrlParam("connectTimeout", "10")
        // Every cached context keeps its own Hikari pool (10 connections) on this one server.
        withCommand("postgres", "-c", "max_connections=500")
        waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
        withStartupTimeout(Duration.ofMinutes(2))
    }

    /** Docker's port proxy can accept a connection before Postgres answers; prove a real `SELECT 1` works. */
    fun awaitConnection(container: PostgreSQLContainer) {
        val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        var last: Exception? = null
        while (System.nanoTime() < deadline) {
            try {
                DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { c ->
                    c.createStatement().use { it.executeQuery("select 1").use { rs -> check(rs.next()) } }
                }
                return
            } catch (e: Exception) {
                last = e
                Thread.sleep(200)
            }
        }
        throw IllegalStateException("Postgres container never answered select 1", last)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /** `destroyMethod = ""`: closing one context must not stop the container the other contexts use. */
    @Bean(destroyMethod = "")
    @ServiceConnection
    fun pgvectorContainer(): PostgreSQLContainer = SharedPostgres.instance

}
