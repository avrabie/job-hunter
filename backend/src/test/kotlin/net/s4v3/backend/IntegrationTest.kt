package net.s4v3.backend

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/** Full application context on a throwaway pgvector Postgres (see [TestcontainersConfiguration]). */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
annotation class IntegrationTest
