package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.CvPhoto
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.InvalidPhotoException
import net.s4v3.backend.cv.export.PhotoProcessor
import net.s4v3.backend.cv.export.internal.RenderGuard
import net.s4v3.backend.cv.publiccv.PublicPhoto
import net.s4v3.backend.files.CabinetFile
import net.s4v3.backend.files.FileCabinet
import java.util.concurrent.TimeUnit
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
import org.springframework.transaction.annotation.Transactional
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
    private val cabinet: FileCabinet,
    private val guard: RenderGuard,
    private val clock: Clock = Clock.systemUTC(),
) : PublicCvService {

    private val html = HtmlRenderer()
    private val photos = PhotoProcessor()

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

    @Transactional
    override fun save(sub: String, handle: String, published: Boolean, settings: PublicCvSettings): SaveResult {
        when (HandleRules.validate(handle)) {
            HandleProblem.INVALID -> return SaveResult.ValidationFailed(mapOf("handle" to INVALID_HANDLE))
            HandleProblem.RESERVED -> return SaveResult.Reserved
            null -> Unit
        }
        // Nothing is saved for a photo that is not the caller's own JPEG; the answer never says which case it was.
        if (settings.photoFileId != null && cabinet.image(sub, settings.photoFileId) == null) return SaveResult.PhotoInvalid
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
        // A claim after a removal counts like a change: within 30 days of the last claim only the owner's most recent handle
        // (on hold for the owner) may be claimed again, so deleting the page does not open a way around the rule.
        val claim = existing == null
        if (claim) {
            val last = repo.lastHandleClaim(sub)
            if (last != null && last.plus(CHANGE_INTERVAL).isAfter(now) && repo.latestHeldHandle(sub) != normal) {
                return SaveResult.ChangeTooSoon(last.plus(CHANGE_INTERVAL))
            }
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
        } catch (e: DataIntegrityViolationException) {
            // Another user claimed the handle between the check and the write; any other violation is a real error.
            if (!isHandleConflict(e)) throw e
            return SaveResult.Taken
        }
        cache.invalidate(sub)
        if (hold != null) repo.deleteHold(normal)
        if (changed) repo.insertHold(existing.handle, sub, now.plus(HOLD))

        val flags = flags(settings)
        if (claim) repo.audit(sub, PublicCvEvent.HANDLE_CLAIMED, now, flags)
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

    @Transactional
    override fun remove(sub: String): RemoveResult {
        val existing = repo.find(sub) ?: return RemoveResult.NOT_FOUND
        // An owner under an administrator's lock must not escape it by deleting and claiming the handle again.
        if (existing.locked) return RemoveResult.LOCKED
        val now = clock.instant()
        repo.delete(sub)
        cache.invalidate(sub)
        repo.insertHold(existing.handle, sub, now.plus(HOLD))
        repo.audit(sub, PublicCvEvent.REMOVED, now, flags(existing.settings))
        return RemoveResult.REMOVED
    }

    override fun renderPublic(handle: String): RenderedPage? = renderServed(handle)

    override fun renderPhoto(handle: String): PublicPhoto? = renderServed(handle)?.photo

    private fun renderServed(handle: String): RenderedPage? {
        if (HandleRules.validate(handle) != null) return null
        val row = repo.findByHandle(HandleRules.normalize(handle)) ?: return null
        if (!row.published || row.locked) return null
        val stored = profiles.get(row.ownerSub)
        if (stored.profile.contact.fullName.isNullOrBlank()) return null
        // The file is read by the owner's sub on every request, so a deleted or replaced file changes the key at once.
        val file = photoFile(row.ownerSub, row.settings)
        val key = RenderKey(
            row.ownerSub, stored.version, settingsHash(row.settings, row.published), row.settings.template, row.settings.language,
            if (file == null) "" else "${file.id}:${file.sizeBytes}:${file.sha256}",
        )
        cache.get(key)?.let { return it }
        val made = file?.let { processPhoto(it) }
        val page = render(stored.profile, row.settings, made?.photo)
        // A photo that could not be made now (busy) is not remembered, so the next request tries again.
        if (made == null || made.final) cache.put(key, page)
        return page
    }

    override fun preview(sub: String, settings: PublicCvSettings): PreviewTicket = PreviewTicket(tickets.issue(sub, settings))

    override fun renderPreview(ticket: PreviewTicket): RenderedPage? {
        val bound = tickets.redeem(ticket.value) ?: return null
        val profile = profiles.get(bound.sub).profile
        if (profile.contact.fullName.isNullOrBlank()) return null
        // A preview is never indexable, whatever the settings say, and is not cached.
        val photo = photoFile(bound.sub, bound.settings)?.let { processPhoto(it) }?.photo
        return render(profile, bound.settings.copy(indexable = false), photo)
    }

    /** The owner's JPEG for the photo, only with the PROFESSIONAL template and a file that is still theirs; else null. */
    private fun photoFile(sub: String, s: PublicCvSettings): CabinetFile? {
        val id = s.photoFileId ?: return null
        if (s.template != CvTemplate.PROFESSIONAL) return null
        return cabinet.image(sub, id)
    }

    /** The processed photo, or null when it cannot be made (an unreadable file or no render permit); never logs the file. */
    private fun processPhoto(file: CabinetFile): MadePhoto {
        if (!guard.permits.tryAcquire(guard.wait.toMillis(), TimeUnit.MILLISECONDS)) return MadePhoto(null, final = false)
        try {
            return MadePhoto(CvPhoto(photos.process(file)), final = true)
        } catch (_: InvalidPhotoException) {
            return MadePhoto(null, final = true)
        } finally {
            guard.permits.release()
        }
    }

    override fun takeDown(handle: String): Boolean {
        val sub = repo.takeDown(HandleRules.normalize(handle), clock.instant()) ?: return false
        cache.invalidate(sub)
        repo.audit(sub, PublicCvEvent.TAKEN_DOWN, clock.instant(), emptyMap())
        return true
    }

    override fun unlock(handle: String): Boolean {
        val sub = repo.unlock(HandleRules.normalize(handle), clock.instant()) ?: return false
        cache.invalidate(sub)
        repo.audit(sub, PublicCvEvent.UNLOCKED, clock.instant(), emptyMap())
        return true
    }

    private fun hasNoName(sub: String) = profiles.get(sub).profile.contact.fullName.isNullOrBlank()

    private fun state(row: PublicCvRow): PublicCvState {
        val now = clock.instant()
        val changeable = repo.lastHandleChange(row.ownerSub)?.plus(CHANGE_INTERVAL)?.takeIf { it.isAfter(now) }
        return PublicCvState(row.handle, row.published, row.locked, row.settings, changeable, row.updatedAt)
    }

    private fun render(profile: Profile, settings: PublicCvSettings, photo: CvPhoto? = null): RenderedPage {
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
        val page = html.html(CvBuilder.build(profile, options, photo?.bytes), settings.template, head)
            .replace("<a href=\"", "<a rel=\"noopener noreferrer nofollow\" href=\"")
        val bytes = page.toByteArray(Charsets.UTF_8)
        return RenderedPage(bytes, sha256(bytes), settings.indexable, photo?.let { PublicPhoto(it.bytes, sha256(it.bytes)) })
    }

    /** SHA-256 of every setting that shapes the page, in a fixed order, so a page is never found for other settings. */
    private fun settingsHash(s: PublicCvSettings, published: Boolean): String {
        val canonical = listOf(
            "p=$published", "t=${s.template.name}", "l=${s.language.name}", "s=${s.sections.joinToString(",") { it.name }}",
            "h=${s.headline}", "a=${s.analysis}", "w=${s.workAuthorisation}", "loc=${s.location}",
            "ce=${s.contact.email}", "cp=${s.contact.phone}", "cw=${s.contact.website}", "cl=${s.contact.linkedin}",
            "cc=${s.contact.codeAccounts}", "i=${s.indexable}",
        ).joinToString(";")
        return sha256(canonical.toByteArray(Charsets.UTF_8))
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
        put("photo", s.photoFileId != null)
        CvSectionKind.entries.forEach { put("section.${it.name}", it in s.sections) }
    }

    /** [final] is false when the photo was not tried (no permit), so the page must not be cached. */
    private class MadePhoto(val photo: CvPhoto?, val final: Boolean)

    internal companion object {
        /** True only for a violation of the unique handle index; the name is in the database error text. */
        fun isHandleConflict(e: DataIntegrityViolationException): Boolean =
            generateSequence<Throwable>(e) { it.cause }.any { it.message?.contains(HANDLE_INDEX) == true }

        private const val HANDLE_INDEX = "public_cv_handle_lower_idx"

        private val HOLD: Duration = Duration.ofDays(90)
        private val CHANGE_INTERVAL: Duration = Duration.ofDays(30)
        private const val INVALID_HANDLE = "Use 3 to 30 lower-case letters, digits or single hyphens, not only digits."
    }
}
