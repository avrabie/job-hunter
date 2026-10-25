package net.s4v3.backend.llm.internal

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the model is asked to return for one section of a CV. Same rules as [CvProfileDraft]: the entry types are reused,
 * so [DraftMapper] checks every value in one place.
 */
@Serializable
@SerialName("ExperienceSection")
@LLMDescription(
    "The jobs the CV states. Use only what the CV text says. Never guess or infer missing facts.",
)
internal data class ExperienceSection(
    @property:LLMDescription("Every position in the CV's experience section, most recent first, each with every achievement the CV states.")
    val experience: List<ExperienceDraft> = emptyList(),
)

@Serializable
@SerialName("EducationSection")
@LLMDescription(
    "The education the CV states. Use only what the CV text says. Never guess or infer missing facts.",
)
internal data class EducationSection(
    @property:LLMDescription("Every education entry the CV states, as the CV lists them.")
    val education: List<EducationDraft> = emptyList(),
)

@Serializable
@SerialName("PublicationsSection")
@LLMDescription(
    "The publications the CV states. Use only what the CV text says. Never guess or infer missing facts.",
)
internal data class PublicationsSection(
    @property:LLMDescription("Every publication the CV states, in the CV's order.")
    val publications: List<PublicationDraft> = emptyList(),
)

@Serializable
@SerialName("ContactSection")
@LLMDescription(
    "The candidate's own contact details as the CV states them. Never use a referee's or a company's contact. " +
        "Use only what the CV text says. Leave a field null if the CV does not state it.",
)
internal data class ContactSection(
    @property:LLMDescription("The candidate's full name, as written in the CV header.")
    val fullName: String? = null,
    @property:LLMDescription("The professional headline under the name (for example a job title), if the CV has one.")
    val headline: String? = null,
    @property:LLMDescription("The candidate's own email address.")
    val email: String? = null,
    @property:LLMDescription("The candidate's own phone number, as written.")
    val phone: String? = null,
    @property:LLMDescription("The candidate's personal website address.")
    val website: String? = null,
    @property:LLMDescription("The candidate's LinkedIn profile address.")
    val linkedin: String? = null,
)

