package net.s4v3.backend.files.internal

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived, single-use download tickets, so a browser can start a download without a bearer token (a plain navigation
 * cannot send one). A ticket is 32 random bytes as hex, bound to one user and one file, valid for `ticket-seconds`, and
 * removed on its first use. Tickets are kept in memory only: a restart invalidates them, which is fine for a minute.
 * A ticket is a credential and is never logged.
 */
@Component
internal class DownloadTickets(
    private val settings: FilesSettings,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val tickets = ConcurrentHashMap<String, Ticket>()
    private val random = SecureRandom()

    private class Ticket(val sub: String, val fileId: UUID, val expiresAt: Instant)

    fun issue(sub: String, fileId: UUID): String {
        removeExpired()
        val bytes = ByteArray(TICKET_BYTES).also { random.nextBytes(it) }
        val value = bytes.joinToString("") { "%02x".format(it) }
        tickets[value] = Ticket(sub, fileId, clock.instant().plusSeconds(settings.ticketSeconds))
        return value
    }

    /**
     * The owner's `sub` when the ticket is valid for this file. The ticket is used up by any attempt, so a value can be
     * tried only once; an unknown, expired or foreign value answers null.
     */
    fun redeem(value: String, fileId: UUID): String? {
        removeExpired()
        val ticket = tickets.remove(value) ?: return null
        if (ticket.fileId != fileId || clock.instant().isAfter(ticket.expiresAt)) return null
        return ticket.sub
    }

    private fun removeExpired() {
        val now = clock.instant()
        tickets.values.removeIf { now.isAfter(it.expiresAt) }
    }

    private companion object {
        const val TICKET_BYTES = 32
    }
}
