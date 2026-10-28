package net.s4v3.backend.llm.internal

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.s4v3.backend.llm.JobDraft
import net.s4v3.backend.llm.JobExtractor
import net.s4v3.backend.llm.LetterLanguage
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [JobExtractor] on Koog. One plain-text request (no tools); the answer is a JSON object that is parsed, cleaned and checked.
 * Nothing from the page or the answer is logged.
 */
@Component
internal class KoogJobExtractor(
    private val llm: LlmSettings,
    private val import: JobImportSettings,
    private val executors: ExecutorSource,
) : JobExtractor {

    private val log = LoggerFactory.getLogger(KoogJobExtractor::class.java)

    private val executor: PromptExecutor by lazy { executors.create(llm.apiKey) }

    override fun extract(pageText: String): JobDraft {
        if (llm.apiKey.isBlank()) throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val modelName = import.model.ifBlank { llm.model }
        val model = KoogProjectAnalyst.modelFor(modelName) ?: throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val started = System.nanoTime()
        try {
            val raw = runBlocking {
                withTimeout(import.timeout.toMillis()) {
                    val p = prompt("job-extraction", LLMParams(maxTokens = MAX_OUTPUT_TOKENS)) {
                        system(JobPrompts.SYSTEM)
                        user(JobPrompts.user(pageText))
                    }
                    executor.execute(p, model).textContent()
                }
            }
            val draft = JobDraftCleaner.clean(raw) ?: run {
                // A fixed word and a length, never the answer itself.
                log.warn("Job extraction refused the answer: {} ({} chars)", JobDraftCleaner.refusalReason(raw), raw.length)
                throw LlmFailure(LlmFailureKind.FAILED)
            }
            log.info("Job extracted by the model in {} ms (found: {})", elapsedMs(started), draft.found)
            return draft
        } catch (e: LlmFailure) {
            log.warn("Job extraction failed after {} ms (model {}): unusable answer", elapsedMs(started), modelName)
            throw e
        } catch (e: Exception) {
            // Only the class name: an exception message may carry the prompt or the answer.
            log.warn(
                "Job extraction failed after {} ms (model {}): {}", elapsedMs(started), modelName, e::class.simpleName)
            throw LlmFailure(LlmFailureKind.FAILED)
        }
    }

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val MAX_OUTPUT_TOKENS = 8_000
    }
}

@Serializable
internal data class JobDraftJson(
    val found: Boolean = false,
    val title: String = "",
    val company: String = "",
    val location: String? = null,
    val description: String = "",
    val language: String = "EN",
    val street: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val contactPerson: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
)

/** Output cleaning of design decision 5 (the grounding check is the caller's). Returns null when the answer must be refused. */
internal object JobDraftCleaner {
    const val MAX_TITLE_CHARS = 200
    const val MAX_COMPANY_CHARS = 200
    const val MAX_LOCATION_CHARS = 200
    const val MAX_DESCRIPTION_CHARS = 20_000
    const val MAX_DETAIL_CHARS = 200
    private val FENCE = Regex("^```[A-Za-z]*[ \\t]*\\n(.*)\\n```$", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TAG = Regex("<\\s*[A-Za-z/!]")
    private val JSON = Json { ignoreUnknownKeys = true }

    /** The answer's JSON text: without a code fence, and without prose around the object. */
    private fun jsonText(raw: String): String {
        var text = raw.trim()
        FENCE.matchEntire(text)?.let { text = it.groupValues[1].trim() }
        if (!text.startsWith("{")) {
            val from = text.indexOf('{')
            val to = text.lastIndexOf('}')
            if (from >= 0 && to > from) text = text.substring(from, to + 1)
        }
        return text
    }

    /** Why [clean] refuses [raw], as a fixed word for the log (never the answer's text). */
    fun refusalReason(raw: String): String {
        val parsed = try {
            JSON.decodeFromString(JobDraftJson.serializer(), jsonText(raw))
        } catch (_: Exception) {
            return "not_json"
        }
        if (!parsed.found) return "not_found"
        val title = parsed.title.trim()
        val company = parsed.company.trim()
        val description = parsed.description.trim()
        return when {
            title.isEmpty() -> "empty_title"
            company.isEmpty() -> "empty_company"
            description.isEmpty() -> "empty_description"
            title.length > MAX_TITLE_CHARS || company.length > MAX_COMPANY_CHARS -> "too_long"
            description.length > MAX_DESCRIPTION_CHARS -> "description_too_long"
            listOfNotNull(title, company, parsed.location, description).any { HTML_TAG.containsMatchIn(it) } -> "markup"
            parsed.language.trim().uppercase() !in setOf("EN", "DE") -> "bad_language"
            else -> "other"
        }
    }

    fun clean(raw: String): JobDraft? {
        val text = jsonText(raw)
        val parsed = try {
            JSON.decodeFromString(JobDraftJson.serializer(), text)
        } catch (_: Exception) {
            return null
        }
        if (!parsed.found) return JobDraft(false, "", "", null, "", LetterLanguage.EN)
        val title = parsed.title.trim()
        val company = parsed.company.trim()
        val location = parsed.location?.trim()?.ifEmpty { null }
        val description = parsed.description.trim()
        if (title.isEmpty() || company.isEmpty() || description.isEmpty()) return null
        if (title.length > MAX_TITLE_CHARS || company.length > MAX_COMPANY_CHARS) return null
        if ((location?.length ?: 0) > MAX_LOCATION_CHARS || description.length > MAX_DESCRIPTION_CHARS) return null
        if (listOfNotNull(title, company, location, description).any { HTML_TAG.containsMatchIn(it) }) return null
        val language = when (parsed.language.trim().uppercase()) {
            "EN" -> LetterLanguage.EN
            "DE" -> LetterLanguage.DE
            else -> return null
        }
        return JobDraft(
            true, title, company, location, description, language,
            street = detail(parsed.street), postalCode = detail(parsed.postalCode), city = detail(parsed.city),
            contactPerson = detail(parsed.contactPerson), contactPhone = detail(parsed.contactPhone),
            contactEmail = detail(parsed.contactEmail),
        )
    }

    /** An address or contact value: blank, over-long or tag-like becomes null, since a missing value never fails the answer. */
    private fun detail(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_DETAIL_CHARS && !HTML_TAG.containsMatchIn(it) }
}
