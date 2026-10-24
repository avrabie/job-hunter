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
    val publications: List<Publication> = emptyList(),
    val projects: List<Project> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val preferences: Preferences = Preferences(),
)

data class Location(val city: String? = null, val canton: String? = null)

enum class WorkAuthorisation { SWISS_CITIZEN, PERMIT_C, PERMIT_B, PERMIT_L, PERMIT_G, NEEDS_SPONSORSHIP }

enum class LanguageLevel { A1, A2, B1, B2, C1, C2, NATIVE }

/** A code-hosting provider. Stand-in for the group 1 type; the lead resolves the overlap at merge. */
enum class CodeProvider { GITHUB }

data class LanguageSkill(val code: String, val level: LanguageLevel)

data class Experience(
    val employer: String,
    val title: String,
    val start: YearMonth,
    /** Null means the position is current. */
    val end: YearMonth? = null,
    val location: String? = null,
    val achievements: List<String> = emptyList(),
    /** Technologies the position names, as short tags; tidied by the client and the CV reader. */
    val technologies: List<String> = emptyList(),
)

data class Education(
    val institution: String,
    val degree: String,
    val field: String? = null,
    val start: YearMonth? = null,
    val end: YearMonth? = null,
)

/** A paper, talk or other publication. [venue] is the journal, conference or publisher; [authors] is free text. */
data class Publication(
    val title: String,
    val venue: String? = null,
    val year: Int? = null,
    val authors: String? = null,
    val url: String? = null,
)

/** What kind of work a project was: a paid assignment, a personal project, open-source work or other work. */
enum class ProjectType { ASSIGNMENT, PERSONAL, OPEN_SOURCE, WORK }

/**
 * A project worth showing: an assignment, a personal or open-source project. [results] are short lines of stated
 * outcomes; [highlight] is set by the user only, never by the CV reader.
 */
data class Project(
    val name: String,
    val type: ProjectType,
    val context: String? = null,
    val start: YearMonth? = null,
    val end: YearMonth? = null,
    val description: String? = null,
    val technologies: List<String> = emptyList(),
    val results: List<String> = emptyList(),
    val links: List<ProjectLink> = emptyList(),
    val highlight: Boolean = false,
)

/** A labelled link of a project, for example its repository. [url] is an absolute http or https address. */
data class ProjectLink(val label: String, val url: String)

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
