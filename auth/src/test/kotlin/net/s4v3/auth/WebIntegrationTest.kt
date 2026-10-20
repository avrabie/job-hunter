package net.s4v3.auth

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/** Full application on a random port against a PostgreSQL Testcontainer, for tests that speak real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, RecordingMailConfig::class)
@ActiveProfiles("test")
annotation class WebIntegrationTest
