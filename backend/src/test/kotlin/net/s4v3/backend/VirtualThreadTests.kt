package net.s4v3.backend

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.RestClient
import javax.sql.DataSource
import kotlin.test.assertEquals

/** Needs a real servlet container: MockMvc handles requests on the test thread. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, TestEndpoints::class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = [MockIssuerInitializer::class])
class VirtualThreadTests(
    @LocalServerPort private val port: Int,
    @Autowired private val dataSource: DataSource,
) {

    @Test
    fun `a request is handled on a virtual thread`() {
        val body = RestClient.create("http://localhost:$port").get()
            .uri("/api/test/thread")
            .header("Authorization", "Bearer ${SharedMockIssuer.instance.token("user-vt")}")
            .retrieve()
            .body(String::class.java)

        assertEquals("true", body)
    }

    @Test
    fun `the database pool size is set explicitly`() {
        assertEquals(10, (dataSource as HikariDataSource).maximumPoolSize)
    }
}
