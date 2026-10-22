package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ApiTest
class ApiDocsTests(@Autowired private val mvc: MockMvc) {

    private val json = JsonMapper.builder().build()

    private fun document() = json.readTree(mvc.perform(get("/api/openapi.json")).andReturn().response.contentAsString)

    @Test
    fun `the document is served to anyone and is OpenAPI 3`() {
        mvc.perform(get("/api/openapi.json")).andExpect(status().isOk)

        assertTrue(document().path("openapi").asString().startsWith("3."))
    }

    @Test
    fun `the page is served and every file it needs lives under api`() {
        val entry = mvc.perform(get("/api/docs")).andReturn().response
        val pagePath = entry.redirectedUrl ?: "/api/docs"
        val html = mvc.perform(get(pagePath)).andReturn().response.contentAsString
        val urls = Regex("""(?:src|href)="([^"]+\.(?:js|css))"""").findAll(html).map { it.groupValues[1] }.toList()

        assertTrue(urls.isNotEmpty(), html.take(500))
        urls.forEach { url ->
            val path = java.net.URI("http://localhost$pagePath").resolve(url).path
            assertTrue(path.startsWith("/api/"), "$url resolves outside /api: $path")
            assertEquals(200, mvc.perform(get(path)).andReturn().response.status, path)
        }
    }

    @Test
    fun `the page loads the document from api openapi json`() {
        val initializer = mvc.perform(get("/api/swagger-ui/swagger-initializer.js")).andReturn().response.contentAsString

        assertTrue("\"/api/openapi.json\"" in initializer, initializer.take(400))
        assertTrue("petstore" !in initializer)
    }

    @Test
    fun `reads are open and nothing else becomes open`() {
        mvc.perform(get("/api/docs")).andExpect { assertTrue(it.response.status in 200..399) }
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/admin/crawl-schedule")).andExpect(status().isUnauthorized)
        val denied = mvc.perform(post("/api/openapi.json")).andReturn().response.status
        assertTrue(denied == 401 || denied == 405, "POST answered $denied")
    }
}
