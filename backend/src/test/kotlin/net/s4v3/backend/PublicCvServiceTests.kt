package net.s4v3.backend

import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.publiccv.PublicCvContact
import net.s4v3.backend.cv.publiccv.PublicCvSettings
import net.s4v3.backend.cv.publiccv.PublicCvState
import net.s4v3.backend.cv.publiccv.RemoveResult
import net.s4v3.backend.cv.publiccv.RenderedPage
import net.s4v3.backend.cv.publiccv.SaveResult
import net.s4v3.backend.cv.publiccv.internal.DefaultPublicCvService
import net.s4v3.backend.cv.publiccv.internal.PreviewTickets
import net.s4v3.backend.cv.publiccv.internal.PublicCvRepository
import net.s4v3.backend.cv.publiccv.internal.RenderCache
import net.s4v3.backend.profile.Preferences
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.ProfileUpdate
import net.s4v3.backend.profile.WorkAuthorisation
import net.s4v3.backend.profile.internal.ProfileJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The rules and the rendering of the public CV page without HTTP. Synthetic profile (an invented person). */
@IntegrationTest
internal class PublicCvServiceTests(
    @Autowired private val repo: PublicCvRepository,
    @Autowired private val profiles: ProfileService,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val clock = TestClock()
    private val service = DefaultPublicCvService(repo, profiles, PreviewTickets(clock), RenderCache(clock = clock), clock)

    private val fixture = javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes().decodeToString()

    private fun baseProfile(): Profile = ProfileJson.read(fixture).let {
        it.copy(
            contact = it.contact.copy(fullName = "Ada Example", phone = "+41 79 555 01 23"),
            workAuthorisation = WorkAuthorisation.PERMIT_B,
            preferences = Preferences(targetRoles = listOf("Secret Role"), cantons = listOf("ZG"), minSalaryChf = 150000),
        )
    }

    private fun newUser(profile: Profile? = baseProfile()): String {
        val sub = "pcv-svc-" + UUID.randomUUID()
        jdbc.update("insert into app_user (sub) values (?)", sub)
        if (profile != null) store(sub, profile)
        return sub
    }

    private fun store(sub: String, profile: Profile) {
        profiles.replace(sub, ProfileUpdate(profiles.get(sub).version, profile))
    }

    private fun newHandle() = "pcv-" + UUID.randomUUID().toString().take(12)

    private fun all() = PublicCvSettings(
        sections = CvSectionKind.entries.toList(),
        headline = true,
        analysis = true,
        workAuthorisation = true,
        location = true,
        contact = PublicCvContact(email = true, phone = true, website = true, linkedin = true, codeAccounts = true),
    )

    private fun publish(sub: String, handle: String = newHandle(), settings: PublicCvSettings = PublicCvSettings()): String {
        val result = service.save(sub, handle, true, settings)
        assertTrue(result is SaveResult.Saved, "expected Saved but was $result")
        return handle
    }

    private fun page(handle: String): String = service.renderPublic(handle)!!.html.decodeToString()

    private fun events(sub: String): List<String> =
        jdbc.queryForList("select event from public_cv_audit where owner_sub = ? order by id", String::class.java, sub).map { it!! }

    // Visibility settings

    @Test
    fun `defaults are private, only the full name is public`() {
        val handle = publish(newUser())
        val text = page(handle)

        assertTrue("Ada Example" in text)
        for (hidden in listOf("ada@example.org", "555", "Senior Kotlin", "Zurich", "Permit", "Example AG", "Example University", "Kotlin", "example.org")) {
            assertFalse(hidden in text, "found $hidden")
        }
    }

    @Test
    fun `ticked items appear and nothing else`() {
        val settings = PublicCvSettings(
            sections = listOf(CvSectionKind.EXPERIENCE, CvSectionKind.SKILLS),
            headline = true,
            contact = PublicCvContact(website = true),
        )
        val text = page(publish(newUser(), settings = settings))

        assertTrue("Senior Kotlin Engineer" in text)
        assertTrue("https://example.org" in text)
        assertTrue("Example AG" in text)
        assertTrue("Kotlin" in text)
        for (hidden in listOf("ada@example.org", "555", "Example University", "linkedin", "octocat", "Permit")) {
            assertFalse(hidden in text, "found $hidden")
        }
    }

    @Test
    fun `preferences are never printed, whatever is ticked`() {
        val text = page(publish(newUser(), settings = all()))

        for (secret in listOf("150000", "150'000", "Secret Role", "ZG", "130000", "Backend Engineer")) {
            assertFalse(secret in text, "found $secret")
        }
        assertTrue("Example AG" in text)
    }

    @Test
    fun `sections come in the owner's order`() {
        val text = page(publish(newUser(), settings = PublicCvSettings(sections = listOf(CvSectionKind.SKILLS, CvSectionKind.EXPERIENCE))))

        val skills = text.indexOf("<h2>Skills")
        val experience = text.indexOf("<h2>Experience")
        assertTrue(skills in 0 until experience, "skills $skills experience $experience")
    }

    @Test
    fun `open source is printed only when it is a section`() {
        val without = page(publish(newUser(), settings = PublicCvSettings(sections = listOf(CvSectionKind.SKILLS), analysis = true)))
        val with = page(publish(newUser(), settings = PublicCvSettings(sections = listOf(CvSectionKind.OPEN_SOURCE), analysis = true)))

        assertFalse("hello-world" in without)
        assertTrue("hello-world" in with)
        assertTrue("small Spring Boot service" in with)
    }

    @Test
    fun `the language and template of the settings are used`() {
        val de = page(publish(newUser(), settings = PublicCvSettings(language = CvLanguage.DE, sections = listOf(CvSectionKind.EXPERIENCE))))
        assertTrue("lang=\"de\"" in de)
        assertTrue("Berufserfahrung" in de)
    }

    // The page

    @Test
    fun `the page head has title, robots, referrer and a link rel`() {
        val handle = publish(newUser(), settings = PublicCvSettings(contact = PublicCvContact(website = true)))
        val text = page(handle)

        assertTrue("<title>Ada Example</title>" in text)
        assertTrue("<meta name=\"robots\" content=\"noindex, nofollow, noarchive, nosnippet\"/>" in text)
        assertTrue("<meta name=\"referrer\" content=\"no-referrer\"/>" in text)
        assertTrue("<a rel=\"noopener noreferrer nofollow\" href=\"https://example.org\">" in text)
        assertFalse("<script" in text)
    }

    @Test
    fun `indexable switches the robots meta`() {
        val handle = publish(newUser(), settings = PublicCvSettings(indexable = true))
        val rendered = service.renderPublic(handle)!!

        assertTrue(rendered.indexable)
        assertTrue("<meta name=\"robots\" content=\"index, follow\"/>" in rendered.html.decodeToString())
    }

    @Test
    fun `the etag is the sha-256 of the html`() {
        val rendered = service.renderPublic(publish(newUser()))!!

        val expected = MessageDigest.getInstance("SHA-256").digest(rendered.html).joinToString("") { "%02x".format(it) }
        assertEquals(expected, rendered.etag)
    }

    @Test
    fun `the handle is matched whatever its case and served pages are cached by version`() {
        val handle = publish(newUser())

        val first = service.renderPublic(handle.uppercase())!!
        assertSame(first, service.renderPublic(handle))
    }

    @Test
    fun `a live edit shows on the next request and the cache follows the profile version`() {
        val sub = newUser()
        val handle = publish(sub, settings = PublicCvSettings(sections = listOf(CvSectionKind.PROFILE)))
        val before = service.renderPublic(handle)!!
        assertTrue("Invented engineer" in before.html.decodeToString())

        store(sub, baseProfile().copy(summary = "A new summary of Ada."))
        val after = service.renderPublic(handle)!!

        assertTrue("A new summary of Ada." in after.html.decodeToString())
        assertNotEquals(before.etag, after.etag)
    }

    @Test
    fun `an edit of something not ticked stays private`() {
        val sub = newUser()
        val handle = publish(sub, settings = PublicCvSettings(sections = listOf(CvSectionKind.SKILLS)))

        store(sub, baseProfile().let { it.copy(contact = it.contact.copy(phone = "+41 78 999 88 77")) })

        assertFalse("88 77" in page(handle))
    }

    @Test
    fun `a settings change shows on the next request`() {
        val sub = newUser()
        val handle = publish(sub)
        assertFalse("Senior Kotlin Engineer" in page(handle))

        service.save(sub, handle, true, PublicCvSettings(headline = true))

        assertTrue("Senior Kotlin Engineer" in page(handle))
    }

    @Test
    fun `markup in the profile is printed as text`() {
        val sub = newUser(baseProfile().let { it.copy(summary = "<script>alert(1)</script> \"x\" & y") })
        val text = page(publish(sub, settings = PublicCvSettings(sections = listOf(CvSectionKind.PROFILE))))

        assertTrue("&lt;script&gt;" in text)
        assertFalse("<script" in text)
    }

    // Not served

    @Test
    fun `every state that is not served answers null`() {
        val sub = newUser()
        val handle = publish(sub)
        assertNotNull(service.renderPublic(handle))

        assertNull(service.renderPublic("no-such-" + UUID.randomUUID().toString().take(8)))
        assertNull(service.renderPublic("A"))
        assertNull(service.renderPublic(""))
        assertNull(service.renderPublic("x".repeat(500)))
        assertNull(service.renderPublic("admin"))

        service.save(sub, handle, false, PublicCvSettings())
        assertNull(service.renderPublic(handle), "paused")
        service.save(sub, handle, true, PublicCvSettings())
        assertNotNull(service.renderPublic(handle))

        store(sub, baseProfile().let { it.copy(contact = it.contact.copy(fullName = null)) })
        assertNull(service.renderPublic(handle), "no name")
        store(sub, baseProfile())
        assertNotNull(service.renderPublic(handle))

        assertTrue(service.takeDown(handle))
        assertNull(service.renderPublic(handle), "locked")

        assertEquals(RemoveResult.LOCKED, service.remove(sub))
        service.unlock(handle)
        assertEquals(RemoveResult.REMOVED, service.remove(sub))
        assertNull(service.renderPublic(handle), "removed")
    }

    // Publish, pause and remove

    @Test
    fun `publish, pause and resume keep the handle and settings`() {
        val sub = newUser()
        val settings = PublicCvSettings(headline = true)
        val handle = publish(sub, settings = settings)

        service.save(sub, handle, false, settings)
        val paused = service.state(sub)!!
        assertFalse(paused.published)
        assertEquals(settings, paused.settings)

        service.save(sub, handle, true, settings)
        assertTrue(service.state(sub)!!.published)
        assertTrue("Senior Kotlin Engineer" in page(handle))
    }

    @Test
    fun `a handle is stored in lower case`() {
        val sub = newUser()
        val handle = newHandle()
        val saved = service.save(sub, handle.uppercase(), true, PublicCvSettings()) as SaveResult.Saved
        assertEquals(handle, saved.state.handle)
    }

    @Test
    fun `no state before a page is configured`() {
        assertNull(service.state(newUser()))
        assertEquals(RemoveResult.NOT_FOUND, service.remove(newUser()))
    }

    @Test
    fun `a full name is required to publish but not to save a draft`() {
        val sub = newUser(baseProfile().let { it.copy(contact = it.contact.copy(fullName = null)) })

        assertEquals(SaveResult.NameRequired, service.save(sub, newHandle(), true, PublicCvSettings()))
        assertTrue(service.save(sub, newHandle(), false, PublicCvSettings()) is SaveResult.Saved)
    }

    @Test
    fun `a locked page refuses to publish and refuses removal, but settings can be saved paused`() {
        val sub = newUser()
        val handle = publish(sub)
        service.takeDown(handle)

        assertEquals(SaveResult.Locked, service.save(sub, handle, true, PublicCvSettings()))
        assertEquals(RemoveResult.LOCKED, service.remove(sub))
        assertTrue(service.state(sub)!!.locked)
        assertTrue(service.save(sub, handle, false, PublicCvSettings(headline = true)) is SaveResult.Saved)
        assertTrue(service.state(sub)!!.locked)

        assertTrue(service.unlock(handle))
        assertTrue(service.save(sub, handle, true, PublicCvSettings()) is SaveResult.Saved)
    }

    @Test
    fun `takedown and unlock of an unknown handle answer false`() {
        assertFalse(service.takeDown("nobody-" + UUID.randomUUID().toString().take(8)))
        assertFalse(service.unlock("nobody-" + UUID.randomUUID().toString().take(8)))
    }

    // Handle rules

    @Test
    fun `invalid and reserved handles are refused with no value in the message`() {
        val sub = newUser()
        for (bad in listOf("ab", "-ada", "ada--x", "ada_x", "a".repeat(31), "123456")) {
            val result = service.save(sub, bad, false, PublicCvSettings())
            assertTrue(result is SaveResult.ValidationFailed, bad)
            assertEquals(setOf("handle"), result.fields.keys)
            assertFalse(bad in result.fields.getValue("handle"))
        }
        assertEquals(SaveResult.Reserved, service.save(sub, "Profile", false, PublicCvSettings()))
        assertNull(service.state(sub))
    }

    @Test
    fun `a handle of another user is taken and the other page is unchanged`() {
        val a = newUser()
        val handle = publish(a, settings = PublicCvSettings(headline = true))

        assertEquals(SaveResult.Taken, service.save(newUser(), handle.uppercase(), true, PublicCvSettings()))
        assertTrue("Senior Kotlin Engineer" in page(handle))
    }

    @Test
    fun `the first claim is not a change and a later change is held for 90 days`() {
        val a = newUser()
        val old = publish(a)
        val new = newHandle()
        assertTrue(service.save(a, new, true, PublicCvSettings()) is SaveResult.Saved, "change right after the first claim")
        assertNull(service.renderPublic(old))
        val b = newUser()

        assertEquals(SaveResult.Held, service.save(b, old, true, PublicCvSettings()))
        clock.advance(Duration.ofDays(89))
        assertEquals(SaveResult.Held, service.save(b, old, true, PublicCvSettings()))
        clock.advance(Duration.ofDays(2))
        assertTrue(service.save(b, old, true, PublicCvSettings()) is SaveResult.Saved, "free after the hold")
    }

    @Test
    fun `the owner can take a held handle back and the hold goes`() {
        val a = newUser()
        val old = publish(a)
        service.save(a, newHandle(), true, PublicCvSettings())
        clock.advance(Duration.ofDays(31))

        assertTrue(service.save(a, old, true, PublicCvSettings()) is SaveResult.Saved)
        assertNull(repo.activeHold(old, clock.instant()))
        assertNotNull(service.renderPublic(old))
    }

    @Test
    fun `a second change within 30 days is too soon and the date is known`() {
        val a = newUser()
        publish(a)
        val second = newHandle()
        service.save(a, second, true, PublicCvSettings())
        val changedAt = clock.instant()
        clock.advance(Duration.ofDays(10))

        val result = service.save(a, newHandle(), true, PublicCvSettings())

        assertEquals(SaveResult.ChangeTooSoon(changedAt.plus(Duration.ofDays(30))), result)
        assertEquals(changedAt.plus(Duration.ofDays(30)), service.state(a)!!.handleChangeableAt)
        assertEquals(second, service.state(a)!!.handle)

        clock.advance(Duration.ofDays(21))
        assertTrue(service.save(a, newHandle(), true, PublicCvSettings()) is SaveResult.Saved)
        assertNull(service.state(a)!!.handleChangeableAt.takeIf { it?.isBefore(clock.instant()) == true })
    }

    @Test
    fun `saving the same handle again is not a change`() {
        val a = newUser()
        val handle = publish(a)
        assertNull(service.state(a)!!.handleChangeableAt)
        assertTrue(service.save(a, handle, true, PublicCvSettings(headline = true)) is SaveResult.Saved)
        assertEquals(listOf("HANDLE_CLAIMED", "PUBLISHED", "SETTINGS_CHANGED"), events(a))
    }

    // Remove

    @Test
    fun `remove deletes the page, holds the handle for 90 days and lets the owner take it back`() {
        val a = newUser()
        val handle = publish(a)

        assertEquals(RemoveResult.REMOVED, service.remove(a))
        assertNull(service.state(a))
        assertNull(service.renderPublic(handle))
        val b = newUser()
        assertEquals(SaveResult.Held, service.save(b, handle, true, PublicCvSettings()))

        assertTrue(service.save(a, handle, true, PublicCvSettings()) is SaveResult.Saved)
        assertNull(repo.activeHold(handle, clock.instant()))
    }

    @Test
    fun `removing and claiming a different handle within 30 days is too soon, after 30 days it is allowed`() {
        val a = newUser()
        val first = publish(a)
        service.remove(a)

        val result = service.save(a, newHandle(), true, PublicCvSettings())
        assertTrue(result is SaveResult.ChangeTooSoon, "was $result")
        assertNull(service.state(a))

        clock.advance(Duration.ofDays(31))
        assertTrue(service.save(a, newHandle(), true, PublicCvSettings()) is SaveResult.Saved)
        assertNotNull(repo.activeHold(first, clock.instant()))
    }

    @Test
    fun `removing and claiming the same handle is always allowed, even again and again`() {
        val a = newUser()
        val handle = publish(a)
        repeat(3) {
            service.remove(a)
            assertTrue(service.save(a, handle, true, PublicCvSettings()) is SaveResult.Saved)
        }
        // The window now runs from the last claim: a different handle is still too soon.
        service.remove(a)
        assertTrue(service.save(a, newHandle(), true, PublicCvSettings()) is SaveResult.ChangeTooSoon)
    }

    @Test
    fun `after a change and a removal only the most recent handle can be claimed back within 30 days`() {
        val a = newUser()
        val first = publish(a)
        val second = newHandle()
        service.save(a, second, true, PublicCvSettings())
        clock.advance(Duration.ofMinutes(5))
        service.remove(a)

        assertTrue(service.save(a, first, true, PublicCvSettings()) is SaveResult.ChangeTooSoon)
        assertTrue(service.save(a, second, true, PublicCvSettings()) is SaveResult.Saved)
    }

    @Test
    fun `one owner holds at most two handles at once however often they delete and claim`() {
        val a = newUser()
        val handles = listOf(publish(a)) + List(5) { newHandle() }
        service.save(a, newHandle(), true, PublicCvSettings())
        service.remove(a)
        var held = 0
        repeat(10) {
            val outcome = service.save(a, handles[it % handles.size], true, PublicCvSettings())
            if (outcome is SaveResult.Saved) service.remove(a)
            held = maxOf(held, jdbc.queryForObject("select count(*) from public_cv_handle_hold where owner_sub = ?", Int::class.java, a)!!)
        }
        assertTrue(held <= 2, "held $held")
    }

    @Test
    fun `the first claim is recorded so that a delete and a different claim is caught`() {
        val a = newUser()
        publish(a)
        assertEquals(listOf("HANDLE_CLAIMED", "PUBLISHED"), events(a))
    }

    @Test
    fun `only a violation of the handle index is taken, any other is rethrown`() {
        val handle = org.springframework.dao.DataIntegrityViolationException(
            "x", RuntimeException("ERROR: duplicate key value violates unique constraint \"public_cv_handle_lower_idx\""),
        )
        val other = org.springframework.dao.DataIntegrityViolationException(
            "x", RuntimeException("ERROR: null value in column \"settings\" violates not-null constraint"),
        )
        assertTrue(DefaultPublicCvService.isHandleConflict(handle))
        assertFalse(DefaultPublicCvService.isHandleConflict(other))
    }

    @Test
    fun `handles are lower-cased with the root locale`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            assertEquals("ida-x", net.s4v3.backend.cv.publiccv.internal.HandleRules.normalize("IDA-X"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    // Cache never shows older settings

    @Test
    fun `delete and publish again with the email off never shows the email from the cache`() {
        val sub = newUser()
        val handle = publish(sub, settings = PublicCvSettings(contact = PublicCvContact(email = true)))
        assertTrue("ada@example.org" in page(handle))

        service.remove(sub)
        publish(sub, handle, PublicCvSettings(contact = PublicCvContact(email = false)))

        assertFalse("ada@example.org" in page(handle))
    }

    @Test
    fun `two saves in a row with the email switched off never show the email`() {
        val sub = newUser()
        val handle = publish(sub, settings = PublicCvSettings(contact = PublicCvContact(email = true)))
        assertTrue("ada@example.org" in page(handle))

        service.save(sub, handle, true, PublicCvSettings(headline = true, contact = PublicCvContact(email = true)))
        service.save(sub, handle, true, PublicCvSettings(headline = true))

        assertFalse("ada@example.org" in page(handle))
    }

    @Test
    fun `a profile edit after the email was switched off in the cache does not bring it back`() {
        val sub = newUser()
        val handle = publish(sub, settings = PublicCvSettings(contact = PublicCvContact(email = true)))
        assertTrue("ada@example.org" in page(handle))
        service.remove(sub)
        publish(sub, handle, PublicCvSettings())
        store(sub, baseProfile().copy(summary = "Edited."))

        assertFalse("ada@example.org" in page(handle))
    }

    @Test
    fun `take down and unlock drop the cached page`() {
        val sub = newUser()
        val handle = publish(sub, settings = PublicCvSettings(contact = PublicCvContact(email = true)))
        page(handle)

        service.takeDown(handle)
        assertNull(service.renderPublic(handle))
        service.unlock(handle)
        service.save(sub, handle, true, PublicCvSettings())
        assertFalse("ada@example.org" in page(handle))
    }

    @Test
    fun `a preview for an owner whose account is gone answers null`() {
        val sub = newUser()
        val ticket = service.preview(sub, PublicCvSettings())
        jdbc.update("delete from app_user where sub = ?", sub)

        assertNull(service.renderPreview(ticket))
    }

    // Audit

    @Test
    fun `publish, handle change and pause write three audit events with flags only`() {
        val a = newUser()
        val handle = publish(a, settings = PublicCvSettings(headline = true, contact = PublicCvContact(email = true)))
        service.save(a, newHandle(), true, PublicCvSettings(headline = true, contact = PublicCvContact(email = true)))
        service.save(a, service.state(a)!!.handle, false, PublicCvSettings(headline = true, contact = PublicCvContact(email = true)))

        assertEquals(listOf("HANDLE_CLAIMED", "PUBLISHED", "HANDLE_CHANGED", "PAUSED"), events(a))
        val flags = jdbc.queryForObject(
            "select flags::text from public_cv_audit where owner_sub = ? and event = 'PUBLISHED'", String::class.java, a,
        )!!
        assertTrue("\"headline\": true" in flags && "\"contact.email\": true" in flags && "\"contact.phone\": false" in flags, flags)
        assertFalse(handle in flags || "ada@example.org" in flags || "Ada" in flags)
    }

    @Test
    fun `takedown, unlock and removal are audited against the owner`() {
        val a = newUser()
        val handle = publish(a)
        service.takeDown(handle)
        service.unlock(handle)
        service.remove(a)

        assertEquals(listOf("HANDLE_CLAIMED", "PUBLISHED", "TAKEN_DOWN", "UNLOCKED", "REMOVED"), events(a))
    }

    // Preview

    @Test
    fun `a preview works without a published page, once, and is never indexable`() {
        val a = newUser()
        val ticket = service.preview(a, PublicCvSettings(headline = true, indexable = true))

        val page: RenderedPage = service.renderPreview(ticket)!!
        val text = page.html.decodeToString()
        assertTrue("Senior Kotlin Engineer" in text)
        assertFalse(page.indexable)
        assertTrue("noindex, nofollow, noarchive, nosnippet" in text)
        assertNull(service.renderPreview(ticket))
    }

    @Test
    fun `a preview shows the posted settings, not the stored ones`() {
        val a = newUser()
        val handle = publish(a, settings = PublicCvSettings(headline = true))

        val text = service.renderPreview(service.preview(a, PublicCvSettings()))!!.html.decodeToString()

        assertFalse("Senior Kotlin Engineer" in text)
        assertTrue("Senior Kotlin Engineer" in page(handle))
    }

    @Test
    fun `a preview ticket expires after 60 seconds and a forged one answers null`() {
        val ticket = service.preview(newUser(), PublicCvSettings())
        clock.advance(Duration.ofSeconds(61))

        assertNull(service.renderPreview(ticket))
        assertNull(service.renderPreview(net.s4v3.backend.cv.publiccv.PreviewTicket("0".repeat(32))))
    }

    @Test
    fun `state carries the handle, switches and updated time`() {
        val a = newUser()
        val handle = publish(a)

        val state: PublicCvState = service.state(a)!!

        assertEquals(handle, state.handle)
        assertTrue(state.published)
        assertFalse(state.locked)
    }
}
