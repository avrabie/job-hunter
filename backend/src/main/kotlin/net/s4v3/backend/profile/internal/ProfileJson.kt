package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileUpdate
import tools.jackson.core.JacksonException
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * JSON for request bodies and for the stored document. Requests are parsed strictly; the stored document is read
 * leniently so that a later release can add a field without breaking older rows.
 */
internal object ProfileJson {

    /** Version of the stored document layout (`profile.schema_version`). */
    const val SCHEMA_VERSION = 1

    private val strict: JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build()

    private val lenient: JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun parseUpdate(body: ByteArray): ProfileUpdate =
        try {
            strict.readValue(body, ProfileUpdate::class.java)
        } catch (e: JacksonException) {
            // The exception message quotes parts of the input, so only its location (the property path) is used.
            throw ProfileInvalidException(mapOf(pathOf(e) to "is missing, unknown or has the wrong type"))
        }

    fun write(profile: Profile): String = strict.writeValueAsString(profile)

    fun read(document: String): Profile = lenient.readValue(document, Profile::class.java)

    /** `profile.experience[0].title` in the request becomes `experience[0].title`; `version` stays `version`. */
    private fun pathOf(e: JacksonException): String {
        val references = (e as? DatabindException)?.path.orEmpty()
        val path = StringBuilder()
        references.forEachIndexed { i, ref ->
            val name = ref.propertyName
            when {
                i == 0 && name == "profile" -> Unit
                name != null -> path.append(if (path.isEmpty()) name else ".$name")
                ref.index >= 0 -> path.append("[${ref.index}]")
            }
        }
        return path.toString().ifEmpty { "body" }
    }
}
