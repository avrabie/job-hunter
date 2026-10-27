package net.s4v3.backend.postings.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Configuration
@EnableConfigurationProperties(PublicPostingsSettings::class)
internal class PublicPostingsConfig

/** `jobhunter.postings.public.*`. `perMinute` limits requests per client address in any rolling minute. */
@ConfigurationProperties("jobhunter.postings.public")
internal data class PublicPostingsSettings(val perMinute: Int = 60)

/**
 * Sliding-window limiter per client address for the public postings list: at most `perMinute` calls in any [WINDOW]. In memory
 * (one backend instance). Addresses are keys only and are never logged. The map holds at most [maxClients] addresses: when
 * it is full, the address that has not called for longest is dropped, so a spray of addresses can neither grow the memory
 * nor lock a new client out. Dropping resets that address's count; the limit is a cost guard, not security.
 */
@Component
internal class PublicPostingsLimiter(
    private val settings: PublicPostingsSettings,
    private val clock: Clock = Clock.systemUTC(),
    private val maxClients: Int = MAX_CLIENTS,
) {

    // Access order: the first entry is the address that called least recently.
    private val calls = object : LinkedHashMap<String, ArrayDeque<Instant>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<Instant>>) = size > maxClients
    }

    /** True and counted when the call is allowed; false and not counted otherwise. */
    @Synchronized
    fun tryAcquire(client: String): Boolean {
        val now = clock.instant()
        val queue = calls.getOrPut(client) { ArrayDeque() }
        evict(queue, now)
        if (queue.size >= settings.perMinute) return false
        queue.addLast(now)
        return true
    }

    /** Whole seconds (at least 1) until [client] may call again, for `Retry-After`. */
    @Synchronized
    fun retryAfterSeconds(client: String): Long {
        val now = clock.instant()
        val queue = calls[client] ?: return 1
        evict(queue, now)
        if (queue.isEmpty() || queue.size < settings.perMinute) return 1
        val wait = Duration.between(now, queue.first().plus(WINDOW))
        return maxOf(1L, (wait.toMillis() + 999) / 1000)
    }

    @Synchronized
    fun size(): Int = calls.size

    private fun evict(queue: ArrayDeque<Instant>, now: Instant) {
        // A call exactly one window old no longer counts: the window is (now - WINDOW, now].
        while (queue.isNotEmpty() && !queue.first().isAfter(now.minus(WINDOW))) queue.removeFirst()
    }

    companion object {
        val WINDOW: Duration = Duration.ofMinutes(1)
        const val MAX_CLIENTS = 10_000
    }
}
