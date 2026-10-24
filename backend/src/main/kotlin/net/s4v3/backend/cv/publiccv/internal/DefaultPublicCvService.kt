package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.ContactVisibility
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.internal.CvBuilder
import net.s4v3.backend.cv.export.internal.HtmlRenderer
import net.s4v3.backend.cv.publiccv.HandleUnavailable
import net.s4v3.backend.cv.publiccv.PreviewTicket
import net.s4v3.backend.cv.publiccv.PublicCvService
import net.s4v3.backend.cv.publiccv.PublicCvSettings
import net.s4v3.backend.cv.publiccv.PublicCvState
import net.s4v3.backend.cv.publiccv.RemoveResult
import net.s4v3.backend.cv.publiccv.RenderedPage
import net.s4v3.backend.cv.publiccv.SaveResult
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration

/**
 * Rules and rendering of the public CV page. The handle rules, the hold and the change rule are applied here; the
 * repository only stores. Nothing in here logs a handle, a setting value or profile text, and the audit keeps flags only.
 */
@Service
internal class DefaultPublicCvService(
    private val repo: PublicCvRepository,
    private val profiles: ProfileService,
    private val tickets: PreviewTickets,
    private val cache: RenderCache,
    private val clock: Clock = Clock.systemUTC(),
) : PublicCvService {

    private val html = HtmlRenderer()

    override fun state(sub: String): PublicCvState? = repo.find(sub)?.let { state(it) }

    override fun checkHandle(sub: String, handle: String): HandleUnavailable? {
        when (HandleRules.validate(handle)) {
            HandleProblem.INVALID -> return HandleUnavailable.INVALID
            HandleProblem.RESERVED -> return HandleUnavailable.RESERVED
            null -> Unit
        }
        val normal = HandleRules.normalize(handle)
        val other = repo.findByHandle(normal)
        if (other != null && other.ownerSub != sub) return HandleUnavailable.TAKEN
        val hold = repo.activeHold(normal, clock.instant())
        if (hold != null && hold.ownerSub != sub) return HandleUnavailable.HELD
        return null
    }

    override fun save(sub: String, handle: String, published: Boolean, settings: PublicCvSettings): SaveResult {
        when (HandleRules.validate(handle)) {
            HandleProblem.INVALID -> return SaveResult.ValidationFailed(mapOf("handle" to INVALID_HANDLE))
            HandleProblem.RESERVED -> return SaveResult.Reserved
            null -> Unit
        }
        val normal = HandleRules.normalize(handle)
        val existing = repo.find(sub)
        if (existing?.locked == true && published) return SaveResult.Locked
        if (published && hasNoName(sub)) return SaveResult.NameRequired
        val now = clock.instant()

        val other = repo.findByHandle(normal)
        if (other != null && other.ownerSub != sub) return SaveResult.Taken
        val hold = repo.activeHold(normal, now)
        if (hold != null && hold.ownerSub != sub) return SaveResult.Held
        // The first claim is not a change; a later change is, and only one is allowed in 30 days.
        val changed = existing != null && existing.handle != normal
        if (changed) {
            val last = repo.lastHandleChange(sub)
            if (last != null && last.plus(CHANGE_INTERVAL).isAfter(now)) return SaveResult.ChangeTooSoon(last.plus(CHANGE_INTERVAL))
        }

        val settingsChanged = existing == null || existing.settings != settings
        val row = PublicCvRow(
            ownerSub = sub,
            handle = normal,
            settings = settings,
            published = published,
            locked = existing?.locked ?: false,
            settingsVersion = if (existing == null) 1 else if (settingsChanged) existing.settingsVersion + 1 else existing.settingsVersion,
            handleChangedAt = if (changed) now else existing?.handleChangedAt,
            updatedAt = now,
        )
        try {
            repo.upsert(row)
        } catch (_: DataIntegrityViolationException) {
            // Another user claimed the handle between the check and the write.
            return SaveResult.Taken
        }
        if (hold != null) repo.deleteHold(normal)
        if (changed) repo.insertHold(existing.handle, sub, now.plus(HOLD))

        val flags = flags(settings)
        if (changed) repo.audit(sub, PublicCvEvent.HANDLE_CHANGED, now, flags)
        val wasPublished = existing?.published == true
        if (published && !wasPublished) repo.audit(sub, PublicCvEvent.PUBLISHED, now, flags)
        if (!published && wasPublished) repo.audit(sub, PublicCvEvent.PAUSED, now, flags)
        // A first save that publishes is only PUBLISHED; a first draft or a later change of settings is SETTINGS_CHANGED.
        if (if (existing == null) !published else settingsChanged) {
            repo.audit(sub, PublicCvEvent.SETTINGS_CHANGED, now, flags)
        }
        return SaveResult.Saved(state(row))
    }

    override fun remove(sub: String): RemoveResult {
        val existing = repo.find(sub) ?: return RemoveResult.NOT_FOUND
        // An owner under an administrator's lock must not escape it by deleting and claiming the handle again.
        if (existing.locked) return RemoveResult.LOCKED
        val now = clock.instant()
        repo.delete(sub)
        repo.insertHold(existing.handle, sub, now.plus(HOLD))
        repo.audit(sub, PublicCvEvent.REMOVED, now, flags(existing.settings))
        return RemoveResult.REMOVED
    }

    override fun renderPublic(handle: String): RenderedPage? {
        if (HandleRules.validate(handle) != null) return null
        val row = repo.findByHandle(HandleRules.normalize(handle)) ?: return null
        if (!row.published || row.locked) return null
        val stored = profiles.get(row.ownerSub)
        if (stored.profile.contact.fullName.isNullOrBlank()) return null
        val key = RenderKey(row.ownerSub, stored.version, row.settingsVersion, row.settings.template, row.settings.language)
        cache.get(key)?.let { return it }
        return render(stored.profile, row.settings).also { cache.put(key, it) }
    }

    override fun preview(sub: String, settings: PublicCvSettings): PreviewTicket = PreviewTicket(tickets.issue(sub, settings))

    override fun renderPreview(ticket: PreviewTicket): RenderedPage? {
        val bound = tickets.redeem(ticket.value) ?: return null
        val profile = profiles.get(bound.sub).profile
        if (profile.contact.fullName.isNullOrBlank()) return null
        // A preview is never indexable, whatever the settings say, and is not cached.
        return render(profile, bound.settings.copy(indexable = false))
    }

    override fun takeDown(handle: String): Boolean {
        val sub = repo.takeDown(HandleRules.normalize(handle), clock.instant()) ?: return false
        repo.audit(sub, PublicCvEvent.TAKEN_DOWN, clock.instant(), emptyMap())
        return true
    }

    override fun unlock(handle: String): Boolean {
        val sub = repo.unlock(HandleRules.normalize(handle), clock.instant()) ?: return false
        repo.audit(sub, PublicCvEvent.UNLOCKED, clock.instant(), emptyMap())
        return true
    }

    private fun hasNoName(sub: String) = profiles.get(sub).profile.contact.fullName.isNullOrBlank()

    private fun state(row: PublicCvRow): PublicCvState {
        val now = clock.instant()
        val changeable = repo.lastHandleChange(row.ownerSub)?.plus(CHANGE_INTERVAL)?.takeIf { it.isAfter(now) }
        return PublicCvState(row.handle, row.published, row.locked, row.settings, changeable, row.updatedAt)
    }

    private fun render(profile: Profile, settings: PublicCvSettings): RenderedPage {
        val options = ExportOptions(
            format = CvFormat.HTML,
            template = settings.template,
            language = settings.language,
            sections = settings.sections,
            includeOpenSource = CvSectionKind.OPEN_SOURCE in settings.sections,
            includeAnalysis = settings.analysis,
            includeWorkAuthorisation = settings.workAuthorisation,
            contact = ContactVisibility(
                email = settings.contact.email,
                phone = settings.contact.phone,
                website = settings.contact.website,
                linkedin = settings.contact.linkedin,
                codeAccounts = settings.contact.codeAccounts,
                location = settings.location,
                headline = settings.headline,
            ),
        )
        val robots = if (settings.indexable) "index, follow" else "noindex, nofollow, noarchive, nosnippet"
        val head = "<meta name=\"robots\" content=\"$robots\"/>\n<meta name=\"referrer\" content=\"no-referrer\"/>\n"
        // Every anchor comes from HtmlRenderer.link, whose text and address are escaped, so nothing else can match here.
        val page = html.html(CvBuilder.build(profile, options), settings.template, head)
            .replace("<a href=\"", "<a rel=\"noopener noreferrer nofollow\" href=\"")
        val bytes = page.toByteArray(Charsets.UTF_8)
        return RenderedPage(bytes, sha256(bytes), settings.indexable)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Which settings are on, never their values. */
    private fun flags(s: PublicCvSettings): Map<String, Boolean> = buildMap {
        put("headline", s.headline)
        put("analysis", s.analysis)
        put("workAuthorisation", s.workAuthorisation)
        put("location", s.location)
        put("contact.email", s.contact.email)
        put("contact.phone", s.contact.phone)
        put("contact.website", s.contact.website)
        put("contact.linkedin", s.contact.linkedin)
        put("contact.codeAccounts", s.contact.codeAccounts)
        put("indexable", s.indexable)
        CvSectionKind.entries.forEach { put("section.${it.name}", it in s.sections) }
    }

    private companion object {
        val HOLD: Duration = Duration.ofDays(90)
        val CHANGE_INTERVAL: Duration = Duration.ofDays(30)
        const val INVALID_HANDLE = "Use 3 to 30 lower-case letters, digits or single hyphens, not only digits."
    }
}
