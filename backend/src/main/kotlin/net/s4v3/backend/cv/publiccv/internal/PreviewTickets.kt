package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.cv.publiccv.PublicCvSettings
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The owner and settings a preview ticket was made for. */
internal data class PreviewBinding(val sub: String, val settings: PublicCvSettings)

/**
 * One-use preview tickets (the idea of `DownloadTickets`): 128 random bits as hex, valid for 60 seconds, bound to the owner
 * and the posted settings, used up by the first attempt. In memory only. A ticket is a credential and is never logged.
 */
@Component
internal class PreviewTickets(
    private val clock: Clock = Clock.systemUTC(),
) {

    private class Ticket(val binding: PreviewBinding, val expiresAt: Instant)

    private val tickets = ConcurrentHashMap<String, Ticket>()
    private val random = SecureRandom()

    fun issue(sub: String, settings: PublicCvSettings): String {
        removeExpired()
        val bytes = ByteArray(TICKET_BYTES).also { random.nextBytes(it) }
        val value = bytes.joinToString("") { "%02x".format(it) }
        tickets[value] = Ticket(PreviewBinding(sub, settings), clock.instant().plus(LIFETIME))
        return value
    }

    /** The binding when the ticket is valid; null for an unknown, used or expired value. Any attempt uses the ticket up. */
    fun redeem(value: String): PreviewBinding? {
        removeExpired()
        val ticket = tickets.remove(value) ?: return null
        if (!clock.instant().isBefore(ticket.expiresAt)) return null
        return ticket.binding
    }

    private fun removeExpired() {
        val now = clock.instant()
        tickets.values.removeIf { !now.isBefore(it.expiresAt) }
    }

    private companion object {
        const val TICKET_BYTES = 16
        val LIFETIME: Duration = Duration.ofSeconds(60)
    }
}
