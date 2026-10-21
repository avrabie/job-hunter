package net.s4v3.backend.profile

import java.time.YearMonth

/**
 * The master profile: the facts about one person that matching and drafting may use. Language-neutral; entries keep
 * the order the user gave them. Personal data: never log it.
 */
data class Profile(
    val location: Location? = null,
    val workAuthorisation: WorkAuthorisation? = null,
    val languages: List<LanguageSkill> = emptyList(),
    val summary: String? = null,
    val experience: List<Experience> = emptyList(),
    val education: List<Education> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val preferences: Preferences = Preferences(),
)

data class Location(val city: String? = null, val canton: String? = null)

enum class WorkAuthorisation { SWISS_CITIZEN, PERMIT_C, PERMIT_B, PERMIT_L, PERMIT_G, NEEDS_SPONSORSHIP }

enum class LanguageLevel { A1, A2, B1, B2, C1, C2, NATIVE }

data class LanguageSkill(val code: String, val level: LanguageLevel)

data class Experience(
    val employer: String,
    val title: String,
    val start: YearMonth,
    /** Null means the position is current. */
    val end: YearMonth? = null,
    val location: String? = null,
    val achievements: List<String> = emptyList(),
)

data class Education(
    val institution: String,
    val degree: String,
    val field: String? = null,
    val start: YearMonth? = null,
    val end: YearMonth? = null,
)

enum class SkillLevel { BEGINNER, INTERMEDIATE, ADVANCED, EXPERT }

data class Skill(val name: String, val level: SkillLevel? = null)

enum class RemoteMode { ONSITE, HYBRID, REMOTE }

data class Preferences(
    val targetRoles: List<String> = emptyList(),
    val workloadMin: Int? = null,
    val workloadMax: Int? = null,
    val remoteModes: List<RemoteMode> = emptyList(),
    val cantons: List<String> = emptyList(),
    val minSalaryChf: Int? = null,
)

/** A profile with the version the API compares on save; 0 means nothing is stored yet. */
data class StoredProfile(val version: Long, val profile: Profile)

/** What a client sends: the profile and the version it last read. */
data class ProfileUpdate(val version: Long, val profile: Profile)
