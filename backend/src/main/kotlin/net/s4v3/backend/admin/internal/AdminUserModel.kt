package net.s4v3.backend.admin.internal

import java.time.Instant
import java.time.LocalDate

/** One user in the administrator list: account, profile name and how many tracked jobs are in each stage. */
internal data class AdminUserSummary(
    val sub: String,
    val email: String?,
    val displayName: String?,
    val createdAt: Instant,
    val fullName: String?,
    val hasProfile: Boolean,
    val saved: Int,
    val applying: Int,
    val applied: Int,
    val rejected: Int,
    val lastActivity: Instant?,
)

/** The contact and location fields of the user's profile. Nothing else from the profile is shown. */
internal data class AdminProfile(
    val fullName: String?,
    val headline: String?,
    val phone: String?,
    val website: String?,
    val linkedin: String?,
    val city: String?,
    val canton: String?,
)

/** One tracked job without its description or notes: the frozen title, company and link, the stage and the dates. */
internal data class AdminApplication(
    val id: String,
    val stage: String,
    val title: String,
    val company: String,
    val url: String,
    val source: String,
    val capturedAt: Instant,
    val stageChangedAt: Instant,
    val appliedOn: LocalDate?,
)

internal data class AdminUserDetail(
    val user: AdminUserSummary,
    val profile: AdminProfile?,
    val applications: List<AdminApplication>,
)
