package net.s4v3.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.config.HttpClientProperties
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spring Cloud Gateway ignores settings under a prefix it does not bind, so these read the effective values. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayConfigurationTests {

    @Autowired
    lateinit var httpClient: HttpClientProperties

    @Test
    fun `timeouts and pool limits are bound from application yaml`() {
        assertEquals(5000, httpClient.connectTimeout)
        assertEquals(Duration.ofSeconds(60), httpClient.responseTimeout)
        assertEquals(Duration.ofSeconds(15), httpClient.pool.maxIdleTime)
        assertEquals(Duration.ofSeconds(60), httpClient.pool.maxLifeTime)
        assertEquals(Duration.ofSeconds(15), httpClient.pool.evictionInterval)
    }

    @Test
    fun `every gateway key used in application yaml exists in the configuration metadata of the pinned release`() {
        val known = metadataNames()
        val used = listOf(
            "spring.cloud.gateway.server.webflux.routes",
            "spring.cloud.gateway.server.webflux.default-filters",
            "spring.cloud.gateway.server.webflux.trusted-proxies",
            "spring.cloud.gateway.server.webflux.filter.secure-headers.disable",
            "spring.cloud.gateway.server.webflux.httpclient.connect-timeout",
            "spring.cloud.gateway.server.webflux.httpclient.response-timeout",
            "spring.cloud.gateway.server.webflux.httpclient.pool.max-idle-time",
            "spring.cloud.gateway.server.webflux.httpclient.pool.max-life-time",
            "spring.cloud.gateway.server.webflux.httpclient.pool.eviction-interval",
            "spring.cloud.gateway.server.webflux.x-forwarded.host-append",
            "spring.cloud.gateway.server.webflux.x-forwarded.proto-append",
        )

        val missing = used.filterNot { it in known }
        assertTrue(missing.isEmpty(), "not in the metadata: $missing")
    }

    private fun metadataNames(): Set<String> {
        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        return javaClass.classLoader.getResources("META-INF/spring-configuration-metadata.json").asSequence()
            .flatMap { mapper.readTree(it.openStream())["properties"].asSequence() }
            .map { it["name"].asString() }
            .toSet()
    }
}

/** The pre-5.x prefix is silently ignored; this documents that a value there changes nothing. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.cloud.gateway.httpclient.connect-timeout=1"],
)
class IgnoredPrefixTests {

    @Autowired
    lateinit var httpClient: HttpClientProperties

    @Test
    fun `a setting under the old prefix has no effect, so a moved key would fail the binding test`() {
        assertEquals(5000, httpClient.connectTimeout)
    }
}
