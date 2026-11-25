package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ApiTest
internal class AdminUsersApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val issuer get() = SharedMockIssuer.instance
    private fun admin() = "Bearer " + issuer.token("admin-" + UUID.randomUUID(), roles = listOf("USER", "ADMIN"))
    private fun user() = "Bearer " + issuer.token("user-" + UUID.randomUUID())

    /** One user with a profile and three tracked jobs: one saved, two applying, one applied. */
    private fun seedUser(sub: String): String {
        jdbc.update(
            "insert into app_user (sub, email, display_name) values (?, ?, ?)",
            sub, "$sub@example.org", "Ada",
        )
        jdbc.update(
            "insert into profile (owner_sub, document, schema_version, version) values (?, ?::jsonb, 1, 0)",
            sub,
            """{"contact":{"fullName":"Ada Example","headline":"Kotlin engineer","phone":"+41 79 000 00 00"},""" +
                """"location":{"city":"Zürich","canton":"ZH"}}""",
        )
        val stages = listOf("INTERESTED", "APPLYING", "APPLYING", "APPLIED")
        stages.forEachIndexed { index, stage ->
            jdbc.update(
                """
                insert into application (id, owner_sub, stage, title, company, url, description, job_language,
                    captured_at, content_hash, stage_changed_at, created_at, updated_at, source, applied_on)
                values (?, ?, ?, ?, ?, ?, ?, 'en', now(), 'hash', now(), now(), now(), 'IMPORT', ?)
                """.trimIndent(),
                UUID.randomUUID(), sub, stage, "Job $index", "Acme", "https://example.org/job/$index",
                "SECRET DESCRIPTION TEXT", if (stage == "APPLIED") LocalDate.of(2026, 11, 1) else null,
            )
        }
        return sub
    }

    @Test
    fun `no token gets 401 and a user without the admin role gets 403`() {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/admin/users").header("Authorization", user())).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/users/x").header("Authorization", user())).andExpect(status().isForbidden)
    }

    @Test
    fun `the list shows the user with counts per stage and the profile name`() {
        val sub = seedUser("list-" + UUID.randomUUID())

        val body = mvc.perform(get("/api/admin/users?limit=500").header("Authorization", admin()))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val entry = body.substringAfter("\"sub\":\"$sub\"").substringBefore("}")
        assertTrue("\"saved\":1" in entry, entry)
        assertTrue("\"applying\":2" in entry, entry)
        assertTrue("\"applied\":1" in entry, entry)
        assertTrue("\"rejected\":0" in entry, entry)
        assertTrue("\"fullName\":\"Ada Example\"" in entry, entry)
        assertTrue("\"hasProfile\":true" in entry, entry)
    }

    @Test
    fun `the detail shows the contact fields and every tracked job, never a description`() {
        val sub = seedUser("detail-" + UUID.randomUUID())

        val response = mvc.perform(get("/api/admin/users/$sub").header("Authorization", admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.email").value("$sub@example.org"))
            .andExpect(jsonPath("$.profile.headline").value("Kotlin engineer"))
            .andExpect(jsonPath("$.profile.city").value("Zürich"))
            .andExpect(jsonPath("$.applications.length()").value(4))
            .andReturn().response.contentAsString

        assertFalse("SECRET DESCRIPTION TEXT" in response, "descriptions must not be sent")
        assertFalse("\"description\"" in response, "descriptions must not be sent")
        assertTrue("\"stage\":\"APPLIED\"" in response, response)
        assertTrue("\"appliedOn\":\"2026-11-01\"" in response, response)
    }

    @Test
    fun `an unknown user answers 404`() {
        mvc.perform(get("/api/admin/users/does-not-exist-" + UUID.randomUUID()).header("Authorization", admin()))
            .andExpect(status().isNotFound)
    }
}
