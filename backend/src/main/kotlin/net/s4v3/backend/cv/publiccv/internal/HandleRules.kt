package net.s4v3.backend.cv.publiccv.internal

import java.util.Locale

/** Why a handle may not be claimed. Availability answers map these to `invalid` and `reserved`. */
internal enum class HandleProblem { INVALID, RESERVED }

/**
 * The handle rules of the public CV page (`cv-public` "Handle"). Pure functions: no database, no clock.
 * A handle is 3 to 30 characters of lower-case letters, digits and single inner hyphens, never all digits, and not reserved.
 */
internal object HandleRules {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    private val FORM = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    private val DIGITS_ONLY = Regex("[0-9]+")

    /** Names that a handle must not take: routes of the apps, system names and common words that imply an official page. */
    val RESERVED: Set<String> = setOf(
        "admin", "administrator", "api", "auth", "login", "logout", "register", "callback", "cv", "cvs", "export",
        "profile", "files", "file", "matches", "postings", "posting", "applications", "application", "letters", "letter", "publish", "public", "settings", "setting",
        "account", "accounts", "user", "users", "me", "root", "support", "help", "about", "contact", "terms", "privacy",
        "legal", "imprint", "www", "mail", "static", "assets", "health", "actuator", "docs", "swagger", "openapi", "new",
        "edit", "null", "undefined", "s4v3",
    )

    /** Lower-cases the input. Every check and every stored value uses this form. */
    fun normalize(raw: String): String = raw.lowercase(Locale.ROOT)

    /** Null when [raw] may be claimed by its form; the input is lower-cased first. */
    fun validate(raw: String): HandleProblem? {
        val handle = normalize(raw)
        return when {
            handle.length !in MIN_LENGTH..MAX_LENGTH -> HandleProblem.INVALID
            !FORM.matches(handle) -> HandleProblem.INVALID
            DIGITS_ONLY.matches(handle) -> HandleProblem.INVALID
            handle in RESERVED -> HandleProblem.RESERVED
            else -> null
        }
    }
}
