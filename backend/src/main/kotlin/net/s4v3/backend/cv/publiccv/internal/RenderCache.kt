package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.publiccv.RenderedPage
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** What a rendered page depends on. The settings hash covers every setting that shapes the page and the profile version moves on each edit, so an older page is never found; an owner's entries are also dropped on save, remove, takedown and unlock. */
internal data class RenderKey(
    val sub: String,
    val profileVersion: Long,
    val settingsHash: String,
    val template: CvTemplate,
    val language: CvLanguage,
)

/** Rendered pages in memory: at most [maxEntries], each for [ttl]. The oldest entry makes room. Holds personal data: never logged. */
internal class RenderCache(
    private val maxEntries: Int = 500,
    private val ttl: Duration = Duration.ofMinutes(10),
    private val clock: Clock = Clock.systemUTC(),
) {

    private class Entry(val page: RenderedPage, val expiresAt: Instant)

    // Insertion order: the first entry is the oldest.
    private val entries = LinkedHashMap<RenderKey, Entry>()

    @Synchronized
    fun get(key: RenderKey): RenderedPage? {
        val entry = entries[key] ?: return null
        if (!clock.instant().isBefore(entry.expiresAt)) {
            entries.remove(key)
            return null
        }
        return entry.page
    }

    @Synchronized
    fun put(key: RenderKey, page: RenderedPage) {
        val now = clock.instant()
        entries.remove(key)
        if (entries.size >= maxEntries) entries.values.removeIf { !now.isBefore(it.expiresAt) }
        while (entries.size >= maxEntries) entries.remove(entries.keys.first())
        entries[key] = Entry(page, now.plus(ttl))
    }

    @Synchronized
    fun invalidate(sub: String) {
        entries.keys.removeIf { it.sub == sub }
    }

    @Synchronized
    fun size(): Int = entries.size
}
