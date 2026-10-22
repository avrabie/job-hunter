package net.s4v3.backend.profile

/**
 * Read access to all stored profiles for modules that act for every user (such as `matching`). Not reachable over HTTP.
 * Personal data: callers must not log what they read.
 */
interface ProfileDirectory {

    /**
     * Stored profiles ordered by owner id, starting after [afterSub] (null for the first page). Only users who saved a
     * profile appear. [size] is capped at [MAX_PAGE_SIZE].
     */
    fun page(afterSub: String?, size: Int): List<OwnedProfile>

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

data class OwnedProfile(val sub: String, val stored: StoredProfile)
