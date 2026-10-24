package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Keeps provider answers for [ttlMinutes] minutes per provider and name (lower-cased). When the cache holds [maxEntries]
 * or more entries, a store first drops the expired ones. Callers store only successful answers.
 */
internal class ProviderCache<V : Any>(
    private val ttlMinutes: Long,
    private val maxEntries: Int,
    private val now: () -> Long = System::nanoTime,
) {

    private data class Key(val provider: CodeProvider, val name: String)

    private class Entry<T>(val value: T, val expiresAt: Long)

    private val entries = ConcurrentHashMap<Key, Entry<V>>()

    fun get(provider: CodeProvider, name: String): V? {
        val key = Key(provider, name.lowercase())
        val hit = entries[key] ?: return null
        if (now() < hit.expiresAt) return hit.value
        entries.remove(key, hit)
        return null
    }

    fun put(provider: CodeProvider, name: String, value: V) {
        if (entries.size >= maxEntries) entries.entries.removeIf { now() >= it.value.expiresAt }
        entries[Key(provider, name.lowercase())] = Entry(value, now() + TimeUnit.MINUTES.toNanos(ttlMinutes))
    }
}
