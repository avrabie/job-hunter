package net.s4v3.backend.jobimport.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageTextTests {

    private val body = "Ada Example Ltd is hiring a software engineer for its platform team. ".repeat(8)

    @Test
    fun `keeps the article text and drops script, style, nav, footer and form`() {
        val html = """
            <html><head><style>.x{color:red}</style><script>var secret = 1;</script></head><body>
            <nav>Menu Home Jobs</nav>
            <h1>Software Engineer</h1><p>$body</p><ul><li>Kotlin</li><li>Spring</li></ul>
            <form><label>Your email</label><input></form>
            <footer>Cookie settings</footer>
            </body></html>
        """.trimIndent()
        val text = assertNotNull(PageText.extract(html))
        assertTrue("Software Engineer" in text && "Kotlin" in text)
        listOf("secret", "color:red", "Menu Home", "Your email", "Cookie settings").forEach {
            assertFalse(it in text, it)
        }
        assertTrue("Software Engineer\n" in text, "block elements end with a line break")
    }

    @Test
    fun `collapses whitespace`() {
        val text = assertNotNull(PageText.extract("<p>Ada     Example\t\t$body</p>\n\n\n\n<p>second</p>"))
        assertTrue(text.startsWith("Ada Example Ada Example Ltd"))
        assertFalse("\n\n\n" in text)
    }

    @Test
    fun `short or script-only pages are unreadable`() {
        assertNull(PageText.extract("<html><body><script>render()</script><div id=app></div></body></html>"))
        assertNull(PageText.extract("<p>${"x".repeat(PageText.MIN_CHARS - 1)}</p>"))
        assertNotNull(PageText.extract("<p>${"x".repeat(PageText.MIN_CHARS)}</p>"))
    }

    @Test
    fun `caps the text`() {
        val text = assertNotNull(PageText.extract("<p>${"word ".repeat(20_000)}</p>"))
        assertEquals(PageText.MAX_CHARS, text.length)
    }
}
