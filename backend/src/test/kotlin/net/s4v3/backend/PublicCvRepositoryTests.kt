package net.s4v3.backend

import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.publiccv.PublicCvContact
import net.s4v3.backend.cv.publiccv.PublicCvSettings
import net.s4v3.backend.cv.publiccv.internal.PublicCvEvent
import net.s4v3.backend.cv.publiccv.internal.PublicCvRepository
import net.s4v3.backend.cv.publiccv.internal.PublicCvRow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class PublicCvRepositoryTests(
    @Autowired private val repo: PublicCvRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val t0: Instant = Instant.parse("2026-10-01T10:00:00Z")

    private fun newUser(): String = ("pcv-repo-" + UUID.randomUUID()).also { jdbc.update("insert into app_user (sub) values (?)", it) }

    private fun newHandle(): String = "pcv-" + UUID.randomUUID().toString().take(12)

    private fun settings() = PublicCvSettings(
        sections = listOf(CvSectionKind.EXPERIENCE, CvSectionKind.SKILLS),
        headline = true,
        contact = PublicCvContact(website = true),
    )

    private fun page(sub: String, handle: String, at: Instant = t0) = PublicCvRow(
        ownerSub = sub,
        handle = handle,
        settings = settings(),
        published = true,
        locked = false,
        settingsVersion = 1,
        handleChangedAt = at,
        updatedAt = at,
    )

    @Test
    fun `a page is stored and read back with its settings`() {
        val sub = newUser()
        val handle = newHandle()
        repo.upsert(page(sub, handle))

        val read = repo.find(sub)

        assertEquals(page(sub, handle), read)
        assertFalse(read!!.locked)
    }

    @Test
    fun `a taken handle is found in any capitalisation and cannot be taken again`() {
        val owner = newUser()
        val handle = newHandle()
        repo.upsert(page(owner, handle))

        assertEquals(owner, repo.findByHandle(handle.uppercase())?.ownerSub)
        assertFailsWith<DataIntegrityViolationException> { repo.upsert(page(newUser(), handle)) }
    }

    @Test
    fun `a saved page keeps the owner's settings and the lock is only changed by an administrator`() {
        val owner = newUser()
        val handle = newHandle()
        repo.upsert(page(owner, handle))
        repo.takeDown(handle, t0)

        repo.upsert(page(owner, handle, at = t0).copy(settingsVersion = 2))

        val read = repo.find(owner)!!
        assertTrue(read.locked)
        assertEquals(2, read.settingsVersion)
    }

    @Test
    fun `pausing and removing the page`() {
        val owner = newUser()
        repo.upsert(page(owner, newHandle()))

        assertTrue(repo.setPublished(owner, false, t0))
        assertFalse(repo.find(owner)!!.published)

        assertTrue(repo.delete(owner))
        assertNull(repo.find(owner))
        assertFalse(repo.delete(owner))
        assertFalse(repo.setPublished(owner, true, t0))
    }

    @Test
    fun `a released handle is held until its time and free after it`() {
        val previous = newUser()
        val handle = newHandle()
        val heldUntil = t0.plus(Duration.ofDays(90))
        repo.insertHold(handle, previous, heldUntil)

        assertEquals(previous, repo.activeHold(handle, t0.plus(Duration.ofDays(89)))?.ownerSub)
        assertNull(repo.activeHold(handle, heldUntil))
        // The database is shared with other tests, so only the lower bound is certain.
        assertTrue(repo.purgeHolds(heldUntil.plusSeconds(1)) >= 1)
        assertNull(repo.activeHold(handle, t0))
    }

    @Test
    fun `the owner can take a held handle back by deleting the hold`() {
        val previous = newUser()
        val handle = newHandle()
        repo.insertHold(handle, previous, t0.plus(Duration.ofDays(90)))

        assertTrue(repo.deleteHold(handle))

        assertNull(repo.activeHold(handle, t0))
        assertFalse(repo.deleteHold(handle))
    }

    @Test
    fun `a second hold of the same handle replaces the first`() {
        val first = newUser()
        val second = newUser()
        val handle = newHandle()
        repo.insertHold(handle, first, t0.plus(Duration.ofDays(90)))

        repo.insertHold(handle, second, t0.plus(Duration.ofDays(120)))

        assertEquals(second, repo.activeHold(handle, t0)?.ownerSub)
    }

    @Test
    fun `a change within 30 days is too soon, and the day after it is allowed`() {
        val owner = newUser()
        repo.audit(owner, PublicCvEvent.HANDLE_CHANGED, t0, emptyMap())

        assertTrue(repo.changeTooSoon(owner, t0.plus(Duration.ofDays(10))))
        assertTrue(repo.changeTooSoon(owner, t0.plus(Duration.ofDays(29))))
        assertFalse(repo.changeTooSoon(owner, t0.plus(Duration.ofDays(30))))
        assertFalse(repo.changeTooSoon(newUser(), t0))
    }

    @Test
    fun `deleting the page does not reset the change rule`() {
        val owner = newUser()
        repo.upsert(page(owner, newHandle(), at = t0))
        repo.audit(owner, PublicCvEvent.HANDLE_CHANGED, t0, emptyMap())
        repo.delete(owner)

        assertTrue(repo.changeTooSoon(owner, t0.plus(Duration.ofDays(5))))
    }

    @Test
    fun `the audit keeps the event and the flags, never the values`() {
        val owner = newUser()
        repo.audit(owner, PublicCvEvent.PUBLISHED, t0, mapOf("headline" to true, "email" to false))

        assertEquals("PUBLISHED", jdbc.queryForObject<String>("select event from public_cv_audit where owner_sub = ?", owner))
        assertEquals("true", jdbc.queryForObject<String>("select flags->>'headline' from public_cv_audit where owner_sub = ?", owner))
        assertEquals("false", jdbc.queryForObject<String>("select flags->>'email' from public_cv_audit where owner_sub = ?", owner))
    }

    @Test
    fun `a takedown answers the owner and an unlock clears the lock`() {
        val owner = newUser()
        val handle = newHandle()
        repo.upsert(page(owner, handle))

        assertEquals(owner, repo.takeDown(handle, t0))
        val taken = repo.find(owner)!!
        assertFalse(taken.published)
        assertTrue(taken.locked)

        assertEquals(owner, repo.unlock(handle, t0))
        assertFalse(repo.find(owner)!!.locked)
        assertNull(repo.unlock(newHandle(), t0))
    }

    @Test
    fun `deleting the user removes the page, the holds and the audit`() {
        val owner = newUser()
        val handle = newHandle()
        repo.upsert(page(owner, handle))
        repo.insertHold(handle, owner, t0.plus(Duration.ofDays(90)))
        repo.audit(owner, PublicCvEvent.PUBLISHED, t0, emptyMap())

        jdbc.update("delete from app_user where sub = ?", owner)

        assertNull(repo.find(owner))
        assertNull(repo.activeHold(handle, t0))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from public_cv_audit where owner_sub = ?", owner))
        assertNull(repo.findByHandle(handle))
    }
}
