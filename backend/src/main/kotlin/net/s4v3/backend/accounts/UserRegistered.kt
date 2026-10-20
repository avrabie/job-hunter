package net.s4v3.backend.accounts

import java.time.Instant

/**
 * Published by `accounts` once, when a user record is created for a `sub` seen for the first time.
 * Carries identifiers only: no email or name. No consumers yet; later modules (for example `profile`) may subscribe.
 */
data class UserRegistered(val userId: String, val registeredAt: Instant)
