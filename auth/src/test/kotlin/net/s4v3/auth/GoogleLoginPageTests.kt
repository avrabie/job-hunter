package net.s4v3.auth

import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@WebIntegrationTest
class GoogleNotConfiguredTests(@LocalServerPort port: Int) {
    private val flow = AuthFlow(port)

    @Test
    fun `without credentials the login page has only the password form and the server runs`() {
        val page = flow.get("${flow.base}/login")

        assertEquals(200, page.status)
        assertTrue("name=\"password\"" in page.body)
        assertFalse("oauth2/authorization/google" in page.body, page.body)
    }
}

@WebIntegrationTest
@TestPropertySource(properties = ["app.google.client-id=some-id", "app.google.client-secret=some-secret"])
class GoogleConfiguredTests(@LocalServerPort port: Int) {
    private val flow = AuthFlow(port)

    @Test
    fun `with credentials the login page offers Google next to the password form`() {
        val page = flow.get("${flow.base}/login")

        assertEquals(200, page.status)
        assertTrue("name=\"password\"" in page.body)
        assertTrue("/auth/oauth2/authorization/google" in page.body, page.body)
    }

    @Test
    fun `the Google button redirects to Google with the redirect URI under the public path`() {
        val redirect = flow.get("${flow.base}/oauth2/authorization/google")

        assertEquals(302, redirect.status)
        val location = redirect.location!!
        assertTrue(location.startsWith("https://accounts.google.com/"), location)
        assertEquals("some-id", AuthFlow.queryOf(location)["client_id"])
        assertEquals("${flow.base}/login/oauth2/code/google", AuthFlow.queryOf(location)["redirect_uri"])
    }
}
