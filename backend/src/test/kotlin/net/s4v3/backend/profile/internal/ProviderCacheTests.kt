package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderCacheTests {

    private val minute = TimeUnit.MINUTES.toNanos(1)
    private var clock = 0L

    private fun cache(maxEntries: Int = 1000) = ProviderCache<String>(ttlMinutes = 5, maxEntries = maxEntries) { clock }

    @Test
    fun `a stored answer is a hit before the ttl ends`() {
        val cache = cache()
        cache.put(CodeProvider.GITHUB, "octocat", "listing")

        clock += 5 * minute - 1
        assertEquals("listing", cache.get(CodeProvider.GITHUB, "octocat"))
    }

    @Test
    fun `a stored answer is a miss once the ttl has ended`() {
        val cache = cache()
        cache.put(CodeProvider.GITHUB, "octocat", "listing")

        clock += 5 * minute
        assertNull(cache.get(CodeProvider.GITHUB, "octocat"))
    }

    @Test
    fun `the name is matched without regard to case`() {
        val cache = cache()
        cache.put(CodeProvider.GITHUB, "Octocat", "listing")

        assertEquals("listing", cache.get(CodeProvider.GITHUB, "octocat"))
    }

    @Test
    fun `at the maximum a store drops the expired entries and keeps the live ones`() {
        val cache = cache(maxEntries = 2)
        cache.put(CodeProvider.GITHUB, "expired", "old")
        clock += 4 * minute
        cache.put(CodeProvider.GITHUB, "live", "new")

        clock += 1 * minute
        cache.put(CodeProvider.GITHUB, "third", "newest")

        assertNull(cache.get(CodeProvider.GITHUB, "expired"))
        assertEquals("new", cache.get(CodeProvider.GITHUB, "live"))
        assertEquals("newest", cache.get(CodeProvider.GITHUB, "third"))
    }
}
