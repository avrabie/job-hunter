package net.s4v3.backend.cv.publiccv

import java.time.Instant

/** A rendered public page. [etag] is the SHA-256 of [html] as hex; [indexable] says which robots header goes with it. */
data class RenderedPage(val html: ByteArray, val etag: String, val indexable: Boolean, val photo: PublicPhoto? = null)

/** The processed photo (JPEG) of a public page; [etag] is the SHA-256 of [bytes] as hex. */
class PublicPhoto(val bytes: ByteArray, val etag: String)

/** The handle of a one-use preview. A credential: never log it. */
data class PreviewTicket(val value: String)

/** The answer to a save. Messages for the client are fixed by the controller; none repeats the submitted value. */
sealed interface SaveResult {
    data class Saved(val state: PublicCvState) : SaveResult

    /** Field path to a fixed message. */
    data class ValidationFailed(val fields: Map<String, String>) : SaveResult
    data object Reserved : SaveResult
    data object Taken : SaveResult
    data object Held : SaveResult

    /** [at] is when the handle may be changed again. */
    data class ChangeTooSoon(val at: Instant) : SaveResult
    data object NameRequired : SaveResult
    data object Locked : SaveResult

    /** The photo is not one of the caller's JPEG files; nothing was saved. */
    data object PhotoInvalid : SaveResult
}

/** The answer to removing the owner's page. */
enum class RemoveResult { REMOVED, NOT_FOUND, LOCKED }

/** Why a handle cannot be claimed by the asker. */
enum class HandleUnavailable { INVALID, RESERVED, TAKEN, HELD }

/**
 * The public CV page: the owner's handle and settings, and the page rendered from the owner's current saved profile.
 * Everything is by the token's `sub`; nothing accepts another way to name a user. Personal data: never log it.
 */
interface PublicCvService {

    /** The owner's page, or null when none is configured. */
    fun state(sub: String): PublicCvState?

    /** Null when [handle] is free for [sub] (the owner's own handle and own hold included), else the reason. */
    fun checkHandle(sub: String, handle: String): HandleUnavailable?

    fun save(sub: String, handle: String, published: Boolean, settings: PublicCvSettings): SaveResult

    /** Removes the page and holds its handle for 90 days; refused while an administrator's lock is on. */
    fun remove(sub: String): RemoveResult

    /** The page for a handle, or null in every state that is not served (the caller answers one uniform 404). */
    fun renderPublic(handle: String): RenderedPage?

    /** The processed photo of a served page, or null in every state where the page or its photo is not served (one uniform 404). */
    fun renderPhoto(handle: String): PublicPhoto?

    /** A one-use, 60-second ticket for the owner to see the page with [settings]. */
    fun preview(sub: String, settings: PublicCvSettings): PreviewTicket

    /** The preview page once, or null for an unknown, used or expired ticket. */
    fun renderPreview(ticket: PreviewTicket): RenderedPage?

    /** Administrator takedown. False when no page has the handle. */
    fun takeDown(handle: String): Boolean

    /** Administrator unlock. False when no page has the handle. */
    fun unlock(handle: String): Boolean
}
