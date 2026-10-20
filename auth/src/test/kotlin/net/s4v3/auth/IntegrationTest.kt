package net.s4v3.auth

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/** Full application context against a PostgreSQL Testcontainer, with the `test` profile settings. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
annotation class IntegrationTest
