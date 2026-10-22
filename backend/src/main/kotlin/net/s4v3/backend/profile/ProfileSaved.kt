package net.s4v3.backend.profile

/**
 * Published by `profile` after a profile was replaced successfully, never for a rejected save. [userId] is the token
 * `sub`, [version] the new stored version. It carries no profile content. The consumer is `matching`.
 */
data class ProfileSaved(val userId: String, val version: Long)
