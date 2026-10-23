package net.s4v3.backend.llm.internal

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the model is asked to return for a CV. The `@LLMDescription` texts become part of the JSON schema that Koog sends
 * to the model, so they are the instructions for each field.
 *
 * Levels, permits and months are plain strings here on purpose: if one of them were an enum, a single unknown value would
 * make the whole answer unparseable. [DraftMapper] turns the strings into the typed `Profile` and drops any single value
 * that is not allowed.
 */
@Serializable
@SerialName("CvProfileDraft")
@LLMDescription(
    "The facts stated in a CV. Use only what the CV text says. Leave a field null or an empty list when the CV does " +
        "not state it. Never guess or infer missing facts.",
)
internal data class CvProfileDraft(
    @property:LLMDescription("Where the person lives now, if the CV says so.")
    val location: LocationDraft? = null,
    @property:LLMDescription(
        "Swiss work authorisation if the CV states it: one of SWISS_CITIZEN, PERMIT_C, PERMIT_B, PERMIT_L, PERMIT_G, " +
            "NEEDS_SPONSORSHIP. Otherwise null.",
    )
    val workAuthorisation: String? = null,
    @property:LLMDescription("Languages the person speaks, with the level the CV gives.")
    val languages: List<LanguageDraft> = emptyList(),
    @property:LLMDescription("A short professional summary, taken from the CV's own summary or profile section. Null if none.")
    val summary: String? = null,
    @property:LLMDescription("Jobs, most recent first, as listed in the CV.")
    val experience: List<ExperienceDraft> = emptyList(),
    @property:LLMDescription("Education entries as listed in the CV.")
    val education: List<EducationDraft> = emptyList(),
    @property:LLMDescription("Publications from the CV's publications section, in the CV's order. Empty if the CV has none.")
    val publications: List<PublicationDraft> = emptyList(),
    @property:LLMDescription("Technical and professional skills named in the CV.")
    val skills: List<SkillDraft> = emptyList(),
)

@Serializable
@SerialName("PublicationDraft")
internal data class PublicationDraft(
    @property:LLMDescription("Title of the paper, talk or publication.")
    val title: String,
    @property:LLMDescription("Journal, conference or publisher, if stated.")
    val venue: String? = null,
    @property:LLMDescription("Four-digit year or null.")
    val year: String? = null,
    @property:LLMDescription("Authors as written in the CV, free text.")
    val authors: String? = null,
    @property:LLMDescription("Link to the publication, only if the CV gives a full web address; otherwise null.")
    val url: String? = null,
)

@Serializable
@SerialName("LocationDraft")
internal data class LocationDraft(
    @property:LLMDescription("City or town, e.g. Zurich.")
    val city: String? = null,
    @property:LLMDescription("Swiss canton as its two-letter code, e.g. ZH. Null if not Swiss or not stated.")
    val canton: String? = null,
)

@Serializable
@SerialName("LanguageDraft")
internal data class LanguageDraft(
    @property:LLMDescription("Two-letter lowercase ISO 639-1 code, e.g. en, de, fr, it.")
    val code: String,
    @property:LLMDescription(
        "One of A1, A2, B1, B2, C1, C2, NATIVE. Map words like fluent or mother tongue only if they clearly match; " +
            "otherwise null.",
    )
    val level: String? = null,
)

@Serializable
@SerialName("ExperienceDraft")
internal data class ExperienceDraft(
    @property:LLMDescription("Employer name.")
    val employer: String,
    @property:LLMDescription("Job title.")
    val title: String,
    @property:LLMDescription("Start month as YYYY-MM. Use YYYY-01 only if the CV gives just a year.")
    val start: String? = null,
    @property:LLMDescription("End month as YYYY-MM, or null if the position is current.")
    val end: String? = null,
    @property:LLMDescription("City or country of the position, if stated.")
    val location: String? = null,
    @property:LLMDescription("What the person did or achieved, one short sentence each, in the CV's own words.")
    val achievements: List<String> = emptyList(),
)

@Serializable
@SerialName("EducationDraft")
internal data class EducationDraft(
    @property:LLMDescription("School or university.")
    val institution: String,
    @property:LLMDescription("Degree, e.g. MSc, BSc, Apprenticeship.")
    val degree: String,
    @property:LLMDescription("Field of study, if stated.")
    val field: String? = null,
    @property:LLMDescription("Start month as YYYY-MM, or null.")
    val start: String? = null,
    @property:LLMDescription("End month as YYYY-MM, or null.")
    val end: String? = null,
)

@Serializable
@SerialName("SkillDraft")
internal data class SkillDraft(
    @property:LLMDescription("Skill name as written in the CV, e.g. Kotlin.")
    val name: String,
    @property:LLMDescription("BEGINNER, INTERMEDIATE, ADVANCED or EXPERT only if the CV states a level; otherwise null.")
    val level: String? = null,
)
