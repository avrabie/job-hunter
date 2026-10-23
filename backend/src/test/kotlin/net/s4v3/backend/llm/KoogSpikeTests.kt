package net.s4v3.backend.llm

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * First contact with Koog, with no network and no key: a [FakePromptExecutor] plays the model. Read together with
 * docs/koog-notes.md. Nothing here is production code; the real extraction comes in later tasks.
 */
class KoogSpikeTests {

    /** The target type. `@LLMDescription` text is sent to the model as part of the JSON schema. */
    @Serializable
    @SerialName("Greeting")
    @LLMDescription("A short greeting")
    data class Greeting(
        @property:LLMDescription("The greeting text")
        val text: String,
        @property:LLMDescription("Number of words in the text")
        val words: Int,
    )

    private val ask = prompt("spike") {
        system("You answer with a greeting.")
        user("Say hello.")
    }

    @Test
    fun `a lenient JSON slip is accepted without a second call`() = runBlocking {
        val executor = FakePromptExecutor("""{"text":"hello" "words":1}""")

        val result = executor.executeStructured<Greeting>(ask, AnthropicModels.Haiku_4_5).getOrThrow()

        assertEquals(Greeting("hello", 1), result.data)
        assertEquals(1, executor.prompts.size)
    }

    @Test
    fun `a structured call parses the model's JSON into the data class`() = runBlocking {
        val executor = FakePromptExecutor("""{"text":"hello there","words":2}""")

        val result = executor.executeStructured<Greeting>(ask, AnthropicModels.Haiku_4_5).getOrThrow()

        assertEquals(Greeting("hello there", 2), result.data)
        // Koog adds the schema to the prompt it sends; the model never sees our class, only that description.
        assertTrue(executor.prompts.single().messages.size >= 2)
    }

    @Test
    fun `a broken answer is repaired by a second model call when a fixing parser is given`() = runBlocking {
        // A missing field cannot be parsed. (A missing comma would be accepted: Koog's JSON parser is lenient.)
        val executor = FakePromptExecutor("""{"text":"hello"}""", """{"text":"hello","words":1}""")
        val fixing = StructureFixingParser(model = AnthropicModels.Haiku_4_5, retries = 1)

        val result = executor.executeStructured<Greeting>(ask, AnthropicModels.Haiku_4_5, fixingParser = fixing).getOrThrow()

        assertEquals(Greeting("hello", 1), result.data)
        assertEquals(2, executor.prompts.size)
    }

    @Test
    fun `a broken answer without repairs is a failed Result`() = runBlocking {
        val executor = FakePromptExecutor("not json at all")

        val result = executor.executeStructured<Greeting>(ask, AnthropicModels.Haiku_4_5)

        assertTrue(result.isFailure)
    }
}
