package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.TestClock
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.publiccv.RenderedPage
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class RenderCacheTests {

    private val clock = TestClock()
    private val cache = RenderCache(maxEntries = 3, ttl = Duration.ofMinutes(10), clock = clock)

    private fun key(
        sub: String = "u",
        profile: Long = 1,
        settings: String = "h1",
        template: CvTemplate = CvTemplate.CLASSIC,
        language: CvLanguage = CvLanguage.EN,
    ) = RenderKey(sub, profile, settings, template, language)

    private fun page(text: String) = RenderedPage(text.toByteArray(), text, false)

    @Test
    fun `a stored page is found by the same key`() {
        cache.put(key(), page("a"))
        assertEquals("a", cache.get(key())?.etag)
    }

    @Test
    fun `every part of the key matters`() {
        cache.put(key(), page("a"))
        assertNull(cache.get(key(sub = "v")))
        assertNull(cache.get(key(profile = 2)))
        assertNull(cache.get(key(settings = "h2")))
        assertNull(cache.get(key(template = CvTemplate.MODERN)))
        assertNull(cache.get(key(language = CvLanguage.DE)))
    }

    @Test
    fun `invalidate drops every entry of one owner and no other`() {
        cache.put(key(sub = "u"), page("a"))
        cache.put(key(sub = "u", profile = 2), page("b"))
        cache.put(key(sub = "v"), page("c"))

        cache.invalidate("u")

        assertNull(cache.get(key(sub = "u")))
        assertNull(cache.get(key(sub = "u", profile = 2)))
        assertNotNull(cache.get(key(sub = "v")))
    }

    @Test
    fun `an entry lives ten minutes`() {
        cache.put(key(), page("a"))
        clock.advance(Duration.ofMinutes(10).minusSeconds(1))
        assertNotNull(cache.get(key()))
        clock.advance(Duration.ofSeconds(1))
        assertNull(cache.get(key()))
    }

    @Test
    fun `the oldest entry makes room when the cache is full`() {
        cache.put(key(sub = "1"), page("1"))
        cache.put(key(sub = "2"), page("2"))
        cache.put(key(sub = "3"), page("3"))
        cache.put(key(sub = "4"), page("4"))
        assertEquals(3, cache.size())
        assertNull(cache.get(key(sub = "1")))
        assertNotNull(cache.get(key(sub = "4")))
    }

    @Test
    fun `expired entries go before live ones when the cache is full`() {
        cache.put(key(sub = "1"), page("1"))
        clock.advance(Duration.ofMinutes(11))
        cache.put(key(sub = "2"), page("2"))
        cache.put(key(sub = "3"), page("3"))
        cache.put(key(sub = "4"), page("4"))
        assertEquals(3, cache.size())
        assertNotNull(cache.get(key(sub = "2")))
    }

    @Test
    fun `the default holds at most 500 entries`() {
        val big = RenderCache(clock = clock)
        repeat(600) { big.put(key(sub = "u$it"), page("p")) }
        assertEquals(500, big.size())
    }
}
