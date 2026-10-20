package net.s4v3.backend.accounts

/**
 * The user making the current request. This is the only way other modules learn who the caller is;
 * they never read the token themselves.
 */
interface CurrentUser {

    /** The caller's id: the `sub` claim of the validated access token. */
    fun id(): String
}

/** Thrown when the current user is asked for outside an authenticated request. */
class NoCurrentUserException(message: String = "No authenticated user in the current context") :
    IllegalStateException(message)
