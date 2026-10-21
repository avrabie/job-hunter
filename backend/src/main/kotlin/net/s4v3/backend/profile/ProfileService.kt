package net.s4v3.backend.profile

/**
 * Reads and replaces a user's master profile. Every method takes the user's id (the token's `sub`); callers get it from
 * `CurrentUser`, nothing here accepts any other way to name a user.
 */
interface ProfileService {

    /** The stored profile, or an empty one at version 0. */
    fun get(sub: String): StoredProfile

    /**
     * Parses a request body strictly (unknown fields, unknown enum values and wrong types are rejected).
     *
     * @throws ProfileInvalidException if the body is not a well-formed update
     */
    fun parse(body: ByteArray): ProfileUpdate

    /**
     * Validates [update] and replaces the stored profile if its version matches the stored one.
     *
     * @throws ProfileInvalidException if a rule is broken
     * @throws ProfileConflictException if the stored version differs
     */
    fun replace(sub: String, update: ProfileUpdate): StoredProfile

    companion object {
        /** Largest request body accepted, in bytes. */
        const val MAX_BODY_BYTES = 128 * 1024
    }
}

/** Field path (for example `experience[0].title`) to a message that names the rule, never the value. */
class ProfileInvalidException(val fields: Map<String, String>) : RuntimeException("The profile breaks ${fields.size} rule(s)")

class ProfileConflictException : RuntimeException("The stored profile has a different version")
