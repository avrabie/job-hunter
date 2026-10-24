package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.cv.publiccv.PublicCvSettings
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/** The events of `public_cv_audit`. The audit keeps these names and the flags only, never values. */
internal enum class PublicCvEvent { PUBLISHED, PAUSED, SETTINGS_CHANGED, HANDLE_CHANGED, REMOVED, TAKEN_DOWN, UNLOCKED }

/** One `public_cv` row. `handle` is lower-case. */
internal data class PublicCvRow(
    val ownerSub: String,
    val handle: String,
    val settings: PublicCvSettings,
    val published: Boolean,
    val locked: Boolean,
    val settingsVersion: Int,
    val handleChangedAt: Instant?,
    val updatedAt: Instant,
)

/** A released handle that [ownerSub] may take back until [heldUntil]. */
internal data class HandleHold(val handle: String, val ownerSub: String, val heldUntil: Instant)

/**
 * `public_cv`, `public_cv_handle_hold` and `public_cv_audit`. Every statement is scoped by the owner's `sub` or by the
 * handle. Times come in as parameters, so the hold and change rules can be tested with any `now`.
 */
@Repository
internal class PublicCvRepository(private val jdbc: JdbcTemplate) {

    fun find(sub: String): PublicCvRow? =
        jdbc.query("$SELECT where owner_sub = ?", { rs, _ -> row(rs) }, sub).firstOrNull()

    /** Ignores case, so a request with any capitalisation finds the page. */
    fun findByHandle(handle: String): PublicCvRow? =
        jdbc.query("$SELECT where lower(handle) = lower(?)", { rs, _ -> row(rs) }, handle).firstOrNull()

    /**
     * Creates the owner's row or replaces its handle and settings. The lock is never changed here: only an administrator
     * sets or clears it, so an owner's save cannot undo a takedown.
     */
    fun upsert(row: PublicCvRow) {
        jdbc.update(
            """
            insert into public_cv (owner_sub, handle, settings, published, locked, settings_version, handle_changed_at, updated_at)
            values (?, ?, cast(? as jsonb), ?, false, ?, ?, ?)
            on conflict (owner_sub) do update set
                handle = excluded.handle,
                settings = excluded.settings,
                published = excluded.published,
                settings_version = excluded.settings_version,
                handle_changed_at = excluded.handle_changed_at,
                updated_at = excluded.updated_at
            """.trimIndent(),
            row.ownerSub, row.handle, json.writeValueAsString(row.settings), row.published, row.settingsVersion,
            row.handleChangedAt?.let { Timestamp.from(it) }, Timestamp.from(row.updatedAt),
        )
    }

    /** Publish or pause. False when the owner has no page. */
    fun setPublished(sub: String, published: Boolean, now: Instant): Boolean =
        jdbc.update(
            "update public_cv set published = ?, updated_at = ? where owner_sub = ?",
            published, Timestamp.from(now), sub,
        ) == 1

    /** Removes the owner's page. False when there was none. */
    fun delete(sub: String): Boolean = jdbc.update("delete from public_cv where owner_sub = ?", sub) == 1

    /** Administrator takedown: unpublished and locked. Answers the owner's `sub`, or null when no page has the handle. */
    fun takeDown(handle: String, now: Instant): String? =
        jdbc.query(
            "update public_cv set published = false, locked = true, updated_at = ? where lower(handle) = lower(?) returning owner_sub",
            { rs, _ -> rs.getString("owner_sub") },
            Timestamp.from(now), handle,
        ).firstOrNull()

    /** Administrator unlock. Answers the owner's `sub`, or null when no page has the handle. */
    fun unlock(handle: String, now: Instant): String? =
        jdbc.query(
            "update public_cv set locked = false, updated_at = ? where lower(handle) = lower(?) returning owner_sub",
            { rs, _ -> rs.getString("owner_sub") },
            Timestamp.from(now), handle,
        ).firstOrNull()

    /** Keeps a released handle for [ownerSub] until [heldUntil]. Replaces an older hold of the same handle. */
    fun insertHold(handle: String, ownerSub: String, heldUntil: Instant) {
        jdbc.update(
            """
            insert into public_cv_handle_hold (handle, owner_sub, held_until) values (?, ?, ?)
            on conflict (handle) do update set owner_sub = excluded.owner_sub, held_until = excluded.held_until
            """.trimIndent(),
            handle, ownerSub, Timestamp.from(heldUntil),
        )
    }

    /** The hold that has not run out at [now]. A hold whose time has come is not a hold. */
    fun activeHold(handle: String, now: Instant): HandleHold? =
        jdbc.query(
            "select handle, owner_sub, held_until from public_cv_handle_hold where handle = ? and held_until > ?",
            { rs, _ ->
                HandleHold(rs.getString("handle"), rs.getString("owner_sub"), rs.getTimestamp("held_until").toInstant())
            },
            handle, Timestamp.from(now),
        ).firstOrNull()

    /** Removes a hold, for example when its owner takes the handle back. False when there was none. */
    fun deleteHold(handle: String): Boolean =
        jdbc.update("delete from public_cv_handle_hold where handle = ?", handle) == 1

    /** Removes the holds that have run out at [now]. Answers how many. */
    fun purgeHolds(now: Instant): Int =
        jdbc.update("delete from public_cv_handle_hold where held_until <= ?", Timestamp.from(now))

    /**
     * True when the owner changed the handle less than 30 days before [now]. It reads the audit, so deleting the page
     * and claiming a handle again does not reset the rule.
     */
    fun changeTooSoon(sub: String, now: Instant): Boolean =
        jdbc.queryForObject(
            "select exists (select 1 from public_cv_audit where owner_sub = ? and event = 'HANDLE_CHANGED' and occurred_at > ?)",
            Boolean::class.java,
            sub, Timestamp.from(now.minus(CHANGE_INTERVAL)),
        ) == true

    /** Appends one audit row. [flags] names the settings that are on; it never carries their values. */
    fun audit(sub: String, event: PublicCvEvent, at: Instant, flags: Map<String, Boolean>) {
        jdbc.update(
            "insert into public_cv_audit (owner_sub, event, occurred_at, flags) values (?, ?, ?, cast(? as jsonb))",
            sub, event.name, Timestamp.from(at), json.writeValueAsString(flags),
        )
    }

    /** Lenient on read: a later release may add a setting without breaking stored rows. */
    private val json: JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    private fun row(rs: ResultSet) =
        PublicCvRow(
            ownerSub = rs.getString("owner_sub"),
            handle = rs.getString("handle"),
            settings = json.readValue(rs.getString("settings"), PublicCvSettings::class.java),
            published = rs.getBoolean("published"),
            locked = rs.getBoolean("locked"),
            settingsVersion = rs.getInt("settings_version"),
            handleChangedAt = rs.getTimestamp("handle_changed_at")?.toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )

    private companion object {
        val CHANGE_INTERVAL: Duration = Duration.ofDays(30)

        const val SELECT =
            "select owner_sub, handle, settings::text as settings, published, locked, settings_version, handle_changed_at, updated_at from public_cv"
    }
}
