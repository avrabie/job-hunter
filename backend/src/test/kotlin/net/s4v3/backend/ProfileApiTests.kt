package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ApiTest
class ProfileApiTests(@Autowired private val mvc: MockMvc) {

    private val issuer get() = SharedMockIssuer.instance
    private fun user() = "api-user-" + UUID.randomUUID()
    private fun bearer(sub: String) = "Bearer ${issuer.token(sub)}"

    private val full = javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes().decodeToString()
    private fun body(version: Long, profile: String = full) = """{"version":$version,"profile":$profile}"""

    private fun save(sub: String, json: String) =
        mvc.perform(put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(json))

    private fun read(sub: String) = mvc.perform(get("/api/profile").header("Authorization", bearer(sub)))

    @Test
    fun `anonymous calls get 401`() {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(body(0))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a new user gets the empty profile at version 0`() {
        read(user()).andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.profile.experience.length()").value(0))
            .andExpect(jsonPath("$.profile.languages.length()").value(0))
            .andExpect(jsonPath("$.profile.location").doesNotExist())
    }

    @Test
    fun `save and read back, months as YYYY-MM, current position has a null end`() {
        val sub = user()
        save(sub, body(0)).andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))

        read(sub).andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.profile.experience[0].employer").value("Example AG"))
            .andExpect(jsonPath("$.profile.experience[0].start").value("2021-03"))
            .andExpect(jsonPath("$.profile.experience[0].end").value(null as String?))
            .andExpect(jsonPath("$.profile.experience[1].end").value("2021-02"))
            .andExpect(jsonPath("$.profile.workAuthorisation").value("PERMIT_B"))
            .andExpect(jsonPath("$.profile.preferences.cantons[1]").value("ZG"))
    }

    @Test
    fun `every permit value is accepted and others are not`() {
        val sub = user()
        var version = 0L
        listOf("SWISS_CITIZEN", "PERMIT_C", "PERMIT_B", "PERMIT_L", "PERMIT_G", "NEEDS_SPONSORSHIP").forEach { permit ->
            save(sub, body(version, """{"workAuthorisation":"$permit"}""")).andExpect(status().isOk)
            version++
        }
        save(sub, body(version, """{"workAuthorisation":"CITIZEN"}""")).andExpect(status().isBadRequest)
    }

    @Test
    fun `validation answers 400 with the field path and nothing is stored`() {
        val sub = user()
        val broken = """{"experience":[{"employer":"E","title":" ","start":"2020-05","end":"2019-01"}]}"""

        save(sub, body(0, broken)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("validation_failed"))
            .andExpect(jsonPath("$.fields['experience[0].title']").exists())
            .andExpect(jsonPath("$.fields['experience[0].end']").exists())
        read(sub).andExpect(jsonPath("$.version").value(0))
    }

    @Test
    fun `unknown field and unknown enum value are 400`() {
        val sub = user()
        save(sub, """{"version":0,"profile":{},"extra":1}""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fields.extra").exists())
        save(sub, body(0, """{"workAuthorisation":"CITIZEN"}""")).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fields.workAuthorisation").exists())
        read(sub).andExpect(jsonPath("$.version").value(0))
    }

    @Test
    fun `an error response does not repeat the submitted text`() {
        val marker = "RECOGNISABLE-MARKER-" + "m".repeat(4100)
        val response = save(user(), body(0, """{"summary":"$marker"}""")).andExpect(status().isBadRequest)
            .andReturn().response.contentAsString
        assertFalse("RECOGNISABLE-MARKER" in response)
    }

    @Test
    fun `a stale version gets 409 and changes nothing`() {
        val sub = user()
        save(sub, body(0)).andExpect(status().isOk)

        save(sub, body(0, """{"summary":"stale"}""")).andExpect(status().isConflict)
            .andExpect(content().json("""{"error":"version_conflict"}"""))
        read(sub).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.profile.summary").value("Invented engineer used only in tests."))
    }

    @Test
    fun `a body over 256 KB is 413, before anything is parsed`() {
        val sub = user()
        val huge = body(0, """{"summary":"${"x".repeat(300 * 1024)}"}""")
        save(sub, huge).andExpect(status().isContentTooLarge)
        // Same size without a declared length: the stream is cut off just the same.
        mvc.perform(
            put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content(huge.toByteArray()).header("Content-Length", "-1"),
        ).andExpect(status().isContentTooLarge)
        read(sub).andExpect(jsonPath("$.version").value(0))
    }

    @Test
    fun `Body between the old and the new limit`() {
        val sub = user()
        val achievements = List(20) { "\"" + "w".repeat(600) + "\"" }.joinToString(",")
        val entry = """{"employer":"E","title":"T","start":"2020-01","achievements":[$achievements]}"""
        val padded = body(0, """{"experience":[${List(17) { entry }.joinToString(",")}]}""")
        assertTrue(padded.toByteArray().size in 190_000..210_000, padded.toByteArray().size.toString())

        save(sub, padded).andExpect(status().isOk)
        read(sub).andExpect(jsonPath("$.profile.experience.length()").value(17))
            .andExpect(jsonPath("$.profile.experience[16].achievements.length()").value(20))
    }

    @Test
    fun `a body that is not json is 400, and another content type is 415`() {
        val sub = user()
        save(sub, "{ not json").andExpect(status().isBadRequest).andExpect(jsonPath("$.fields.body").exists())
        mvc.perform(put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.TEXT_PLAIN).content(body(0)))
            .andExpect(status().isUnsupportedMediaType)
    }

    @Test
    fun `user A sees and changes only A's profile`() {
        val a = user()
        val b = user()
        save(a, body(0, """{"summary":"A's summary"}""")).andExpect(status().isOk)
        save(b, body(0, """{"summary":"B's summary"}""")).andExpect(status().isOk)

        read(a).andExpect(jsonPath("$.profile.summary").value("A's summary"))
        // A overwrites with the version A read; B is unaffected.
        save(a, body(1, """{"summary":"A changed"}""")).andExpect(status().isOk)
        read(b).andExpect(jsonPath("$.profile.summary").value("B's summary")).andExpect(jsonPath("$.version").value(1))
        // The only way to name a user is the token: a user id in the query or the body is ignored or rejected.
        mvc.perform(get("/api/profile").param("sub", b).header("Authorization", bearer(a)))
            .andExpect(jsonPath("$.profile.summary").value("A changed"))
        save(a, """{"version":2,"sub":"$b","profile":{}}""").andExpect(status().isBadRequest)
    }
}
